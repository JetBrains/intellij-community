// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @deprecated not that thing one should use, left only for backward compatibility
 */
// TODO remove once persistent fs version updated
@SuppressWarnings("DeprecatedIsStillUsed")
@ApiStatus.Internal
@Deprecated
final class VfsDependentEnum {
  private final Path myFile;
  private final int myVersion;

  // GuardedBy("myLock")
  private boolean myMarkedForInvalidation;

  private final List<String> myInstances = ContainerUtil.createConcurrentList();
  private final Map<String, Integer> myInstanceToId = new ConcurrentHashMap<>();
  private final Object myLock = new Object();
  private boolean myTriedToLoadFile;

  @ApiStatus.Internal
  VfsDependentEnum(@NotNull PersistentFSPaths paths, @NotNull String fileName, int version) {
    myFile = paths.getVfsEnumFile(fileName);
    myVersion = version;
  }

  int getIdRaw(@NotNull String s) throws IOException {
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
        invalidate();
        throw e;
      }
    }
  }

  private void saveToFile(@NotNull String instance) throws IOException {
    Files.createDirectories(myFile.getParent());
    try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(myFile,
                                                                                                       StandardOpenOption.APPEND,
                                                                                                       StandardOpenOption.CREATE,
                                                                                                       StandardOpenOption.WRITE)))) {
      if (Files.size(myFile) == 0L) {
        DataInputOutputUtil.writeTIME(output, FSRecords.getCreationTimestamp());
        DataInputOutputUtil.writeINT(output, myVersion);
      }
      EnumeratorStringDescriptor.INSTANCE.save(output, instance);
    }
  }

  private boolean loadFromFile() throws IOException {
    if ( !myTriedToLoadFile && myInstances.isEmpty() && Files.exists(myFile)) {
      myTriedToLoadFile = true;
      boolean deleteFile = false;
      try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(myFile)))) {
        long vfsVersion = DataInputOutputUtil.readTIME(input);

        if (vfsVersion != FSRecords.getCreationTimestamp()) {
          // vfs was rebuilt, so the list will be rebuilt
          deleteFile = true;
          return false;
        }

        int savedVersion = DataInputOutputUtilRt.readINT(input);
        if (savedVersion == myVersion) {
          List<String> elements = new ArrayList<>();
          Map<String, Integer> elementToIdMap = new HashMap<>();
          while (input.available() > 0) {
            String instance = EnumeratorStringDescriptor.INSTANCE.read(input);
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
  private void invalidate() {
    if (!myMarkedForInvalidation) {
      myMarkedForInvalidation = true;
      // exception will be rethrown in this call
      FileUtil.deleteWithRenaming(myFile); // better alternatives ?
    }
  }

  private void register(@NotNull String instance, int id) {
    myInstanceToId.put(instance, id);
    assert id == myInstances.size() + 1;
    myInstances.add(instance);
  }
}
