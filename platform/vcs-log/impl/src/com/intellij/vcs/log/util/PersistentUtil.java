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
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
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

  @NotNull
  public static String calcLogId(@NotNull Project project, @NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    int hashcode = calcLogProvidersHash(logProviders);
    return project.getLocationHash() + "." + Integer.toHexString(hashcode);
  }

  private static int calcLogProvidersHash(@NotNull final Map<VirtualFile, VcsLogProvider> logProviders) {
    List<VirtualFile> sortedRoots = ContainerUtil.sorted(logProviders.keySet(), new Comparator<VirtualFile>() {
      @Override
      public int compare(@NotNull VirtualFile o1, @NotNull VirtualFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    return StringUtil.join(sortedRoots, new Function<VirtualFile, String>() {
      @Override
      public String fun(VirtualFile root) {
        return root.getPath() + "." + logProviders.get(root).getSupportedVcs().getName();
      }
    }, ".").hashCode();
  }

  @NotNull
  private static File getStorageFile(@NotNull String logId, @NotNull String logKind, int version) {
    File subdir = new File(LOG_CACHE, logKind);
    final File mapFile = new File(subdir, logId + "." + version);
    if (!mapFile.exists()) {
      IOUtil.deleteAllFilesStartingWith(new File(subdir, logId));
    }
    return mapFile;
  }

  @NotNull
  public static <T> PersistentEnumerator<T> createPersistentEnumerator(@NotNull final KeyDescriptor<T> keyDescriptor,
                                                                       @NotNull String logKind,
                                                                       @NotNull String logId,
                                                                       int version) throws IOException {
    final File storageFile = getStorageFile(logId, logKind, version);

    return IOUtil.openCleanOrResetBroken(new ThrowableComputable<PersistentEnumerator<T>, IOException>() {
      @Override
      public PersistentEnumerator<T> compute() throws IOException {
        return new PersistentEnumerator<T>(storageFile, keyDescriptor, Page.PAGE_SIZE);
      }
    }, storageFile);
  }

  @NotNull
  public static PersistentStringEnumerator createPersistentStringEnumerator(@NotNull String logKind, @NotNull String logId, int version)
    throws IOException {
    final File storageFile = getStorageFile(logId, logKind, version);

    return IOUtil.openCleanOrResetBroken(new ThrowableComputable<PersistentStringEnumerator, IOException>() {
      @Override
      public PersistentStringEnumerator compute() throws IOException {
        return new PersistentStringEnumerator(storageFile);
      }
    }, storageFile);
  }
}
