/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.impl.MapIndexStorage;
import com.intellij.util.io.*;
import com.intellij.vcs.log.VcsLogProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class PersistentUtil {
  @NotNull public static final File LOG_CACHE = new File(PathManager.getSystemPath(), "vcs-log");
  @NotNull private static final String CORRUPTION_MARKER = "corruption.marker";

  @NotNull
  public static String calcLogId(@NotNull Project project, @NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    int hashcode = calcLogProvidersHash(logProviders);
    return project.getLocationHash() + "." + Integer.toHexString(hashcode);
  }

  private static int calcLogProvidersHash(@NotNull final Map<VirtualFile, VcsLogProvider> logProviders) {
    List<VirtualFile> sortedRoots = ContainerUtil.sorted(logProviders.keySet(), Comparator.comparing(VirtualFile::getPath));
    return StringUtil.join(sortedRoots, root -> root.getPath() + "." + logProviders.get(root).getSupportedVcs().getName(), ".").hashCode();
  }

  @NotNull
  public static File getStorageFile(@NotNull String storageKind, @NotNull String logId, int version) {
    File subdir = new File(LOG_CACHE, storageKind);
    String safeLogId = PathUtilRt.suggestFileName(logId, true, true);
    final File mapFile = new File(subdir, safeLogId + "." + version);
    if (!mapFile.exists()) {
      IOUtil.deleteAllFilesStartingWith(new File(subdir, safeLogId));
    }
    return mapFile;
  }

  @NotNull
  public static <T> PersistentEnumeratorBase<T> createPersistentEnumerator(@NotNull KeyDescriptor<T> keyDescriptor,
                                                                           @NotNull String storageKind,
                                                                           @NotNull String logId,
                                                                           int version) throws IOException {
    File storageFile = getStorageFile(storageKind, logId, version);

    return IOUtil.openCleanOrResetBroken(() ->
                                           new PersistentBTreeEnumerator<>(storageFile, keyDescriptor, Page.PAGE_SIZE, null, version),
                                         storageFile);
  }

  public static boolean deleteWithRenamingAllFilesStartingWith(@NotNull File baseFile) {
    File parentFile = baseFile.getParentFile();
    if (parentFile == null) return false;

    File[] files = parentFile.listFiles(pathname -> pathname.getName().startsWith(baseFile.getName()));
    if (files == null) return true;

    boolean deleted = true;
    for (File f : files) {
      deleted &= FileUtil.deleteWithRenaming(f);
    }
    return deleted;
  }

  // this method cleans up all storage files for a project in a specified subdir
  // it assumes that these storage files all start with "safeLogId."
  // as method getStorageFile creates them
  // so these two methods should be changed in sync
  public static boolean cleanupStorageFiles(@NotNull String subdirName, @NotNull String id) {
    File subdir = new File(LOG_CACHE, subdirName);
    String safeLogId = PathUtilRt.suggestFileName(id, true, true);
    return deleteWithRenamingAllFilesStartingWith(new File(subdir, safeLogId + "."));
  }

  // do not forget to change cleanupStorageFiles method when editing this one
  @NotNull
  public static File getStorageFile(@NotNull String subdirName,
                                    @NotNull String kind,
                                    @NotNull String id,
                                    int version,
                                    boolean forMapIndexStorage) {
    File subdir = new File(LOG_CACHE, subdirName);
    String safeLogId = PathUtilRt.suggestFileName(id, true, true);
    File baseFile = getFileName(kind, subdir, safeLogId, version);
    File storageFile = forMapIndexStorage ? MapIndexStorage.getIndexStorageFile(baseFile) : baseFile;
    if (!storageFile.exists()) {
      for (int oldVersion = 0; oldVersion < version; oldVersion++) {
        File baseOldStorageFile = getFileName(kind, subdir, safeLogId, oldVersion);
        File oldStorageFile = forMapIndexStorage ? MapIndexStorage.getIndexStorageFile(baseOldStorageFile) : baseOldStorageFile;
        IOUtil.deleteAllFilesStartingWith(oldStorageFile);
      }
    }
    return baseFile;
  }

  @NotNull
  public static File getStorageFile(@NotNull String subdirName,
                                    @NotNull String kind,
                                    @NotNull String id,
                                    int version) {
    return getStorageFile(subdirName, kind, id, version, false);
  }

  @NotNull
  private static File getFileName(@NotNull String kind, @NotNull File subdir, @NotNull String safeLogId, int version) {
    return new File(subdir, safeLogId + "." + kind + "." + version);
  }

  @NotNull
  public static File getCorruptionMarkerFile() {
    return new File(LOG_CACHE, CORRUPTION_MARKER);
  }
}
