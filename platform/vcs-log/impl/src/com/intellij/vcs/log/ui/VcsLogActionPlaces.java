/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;

@NonNls
public class VcsLogActionPlaces {
  // action groups
  public static final String POPUP_ACTION_GROUP = "Vcs.Log.ContextMenu";
  public static final String TOOLBAR_ACTION_GROUP = "Vcs.Log.Toolbar.Internal";
  public static final String TOOLBAR_RIGHT_CORNER_ACTION_GROUP = "Vcs.Log.Toolbar.RightCorner";
  public static final String TOOLBAR_LEFT_CORNER_ACTION_GROUP = "Vcs.Log.Toolbar.LeftCorner";
  public static final String PRESENTATION_SETTINGS_ACTION_GROUP = "Vcs.Log.PresentationSettings";
  public static final String TEXT_FILTER_SETTINGS_ACTION_GROUP = "Vcs.Log.TextFilterSettings";
  public static final String FILE_HISTORY_TOOLBAR_ACTION_GROUP = "Vcs.FileHistory.Toolbar";
  public static final String HISTORY_POPUP_ACTION_GROUP = "Vcs.FileHistory.ContextMenu";
  public static final String CHANGES_BROWSER_POPUP_ACTION_GROUP = "Vcs.Log.ChangesBrowser.Popup";

  // action places
  public static final String VCS_LOG_TABLE_PLACE = "Vcs.Log.ContextMenu";
  public static final String VCS_LOG_TOOLBAR_PLACE = "Vcs.Log.Toolbar";
  public static final String VCS_HISTORY_PLACE = "Vcs.FileHistory.ContextMenu";
  public static final String VCS_HISTORY_TOOLBAR_PLACE = "Vcs.FileHistory.Toolbar";
  public static final String VCS_LOG_TOOLBAR_POPUP_PLACE = "Vcs.Log.Toolbar.Popup";

  // action ids
  public static final String VCS_LOG_INTELLI_SORT_ACTION = "Vcs.Log.IntelliSortChooser";
  public static final String VCS_LOG_FOCUS_TEXT_FILTER = "Vcs.Log.FocusTextFilter";
  public static final String VCS_LOG_SHOW_DIFF_ACTION = "Diff.ShowDiff";
  public static final String CHECKIN_PROJECT_ACTION = "CheckinProject";
}
