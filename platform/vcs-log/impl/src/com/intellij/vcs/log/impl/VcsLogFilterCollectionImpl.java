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
package com.intellij.vcs.log.impl;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class VcsLogFilterCollectionImpl implements VcsLogFilterCollection {
  @NotNull public static final VcsLogFilterCollection EMPTY = new VcsLogFilterCollectionImpl(null, null, null, null, null, null, null);

  @Nullable private final VcsLogBranchFilter myBranchFilter;
  @Nullable private final VcsLogUserFilter myUserFilter;
  @Nullable private final VcsLogHashFilter myHashFilter;
  @Nullable private final VcsLogDateFilter myDateFilter;
  @Nullable private final VcsLogTextFilter myTextFilter;
  @Nullable private final VcsLogStructureFilter myStructureFilter;
  @Nullable private final VcsLogRootFilter myRootFilter;

  public VcsLogFilterCollectionImpl(@Nullable VcsLogBranchFilter branchFilter,
                                    @Nullable VcsLogUserFilter userFilter,
                                    @Nullable VcsLogHashFilter hashFilter,
                                    @Nullable VcsLogDateFilter dateFilter,
                                    @Nullable VcsLogTextFilter textFilter,
                                    @Nullable VcsLogStructureFilter structureFilter,
                                    @Nullable VcsLogRootFilter rootFilter) {
    myBranchFilter = branchFilter;
    myUserFilter = userFilter;
    myHashFilter = hashFilter;
    myDateFilter = dateFilter;
    myTextFilter = textFilter;
    myStructureFilter = structureFilter;
    myRootFilter = rootFilter;
  }

  @Nullable
  @Override
  public VcsLogBranchFilter getBranchFilter() {
    return myBranchFilter;
  }

  @Override
  @Nullable
  public VcsLogHashFilter getHashFilter() {
    return myHashFilter;
  }

  @Nullable
  @Override
  public VcsLogUserFilter getUserFilter() {
    return myUserFilter;
  }

  @Nullable
  @Override
  public VcsLogDateFilter getDateFilter() {
    return myDateFilter;
  }

  @Nullable
  @Override
  public VcsLogTextFilter getTextFilter() {
    return myTextFilter;
  }

  @Nullable
  @Override
  public VcsLogStructureFilter getStructureFilter() {
    return myStructureFilter;
  }

  @Nullable
  @Override
  public VcsLogRootFilter getRootFilter() {
    return myRootFilter;
  }


  @Override
  public boolean isEmpty() {
    return myBranchFilter == null && getDetailsFilters().isEmpty();
  }

  @NotNull
  @Override
  public List<VcsLogDetailsFilter> getDetailsFilters() {
    return ContainerUtil.skipNulls(Arrays.asList(myUserFilter, myDateFilter, myTextFilter, myStructureFilter));
  }
}
