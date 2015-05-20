/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Encapsulates filters which can be set to VCS Log commits.
 * <p/>
 * All not-null filters are connected with AND, which means that commits should meet all of them. <br/>
 */
public interface VcsLogFilterCollection {

  @Nullable
  VcsLogBranchFilter getBranchFilter();

  @Nullable
  VcsLogUserFilter getUserFilter();

  @Nullable
  VcsLogDateFilter getDateFilter();

  @Nullable
  VcsLogTextFilter getTextFilter();

  @Nullable
  VcsLogHashFilter getHashFilter();

  @Nullable
  VcsLogStructureFilter getStructureFilter();

  @Nullable
  VcsLogRootFilter getRootFilter();

  /**
   * Returns true if there are no filters in this collection.
   */
  boolean isEmpty();

  @NotNull
  List<VcsLogDetailsFilter> getDetailsFilters();

}
