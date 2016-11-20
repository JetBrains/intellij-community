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
package com.intellij.vcs.log.ui.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogFilterUi;
import com.intellij.vcs.log.data.VcsLogStructureFilterImpl;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class FileHistoryFilterUi implements VcsLogFilterUi {
  @NotNull private final FilePath myPath;

  public FileHistoryFilterUi(@NotNull FilePath path) {
    myPath = path;
  }

  @NotNull
  @Override
  public VcsLogFilterCollection getFilters() {
    VcsLogStructureFilterImpl fileFilter = new VcsLogStructureFilterImpl(Collections.singleton(myPath));
    return new VcsLogFilterCollectionImpl(null, null, null, null, null, fileFilter, null);
  }

  @Override
  public void setFilter(@NotNull VcsLogFilter filter) {
  }
}
