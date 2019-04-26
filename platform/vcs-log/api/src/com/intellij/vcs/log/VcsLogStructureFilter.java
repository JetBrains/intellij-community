// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.vcs.log.VcsLogFilterCollection.STRUCTURE_FILTER;

/**
 * Tells the log to filter by files and folders.
 */
public interface VcsLogStructureFilter extends VcsLogDetailsFilter {

  /**
   * <p>Returns files which are affected by matching commits, and folders containing such files.</p>
   * <p>
   * <p>That is: the commit A (made in the given VCS root) modifying file f.txt matches this filter,
   * if this method returns a set which includes a folder containing f.txt, or the file f.txt itself.</p>
   */
  @NotNull
  Collection<FilePath> getFiles();

  @NotNull
  @Override
  default VcsLogFilterCollection.FilterKey<VcsLogStructureFilter> getKey() {
    return STRUCTURE_FILTER;
  }

  @NotNull
  @Override
  default String getPresentation() {
    return StringUtil.join(getFiles(), FilePath::getName, ", ");
  }
}
