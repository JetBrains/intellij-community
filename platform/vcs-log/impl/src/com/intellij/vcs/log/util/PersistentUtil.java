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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.*;
import com.intellij.vcs.log.VcsLogProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class PersistentUtil {
  @NotNull public static final File LOG_CACHE = new File(PathManager.getSystemPath(), "vcs-log");

  @NotNull
  public static String calcLogId(@NotNull Project project, @NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    int hashcode = calcLogProvidersHash(logProviders);
    return project.getLocationHash() + "." + Integer.toHexString(hashcode);
  }

  private static int calcLogProvidersHash(@NotNull final Map<VirtualFile, VcsLogProvider> logProviders) {
    List<VirtualFile> sortedRoots = ContainerUtil.sorted(logProviders.keySet(), (o1, o2) -> o1.getPath().compareTo(o2.getPath()));
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

  public static void cleanupOldStorageFile(@NotNull String storageKind, @NotNull String logId, int version) {
    IOUtil.deleteAllFilesStartingWith(getStorageFile(storageKind, logId, version));
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

  @NotNull
  public static <V> PersistentHashMap<Integer, V> createPersistentHashMap(@NotNull DataExternalizer<V> externalizer,
                                                                          @NotNull String storageKind,
                                                                          @NotNull String logId,
                                                                          int version) throws IOException {
    File storageFile = getStorageFile(storageKind, logId, version);

    return IOUtil.openCleanOrResetBroken(() ->
                                           new PersistentHashMap<>(storageFile, new IntInlineKeyDescriptor(), externalizer, Page.PAGE_SIZE),
                                         storageFile);
  }
}
