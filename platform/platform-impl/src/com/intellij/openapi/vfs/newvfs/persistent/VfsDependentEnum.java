/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ConcurrentList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

// Relatively small T <-> int mapping for elements that have int numbers stored in vfs, similar to PersistentEnumerator<T>,
// unlike later numbers assigned to T are consequent and retained in memory / expected to be small.
// Vfs invalidation will rebuild this mapping, also any exception with the mapping will cause rebuild of the vfs
// stored data is VfsTimeStamp Version T*
//
public class VfsDependentEnum<T> {
  private static final String DEPENDENT_PERSISTENT_LIST_START_PREFIX = "vfs_enum_";
  private final File myFile;
  private final DataExternalizer<T> myKeyDescriptor;
  private int myVersion;

  // GuardedBy("myLock")
  private boolean myMarkedForInvalidation;

  private final ConcurrentList<T> myInstances = ContainerUtil.createConcurrentList();
  private final ConcurrentMap<T, Integer> myInstanceToId = ContainerUtil.newConcurrentMap();
  private final Object myLock = new Object();
  private boolean myTriedToLoadFile;

  public VfsDependentEnum(String fileName, KeyDescriptor<T> descriptor, int version) {
    myFile = new File(FSRecords.basePath(), DEPENDENT_PERSISTENT_LIST_START_PREFIX + fileName  + FSRecords.VFS_FILES_EXTENSION);
    myKeyDescriptor = descriptor;
    myVersion = version;
  }

  static File getBaseFile() {
    return new File(FSRecords.basePath(), DEPENDENT_PERSISTENT_LIST_START_PREFIX);
  }

  public int getId(@NotNull T s) throws IOException {
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
        throw invalidate(e);
      }
    }
  }

  private void saveToFile(@NotNull T instance) throws IOException {
    FileOutputStream fileOutputStream = new FileOutputStream(myFile, true);
    DataOutputStream output = new DataOutputStream(new BufferedOutputStream(fileOutputStream));

    try {
      if (myFile.length() == 0) {
        DataInputOutputUtil.writeTIME(output, FSRecords.getCreationTimestamp());
        DataInputOutputUtil.writeINT(output, myVersion);
      }
      myKeyDescriptor.save(output, instance);
    } finally {
      try {
        output.close();
        fileOutputStream.getFD().sync();
      }  catch (IOException ignore) {}
    }
  }

  private boolean loadFromFile() throws IOException {
    if (!myTriedToLoadFile && myInstances.size() == 0 && myFile.exists()) {
      myTriedToLoadFile = true;
      DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(myFile)));
      long vfsVersion = DataInputOutputUtil.readTIME(input);

      if (vfsVersion != FSRecords.getCreationTimestamp()) {
        // vfs was rebuilt, so the list will be rebuit
        try { input.close(); } catch (IOException ignore) {}
        FileUtil.deleteWithRenaming(myFile);
        return false;
      }

      List<T> elements = new ArrayList<>();
      Map<T, Integer> elementToIdMap = new THashMap<>();
      int savedVersion = DataInputOutputUtil.readINT(input);
      try {
        if (savedVersion == myVersion) {
          while (input.available() > 0) {
            T instance = myKeyDescriptor.read(input);
            assert instance != null;
            elements.add(instance);
            elementToIdMap.put(instance, elements.size());
          }
          myInstances.addAll(elements);
          myInstanceToId.putAll(elementToIdMap);
          return true;
        } else {
          // force vfs to rebuild
          throw new IOException("Version mismatch: current " + myVersion + ", previous:" + savedVersion + ", file:" + myFile);
        }
      }
      finally {
        try { input.close(); } catch (IOException ignore) {}
      }
    }
    return false;
  }

  // GuardedBy("myLock")
  private @Nullable IOException invalidate(@Nullable Throwable e) {
    if (!myMarkedForInvalidation) {
      doInvalidation(e);
      myMarkedForInvalidation = true;
    }
    if (e instanceof IOException) return (IOException)e;
    return null;
  }

  protected void doInvalidation(Throwable e) {
    FileUtil.deleteWithRenaming(myFile); // better alternatives ?
    FSRecords.requestVfsRebuild(e);
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
      catch (IOException e) {
        throw invalidate(e);
      } catch (AssertionError e) {
        invalidate(e);
        throw e;
      }
    }

    return null;
  }
}
