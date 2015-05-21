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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.actions.ActivateToolWindowAction;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.PairFunction;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author gregsh
 */
public class EditorEmptyTextPainter {

  public void paintEmptyText(final EditorsSplitters splitters, Graphics g) {
    boolean isDarkBackground = UIUtil.isUnderDarcula();
    UISettings.setupAntialiasing(g);
    g.setColor(new JBColor(isDarkBackground ? Gray._230 : Gray._80, Gray._160));
    g.setFont(JBUI.Fonts.label(isDarkBackground ? 24f : 20f));

    UIUtil.TextPainter painter = new UIUtil.TextPainter().withLineSpacing(1.5f);
    painter.withShadow(true, new JBColor(Gray._200.withAlpha(100), Gray._0.withAlpha(255)));

    painter.appendLine("No files are open").underlined(new JBColor(Gray._150, Gray._180));

    advertiseActions(splitters, painter);

    painter.draw(g, new PairFunction<Integer, Integer, Couple<Integer>>() {
      @Override
      public Couple<Integer> fun(Integer width, Integer height) {
        Dimension s = splitters.getSize();
        return Couple.of((s.width - width) / 2, (s.height - height) / 2);
      }
    });
  }

  protected void advertiseActions(EditorsSplitters splitters, UIUtil.TextPainter painter) {
    appendSearchEverywhere(painter);
    appendToolWindow(painter, "Open Project View", ToolWindowId.PROJECT_VIEW, splitters);
    appendAction(painter, "Open a file by name", getActionShortcutText("GotoFile"));
    appendAction(painter, "Open Recent Files", getActionShortcutText(IdeActions.ACTION_RECENT_FILES));
    appendAction(painter, "Open Navigation Bar", getActionShortcutText("ShowNavBar"));
    appendLine(painter, "Drag and Drop file(s) here from " + ShowFilePathAction.getFileManagerName());
  }

  protected void appendSearchEverywhere(UIUtil.TextPainter painter) {
    Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE);
    if (shortcuts.length == 0) {
      appendAction(painter, "Search Everywhere", "Double " + (SystemInfo.isMac ? MacKeymapUtil.SHIFT : "Shift"));
    }
    else {
      appendAction(painter, "Search Everywhere", KeymapUtil.getShortcutsText(shortcuts));
    }
  }

  protected void appendToolWindow(UIUtil.TextPainter painter, String action, String toolWindowId, EditorsSplitters splitters) {
    if (!isToolwindowVisible(splitters, toolWindowId)) {
      String activateActionId = ActivateToolWindowAction.getActionIdForToolWindow(toolWindowId);
      appendAction(painter, action, getActionShortcutText(activateActionId));
    }
  }

  protected void appendAction(UIUtil.TextPainter painter, String action, String shortcut) {
    if (StringUtil.isEmpty(shortcut)) return;
    appendLine(painter, action + " with " + "<shortcut>" + shortcut + "</shortcut>");
  }

  protected void appendLine(UIUtil.TextPainter painter, String line) {
    painter.appendLine(line).smaller().withBullet();
  }

  protected String getActionShortcutText(String actionId) {
    return KeymapUtil.getFirstKeyboardShortcutText(actionId);
  }

  protected static boolean isToolwindowVisible(EditorsSplitters splitters, String toolwindowId) {
    final Window frame = SwingUtilities.getWindowAncestor(splitters);
    if (frame instanceof IdeFrameImpl) {
      final Project project = ((IdeFrameImpl)frame).getProject();
      if (project != null) {
        if (!project.isInitialized()) return true;
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolwindowId);
        return toolWindow != null && toolWindow.isVisible();
      }
    }

    return false;
  }
}
