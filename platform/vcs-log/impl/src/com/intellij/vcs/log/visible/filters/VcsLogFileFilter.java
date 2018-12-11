// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters;

import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogRootFilter;
import com.intellij.vcs.log.VcsLogStructureFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsLogFileFilter implements VcsLogFilter {
  @Nullable private final VcsLogStructureFilter myStructureFilter;
  @Nullable private final VcsLogRootFilter myRootFilter;

  public VcsLogFileFilter(@Nullable VcsLogStructureFilter structureFilter, @Nullable VcsLogRootFilter rootFilter) {
    myStructureFilter = structureFilter;
    myRootFilter = rootFilter;
  }

  @Nullable
  public VcsLogStructureFilter getStructureFilter() {
    return myStructureFilter;
  }

  @Nullable
  public VcsLogRootFilter getRootFilter() {
    return myRootFilter;
  }

  @NotNull
  @Override
  public VcsLogFilterCollection.FilterKey<?> getKey() {
    return VcsLogFilterCollection.FilterKey.create("file");
  }

  @NotNull
  @Override
  public String getPresentation() {
    StringBuilder result = new StringBuilder();
    if (myRootFilter != null) {
      result.append(myRootFilter.getPresentation());
    }
    if (myStructureFilter != null) {
      if (result.length() > 0) {
        result.append(" ");
      }
      result.append(myStructureFilter.getPresentation());
    }
    return result.toString();
  }
}
