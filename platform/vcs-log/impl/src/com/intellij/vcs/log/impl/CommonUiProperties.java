// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty;

import java.util.List;

public final class CommonUiProperties {
  public static final VcsLogUiProperty<Boolean> SHOW_DETAILS = new VcsLogUiProperty<>("Window.ShowDetails");
  public static final VcsLogUiProperty<Boolean> SHOW_DIFF_PREVIEW = new VcsLogUiProperty<>("Window.ShowDiffPreview");
  public static final VcsLogUiProperty<List<String>> COLUMN_ID_ORDER = new VcsLogUiProperty<>("Table.ColumnIdOrder");
  public static final VcsLogUiProperty<Boolean> SHOW_ROOT_NAMES = new VcsLogUiProperty<>("Table.ShowRootNames");
  public static final VcsLogUiProperty<Boolean> PREFER_COMMIT_DATE = new VcsLogUiProperty<>("Table.PreferCommitDate");
}
