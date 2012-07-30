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
package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Collection;

/**
 * The "gear" action allowing to configure merge tool visual preferences, such as displaying whitespaces, line numbers and soft wraps.
 *
 * @author Kirill Likhodedov
 * @see MergeToolSettings
 */
class MergeToolSettingsAction extends AnAction {

  private final Collection<Editor> myEditors;
  private final ActionGroup myActionGroup;

  MergeToolSettingsAction(@NotNull Collection<Editor> editors) {
    super(AllIcons.General.Gear);
    myEditors = editors;
    myActionGroup = new MergeToolActionGroup();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    InputEvent inputEvent = e.getInputEvent();
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, myActionGroup);
    int x = 0;
    int y = 0;
    if (inputEvent instanceof MouseEvent) {
      x = ((MouseEvent)inputEvent).getX();
      y = ((MouseEvent)inputEvent).getY();
    }
    popupMenu.getComponent().show(inputEvent.getComponent(), x, y);
  }

  private class MergeToolActionGroup extends ActionGroup {
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[] {
        new MergeToolToggleAction("EditorToggleShowWhitespaces", MergeToolEditorSetting.WHITESPACES, myEditors),
        new MergeToolToggleAction("EditorToggleShowLineNumbers", MergeToolEditorSetting.LINE_NUMBERS, myEditors),
        new MergeToolToggleAction("EditorToggleShowIndentLines", MergeToolEditorSetting.INDENT_LINES, myEditors),
        new MergeToolToggleAction("EditorToggleUseSoftWraps", MergeToolEditorSetting.SOFT_WRAPS, myEditors)
      };
    }
  }

  /**
   * Common class for all actions toggling merge tool editor settings.
   */
  private static class MergeToolToggleAction extends ToggleAction {

    private final MergeToolEditorSetting mySetting;
    private final Collection<Editor> myEditors;

    private MergeToolToggleAction(String actionId, MergeToolEditorSetting setting, Collection<Editor> editors) {
      super(ActionsBundle.actionText(actionId), ActionsBundle.actionDescription(actionId), null);
      mySetting = setting;
      myEditors = editors;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return getPreference(e, mySetting);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      setPreference(e, mySetting, state);
      for (Editor editor : myEditors) {
        mySetting.apply(editor, state);
      }
    }

    private static void setPreference(AnActionEvent event, MergeToolEditorSetting preference, boolean state) {
      MergeToolSettings settings = getSettings(event.getProject());
      if (settings != null) {
        settings.setPreference(preference, state);
      }
    }

    private static boolean getPreference(AnActionEvent event, MergeToolEditorSetting preference) {
      MergeToolSettings settings = getSettings(event.getProject());
      if (settings != null) {
        return settings.getPreference(preference);
      }
      return false;
    }

    @Nullable
    private static MergeToolSettings getSettings(@Nullable Project project) {
      if (project != null) {
        return ServiceManager.getService(project, MergeToolSettings.class);
      }
      return null;
    }
  }

}
