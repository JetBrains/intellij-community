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
package com.intellij.vcs.log.ui;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.vcs.log.VcsLogDiffHandler;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsLogUiProperties;

public class VcsLogInternalDataKeys {
  public static final DataKey<VcsLogManager> LOG_MANAGER = DataKey.create("Vcs.Log.Manager");
  public static final DataKey<VcsLogUiProperties> LOG_UI_PROPERTIES = DataKey.create("Vcs.Log.Ui.Properties");
  public static final DataKey<MainVcsLogUi> MAIN_UI = DataKey.create("Vcs.Log.Main.Ui");
  public static final DataKey<FileHistoryUi> FILE_HISTORY_UI = DataKey.create("Vcs.FileHistory.Ui");
  public static final DataKey<VcsLogUiEx> LOG_UI_EX = DataKey.create("Vcs.Log.UiEx");
  public static final DataKey<VcsLogDiffHandler> LOG_DIFF_HANDLER = DataKey.create("Vcs.Log.Diff.Handler");
  public static final DataKey<VcsLogData> LOG_DATA = DataKey.create("Vcs.Log.Data");
}
