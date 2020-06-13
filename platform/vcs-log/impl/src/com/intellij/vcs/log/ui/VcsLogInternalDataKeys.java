// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsLogUiProperties;

public final class VcsLogInternalDataKeys {
  public static final DataKey<VcsLogManager> LOG_MANAGER = DataKey.create("Vcs.Log.Manager");
  public static final DataKey<VcsLogUiProperties> LOG_UI_PROPERTIES = DataKey.create("Vcs.Log.Ui.Properties");
  public static final DataKey<MainVcsLogUi> MAIN_UI = DataKey.create("Vcs.Log.Main.Ui");
  public static final DataKey<FileHistoryUi> FILE_HISTORY_UI = DataKey.create("Vcs.FileHistory.Ui");
  public static final DataKey<VcsLogUiEx> LOG_UI_EX = DataKey.create("Vcs.Log.UiEx");
  public static final DataKey<VcsLogDiffHandler> LOG_DIFF_HANDLER = DataKey.create("Vcs.Log.Diff.Handler");
  public static final DataKey<VcsLogData> LOG_DATA = DataKey.create("Vcs.Log.Data");
}
