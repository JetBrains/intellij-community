/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.settings;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * The "gear" action allowing to configure merge tool visual preferences, such as displaying whitespaces, line numbers and soft wraps.
 *
 * @see DiffMergeSettings
 */
public class DiffMergeSettingsAction extends ActionGroup {

  @NotNull private final Collection<Editor> myEditors;
  @NotNull private final DiffMergeSettings mySettings;

  public DiffMergeSettingsAction(@NotNull Collection<Editor> editors, @NotNull DiffMergeSettings settings) {
    super("Settings", null, AllIcons.General.SecondaryGroup);
    setPopup(true);
    myEditors = editors;
    mySettings = settings;
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return new AnAction[] {
      new DiffMergeToggleAction("EditorToggleShowWhitespaces", DiffMergeEditorSetting.WHITESPACES, myEditors, mySettings),
      new DiffMergeToggleAction("EditorToggleShowLineNumbers", DiffMergeEditorSetting.LINE_NUMBERS, myEditors, mySettings),
      new DiffMergeToggleAction("EditorToggleShowIndentLines", DiffMergeEditorSetting.INDENT_LINES, myEditors, mySettings),
      new DiffMergeToggleAction("EditorToggleUseSoftWraps", DiffMergeEditorSetting.SOFT_WRAPS, myEditors, mySettings)
    };
  }

  private static class DiffMergeToggleAction extends ToggleAction {

    @NotNull private final DiffMergeEditorSetting mySetting;
    @NotNull private final Collection<Editor> myEditors;
    @NotNull private final DiffMergeSettings mySettings;

    private DiffMergeToggleAction(@NotNull String actionId, @NotNull DiffMergeEditorSetting setting, @NotNull Collection<Editor> editors,
                                  @NotNull DiffMergeSettings settings) {
      super(ActionsBundle.actionText(actionId), ActionsBundle.actionDescription(actionId), null);
      mySetting = setting;
      myEditors = editors;
      mySettings = settings;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return getPreference(mySetting);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      setPreference(mySetting, state);
      for (Editor editor : myEditors) {
        mySetting.apply(editor, state);
      }
    }

    private void setPreference(DiffMergeEditorSetting preference, boolean state) {
      mySettings.setPreference(preference, state);
    }

    private boolean getPreference(DiffMergeEditorSetting preference) {
      return mySettings.getPreference(preference);
    }
  }

}
