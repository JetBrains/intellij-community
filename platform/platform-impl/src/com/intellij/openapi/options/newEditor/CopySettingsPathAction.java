// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.Component;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.function.Supplier;

import static com.intellij.idea.ActionsBundle.message;
import static com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT;
import static com.intellij.openapi.util.SystemInfo.isMac;
import static com.intellij.util.ui.UIUtil.getParentOfType;

public final class CopySettingsPathAction extends DumbAwareAction {
  public CopySettingsPathAction() {
    super(getName(), message("action.CopySettingsPath.description"), null);
    setEnabledInModalContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Component component = event.getData(CONTEXT_COMPONENT);
    SettingsEditor editor = getParentOfType(SettingsEditor.class, component);
    event.getPresentation().setEnabledAndVisible(editor != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Component component = event.getData(CONTEXT_COMPONENT);
    if (component instanceof JTree) {
      SettingsTreeView view = getParentOfType(SettingsTreeView.class, component);
      if (view != null) {
        copy(view.createTransferable(event.getInputEvent()));
        return;
      }
    }
    SettingsEditor editor = getParentOfType(SettingsEditor.class, component);
    Collection<String> names = editor == null ? null : editor.getPathNames();
    if (names == null || names.isEmpty()) return;

    ConfigurableEditor inner = getParentOfType(ConfigurableEditor.class, component);
    if (inner != null) {
      String label = getTextLabel(component);
      ArrayDeque<String> path = new ArrayDeque<>();
      while (component != null && component != inner) {
        if (component instanceof JBTabs) {
          JBTabs tabs = (JBTabs)component;
          TabInfo info = tabs.getSelectedInfo();
          if (info != null) path.addFirst(info.getText());
        }
        if (component instanceof JTabbedPane) {
          JTabbedPane pane = (JTabbedPane)component;
          int index = pane.getSelectedIndex();
          path.addFirst(pane.getTitleAt(index));
        }
        if (component instanceof JComponent) {
          JComponent c = (JComponent)component;
          Border border = c.getBorder();
          if (border instanceof TitledBorder) {
            TitledBorder tb = (TitledBorder)border;
            String title = tb.getTitle();
            if (title != null && !title.isEmpty()) path.addFirst(title);
          }
        }
        component = component.getParent();
      }
      names.addAll(path);
      if (label != null) names.add(label);
    }
    copy(createTransferable(names));
  }

  @NotNull
  private static String getName() {
    return message(isMac ? "action.CopySettingsPath.mac.text" : "action.CopySettingsPath.text");
  }

  @NotNull
  static Action createSwingAction(@NotNull Supplier<? extends Transferable> supplier) {
    AbstractAction action = new AbstractAction(getName()) {
      {
      }

      @Override
      public void actionPerformed(ActionEvent event) {
        copy(supplier.get());
      }
    };
    KeyboardShortcut shortcut = ActionManager.getInstance().getKeyboardShortcut("CopySettingsPath");
    if (shortcut != null) action.putValue(Action.ACCELERATOR_KEY, shortcut.getFirstKeyStroke());
    return action;
  }

  @Nullable
  static Transferable createTransferable(@Nullable Collection<String> names) {
    if (names == null || names.isEmpty()) return null;
    StringBuilder sb = new StringBuilder(isMac ? "Preferences" : "File | Settings");
    for (String object : names) sb.append(" | ").append(object);
    return new TextTransferable(sb.toString());
  }

  static boolean copy(@Nullable Transferable transferable) {
    if (transferable == null) return false;
    CopyPasteManager.getInstance().setContents(transferable);
    return true;
  }

  private static String getTextLabel(Object component) {
    if (component instanceof JToggleButton) {
      JToggleButton button = (JToggleButton)component;
      String text = button.getText();
      if (text != null && !text.isEmpty()) return text;
    }
    // find corresponding label
    if (component instanceof JLabel) {
      JLabel label = (JLabel)component;
      String text = label.getText();
      if (text != null && !text.isEmpty()) return text;
    }
    else if (component instanceof JComponent) {
      JComponent c = (JComponent)component;
      return getTextLabel(c.getClientProperty("labeledBy"));
    }
    return null;
  }
}
