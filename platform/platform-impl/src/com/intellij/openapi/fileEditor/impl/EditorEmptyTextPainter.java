// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ActivateToolWindowAction;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.Gray;
import com.intellij.ui.IslandsState;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

@Internal
public class EditorEmptyTextPainter {
  public void paintEmptyText(@NotNull JComponent splitters, @NotNull Graphics g) {
    if (!IslandsState.Companion.isEnabled()) {
      JRootPane rootPane = splitters.getRootPane();
      if (rootPane.getGlassPane() == splitters) {
        EditorsSplitters editorArea = UIUtil.findComponentOfType((JComponent)rootPane.getContentPane(), EditorsSplitters.class);
        if (editorArea != null) {
          Point shift = SwingUtilities.convertPoint(editorArea, 0, 0, splitters);
          g.translate(shift.x, shift.y);
          doPaintEmptyText(editorArea, g);
          g.translate(-shift.x, -shift.y);
        }
        else {
          doPaintEmptyText(splitters, g);
        }
      }
      else {
        doPaintEmptyText(splitters, g);
      }
    }
  }

  @Internal
  public void doPaintEmptyText(@NotNull JComponent splitters, @NotNull Graphics g) {
    if (!isEnabled()) {
      return;
    }
    if (splitters instanceof EditorsSplitters && !((EditorsSplitters)splitters).isEmptyTextPaintingAllowed()) {
      return;
    }

    UISettings.setupAntialiasing(g);

    UIUtil.TextPainter painter = createTextPainter().withColor(JBColor.namedColor("Label.infoForeground", new JBColor(Gray._80, Gray._160)))
      .withShortcutColor(JBColor.namedColor("Shortcut.foreground", new JBColor(0x0, 0xDFE1E5)));

    advertiseActions(splitters, painter);
    painter.draw(g, (width, ignored) -> {
      Dimension s = splitters.getSize();
      int w = (s.width - width) / 2;
      int h = (int)(s.height * heightRatio());
      return Couple.of(w, h);
    });
  }

  protected double heightRatio() {
    return 0.375; // fix vertical position @ golden ratio
  }

  protected void advertiseActions(@NotNull JComponent splitters, @NotNull UIUtil.TextPainter painter) {
    appendPromotedActions(splitters, painter);
    appendSearchEverywhere(painter);
    appendToolWindow(painter, IdeBundle.message("empty.text.project.view"), ToolWindowId.PROJECT_VIEW, splitters);
    appendAction(painter, IdeBundle.message("empty.text.go.to.file"), getActionShortcutText("GotoFile"));
    appendAction(painter, IdeBundle.message("empty.text.recent.files"), getActionShortcutText(IdeActions.ACTION_RECENT_FILES));
    appendAction(painter, IdeBundle.message("empty.text.navigation.bar"), getActionShortcutText("ShowNavBar"));
    appendDnd(painter);
  }

  private void appendPromotedActions(@NotNull JComponent splitters, @NotNull UIUtil.TextPainter painter) {
    for (EditorEmptyTextPromotedActionProvider provider : EditorEmptyTextPromotedActionProvider.EP_NAME.getExtensionList()) {
      EditorEmptyTextPromotedActionProvider.PromotedAction action = provider.getPromotedAction(splitters);
      if (action != null) {
        appendAction(painter, action.getText(), getShortcutsText(action.getActionId()));
      }
    }
  }

  protected void appendDnd(@NotNull UIUtil.TextPainter painter) {
    appendLine(painter, IdeBundle.message("empty.text.drop.files.to.open"));
  }

  protected void appendSearchEverywhere(@NotNull UIUtil.TextPainter painter) {
    appendAction(painter, IdeBundle.message("empty.text.search.everywhere"), getSearchEverywhereShortcutText());
  }

  protected @Nullable String getSearchEverywhereShortcutText() {
    return getShortcutsText(IdeActions.ACTION_SEARCH_EVERYWHERE);
  }

  private static @Nullable String getShortcutsText(@NonNls @NotNull String actionId) {
    Shortcut[] shortcuts = KeymapUtil.getActiveKeymapShortcuts(actionId).getShortcuts();
    return shortcuts.length == 0 ? null : Arrays.stream(shortcuts)
      .sorted(Comparator.comparingInt(EditorEmptyTextPainter::getShortcutDisplayPriority))
      .map(KeymapUtil::getShortcutText)
      .collect(Collectors.joining(" " + IdeBundle.message("empty.text.shortcut.separator") + " "));
  }

  private static int getShortcutDisplayPriority(@NotNull Shortcut shortcut) {
    return shortcut instanceof KeyboardModifierGestureShortcut ? 0 : 1;
  }

  protected void appendToolWindow(@NotNull UIUtil.TextPainter painter,
                                  @NotNull @Nls String action,
                                  @NotNull String toolWindowId,
                                  @NotNull JComponent splitters) {
    if (!isToolwindowVisible(splitters, toolWindowId)) {
      String activateActionId = ActivateToolWindowAction.Manager.getActionIdForToolWindow(toolWindowId);
      appendAction(painter, action, getActionShortcutText(activateActionId));
    }
  }

  protected void appendAction(@NotNull UIUtil.TextPainter painter, @NotNull @Nls String action, @Nullable String shortcut) {
    if (Strings.isEmpty(shortcut)) {
      return;
    }
    appendLine(painter, action + " " + "<shortcut>" + shortcut + "</shortcut>");
  }

  protected void appendLine(@NotNull UIUtil.TextPainter painter, @NotNull String line) {
    painter.appendLine(line);
  }

  protected @NotNull String getActionShortcutText(@NonNls @NotNull String actionId) {
    return KeymapUtil.getFirstKeyboardShortcutText(actionId);
  }

  protected static boolean isToolwindowVisible(@NotNull JComponent splitters, @NotNull String toolwindowId) {
    Project project = ProjectUtil.getProjectForComponent(splitters);
    if (project != null) {
      if (!project.isInitialized()) return true;
      ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolwindowId);
      return toolWindow != null && toolWindow.isVisible();
    }
    return false;
  }

  public static @NotNull UIUtil.TextPainter createTextPainter() {
    return new UIUtil.TextPainter()
      .withLineSpacing(1.8f)
      .withColor(JBColor.namedColor("Editor.foreground", new JBColor(Gray._80, Gray._160)))
      .withFont(JBUI.Fonts.label(16f));
  }

  static boolean isEnabled() {
    return Registry.is("editor.paint.empty.text", true);
  }
}
