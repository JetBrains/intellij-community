/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogFilterUi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileHistoryFilterUi implements VcsLogFilterUi {
  @NotNull private final FilePath myPath;
  @Nullable private final Hash myHash;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final FileHistoryUiProperties myProperties;

  public FileHistoryFilterUi(@NotNull FilePath path, @Nullable Hash hash, @NotNull VirtualFile root,
                             @NotNull FileHistoryUiProperties properties) {
    myPath = path;
    myHash = hash;
    myRoot = root;
    myProperties = properties;
  }

  @NotNull
  @Override
  public VcsLogFilterCollection getFilters() {
    return FileHistoryFilterer.createFilters(myPath, myHash, myRoot, myProperties.get(FileHistoryUiProperties.SHOW_ALL_BRANCHES));
  }

  @Override
  public void setFilter(@Nullable VcsLogFilter filter) {
    throw new UnsupportedOperationException();
  }
}