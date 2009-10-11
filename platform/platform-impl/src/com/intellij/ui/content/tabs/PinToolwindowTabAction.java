/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui.content.tabs;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedPaneContentUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public class PinToolwindowTabAction extends ToggleAction {
  public static final String ACTION_NAME = "PinToolwindowTab";

  private static final Icon ICON = IconLoader.getIcon("/general/pin_tab.png");

  public static AnAction getPinAction() {
    return ActionManager.getInstance().getAction(ACTION_NAME);
  }

  public PinToolwindowTabAction() {
    super("Pin Tab", "Pin tool window tab", ICON);
  }

  @Nullable
  private static Content getContextContent(AnActionEvent event) {
    final ToolWindow window = PlatformDataKeys.TOOL_WINDOW.getData(event.getDataContext());
    if (window != null) {
      final ContentManager contentManager = window.getContentManager();
      if (contentManager != null) {
        return contentManager.getSelectedContent();
      }
    }

    return null;
  }

  public boolean isSelected(AnActionEvent event) {
    final Content content = getContextContent(event);
    return content != null && content.isPinned();
  }

  public void setSelected(AnActionEvent event, boolean flag) {
    final Content content = getContextContent(event);
    if (content != null) content.setPinned(flag);
  }

  public void update(AnActionEvent event) {
    super.update(event);
    Presentation presentation = event.getPresentation();
    final Content content = getContextContent(event);
    boolean enabled = content != null && content.isPinnable();

    if (enabled) {
      presentation.setIcon(
        TabbedPaneContentUI.POPUP_PLACE.equals(event.getPlace()) || ToolWindowContentUi.POPUP_PLACE.equals(event.getPlace()) ? null : ICON);
    }

    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }
}
