// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Relatively small T <-> int mapping for elements that have int numbers stored in vfs, similar to PersistentEnumerator<T>,
// unlike later numbers assigned to T are consequent and retained in memory / expected to be small.
// Vfs invalidation will rebuild this mapping, also any exception with the mapping will cause rebuild of the vfs
// stored data is VfsTimeStamp Version T*
public final class VfsDependentEnum<T> {
  private static final String DEPENDENT_PERSISTENT_LIST_START_PREFIX = "vfs_enum_";
  private final File myFile;
  private final DataExternalizer<T> myKeyDescriptor;
  private final int myVersion;

  // GuardedBy("myLock")
  private boolean myMarkedForInvalidation;

  private final List<T> myInstances = ContainerUtil.createConcurrentList();
  private final Map<T, Integer> myInstanceToId = new ConcurrentHashMap<>();
  private final Object myLock = new Object();
  private boolean myTriedToLoadFile;

  public VfsDependentEnum(@NotNull String fileName, @NotNull KeyDescriptor<T> descriptor, int version) {
    myFile = new File(FSRecords.basePath(), DEPENDENT_PERSISTENT_LIST_START_PREFIX + fileName  + FSRecords.VFS_FILES_EXTENSION);
    myKeyDescriptor = descriptor;
    myVersion = version;
  }

  static @NotNull File getBaseFile() {
    return new File(FSRecords.basePath(), DEPENDENT_PERSISTENT_LIST_START_PREFIX);
  }

  public int getId(@NotNull T s) throws IOException {
    return getIdRaw(s, true);
  }

  int getIdRaw(@NotNull T s, boolean vfsRebuildOnException) throws IOException {
    Integer integer = myInstanceToId.get(s);
    if (integer != null) return integer;

    synchronized (myLock) {
      integer = myInstanceToId.get(s);
      if (integer != null) return integer;

      try {
        boolean loaded = loadFromFile();
        if (loaded) {
          integer = myInstanceToId.get(s);
          if (integer != null) return integer;
        }

        int enumerated = myInstances.size() + 1;
        register(s, enumerated);
        saveToFile(s);
        return enumerated;
      }
      catch (IOException e) {
        invalidate(e, vfsRebuildOnException);
        throw e;
      }
    }
  }

  private void saveToFile(@NotNull T instance) throws IOException {
    FileOutputStream fileOutputStream = new FileOutputStream(myFile, true);

    try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(fileOutputStream))) {
      if (myFile.length() == 0) {
        DataInputOutputUtil.writeTIME(output, FSRecords.getCreationTimestamp());
        DataInputOutputUtil.writeINT(output, myVersion);
      }
      myKeyDescriptor.save(output, instance);
    }
    finally {
      try {
        fileOutputStream.getFD().sync();
      }
      catch (IOException ignored) {
      }
    }
  }

  private boolean loadFromFile() throws IOException {
    if (!myTriedToLoadFile && myInstances.isEmpty() && myFile.exists()) {
      myTriedToLoadFile = true;
      boolean deleteFile = false;
      try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(myFile)))) {
        long vfsVersion = DataInputOutputUtil.readTIME(input);

        if (vfsVersion != FSRecords.getCreationTimestamp()) {
          // vfs was rebuilt, so the list will be rebuilt
          deleteFile = true;
          return false;
        }

        int savedVersion = DataInputOutputUtilRt.readINT(input);
        if (savedVersion == myVersion) {
          List<T> elements = new ArrayList<>();
          Map<T, Integer> elementToIdMap = new HashMap<>();
          while (input.available() > 0) {
            T instance = myKeyDescriptor.read(input);
            assert instance != null;
            elements.add(instance);
            elementToIdMap.put(instance, elements.size());
          }
          myInstances.addAll(elements);
          myInstanceToId.putAll(elementToIdMap);
          return true;
        }
        else {
          // force vfs to rebuild
          throw new IOException("Version mismatch: current " + myVersion + ", previous:" + savedVersion + ", file:" + myFile);
        }
      }
      finally {
        if (deleteFile) {
          FileUtil.deleteWithRenaming(myFile);
        }
      }
    }
    return false;
  }

  // GuardedBy("myLock")
  private void invalidate(@NotNull Throwable e, boolean vfsRebuildOnException) {
    if (!myMarkedForInvalidation) {
      myMarkedForInvalidation = true;
      // exception will be rethrown in this call
      FileUtil.deleteWithRenaming(myFile); // better alternatives ?
      if (vfsRebuildOnException) {
        FSRecords.requestVfsRebuild(e);
      }
    }
  }

  private void register(@NotNull T instance, int id) {
    myInstanceToId.put(instance, id);
    assert id == myInstances.size() + 1;
    myInstances.add(instance);
  }

  public @NotNull T getById(int id) throws IOException {
    assert id > 0;
    --id;
    T instance;

    if (id < myInstances.size()) {
      instance = myInstances.get(id);
      if (instance != null) return instance;
    }

    synchronized (myLock) {
      if (id < myInstances.size()) {
        instance = myInstances.get(id);
        if (instance != null) return instance;
      }

      try {
        boolean loaded = loadFromFile();
        if (loaded) {
          instance = myInstances.get(id);
          if (instance != null) return instance;
        }
        assert false : "Reading nonexistent value:" + id + "," + myFile + ", loaded:" + loaded;
      }
      catch (IOException | AssertionError e) {
        invalidate(e, true);
        throw e;
      }
    }

    return null;
  }
}
