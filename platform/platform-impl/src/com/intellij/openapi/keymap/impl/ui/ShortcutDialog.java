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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.GradientViewport;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.Nullable;

import java.awt.BorderLayout;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

/**
 * @author Sergey.Malenkov
 */
abstract class ShortcutDialog<T extends Shortcut> extends DialogWrapper {
  private final SimpleColoredComponent myAction = new SimpleColoredComponent();
  private final JBPanel myConflictsContainer = new JBPanel(new VerticalLayout(0));
  private final JBPanel myConflictsPanel = new JBPanel(new BorderLayout())
    .withBorder(JBUI.Borders.empty(10, 10, 0, 10))
    .withPreferredHeight(64)
    .withMinimumHeight(64);

  protected final ShortcutPanel<T> myShortcutPanel;
  private final Project myProject;
  private String myActionId;
  private Keymap myKeymap;
  private Group myGroup;

  ShortcutDialog(Component parent, String title, ShortcutPanel<T> panel) {
    super(parent, true);
    myShortcutPanel = panel;
    myProject = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parent));
    setTitle(KeyMapBundle.message(title));
  }

  String getActionPath(String actionId) {
    return myGroup == null ? null : myGroup.getActionQualifiedPath(actionId);
  }

  boolean hasConflicts() {
    return myConflictsPanel.isVisible();
  }

  abstract Collection<String> getConflicts(T shortcut, String actionId, Keymap keymap);

  abstract T toShortcut(Object value);

  void setShortcut(T shortcut) {
    if (!equal(shortcut, myShortcutPanel.getShortcut())) {
      myShortcutPanel.setShortcut(shortcut);
    }
    myConflictsContainer.removeAll();
    if (shortcut != null) {
      for (String id : getConflicts(shortcut, myActionId, myKeymap)) {
        String path = id.equals(myActionId) ? null : getActionPath(id);
        if (path != null) {
          SimpleColoredComponent component = new SimpleColoredComponent();
          fill(component, id, path);
          if (ScreenReader.isActive()) {
            // Supports TAB/Shift-TAB navigation
            component.setFocusable(true);
          }
          myConflictsContainer.add(VerticalLayout.TOP, component);
        }
      }
      myConflictsPanel.revalidate();
      myConflictsPanel.repaint();
    }
    myConflictsPanel.setVisible(0 < myConflictsContainer.getComponentCount());
  }

  T showAndGet(String id, Keymap keymap, QuickList... lists) {
    myActionId = id;
    myKeymap = keymap;
    myGroup = ActionsTreeUtil.createMainGroup(myProject, keymap, lists, null, false, null);
    fill(myAction, id, getActionPath(id));
    T firstShortcut = null;
    for (Shortcut shortcut : keymap.getShortcuts(id)) {
      firstShortcut = toShortcut(shortcut);
      if (firstShortcut != null) break;
    }
    setShortcut(firstShortcut);
    return showAndGet() ? myShortcutPanel.getShortcut() : null;
  }

  @Nullable
  @Override
  protected Border createContentPaneBorder() {
    return JBUI.Borders.empty();
  }

  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    JComponent panel = super.createSouthPanel();
    if (panel != null) {
      panel.setBorder(JBUI.Borders.empty(8, 12));
    }
    return panel;
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    myAction.setIpad(JBUI.insets(10, 10, 5, 10));
    myShortcutPanel.addPropertyChangeListener("shortcut", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent event) {
        setShortcut(toShortcut(event.getNewValue()));
      }
    });
    JBPanel result = new JBPanel(new BorderLayout()).withPreferredWidth(300).withMinimumWidth(200);
    result.add(BorderLayout.NORTH, myAction);
    result.add(BorderLayout.SOUTH, myShortcutPanel);
    return result;
  }

  @Override
  protected JComponent createCenterPanel() {
    JLabel icon = new JLabel(AllIcons.General.BalloonWarning);
    icon.setVerticalAlignment(SwingConstants.TOP);

    JLabel label = new JLabel(KeyMapBundle.message("dialog.conflicts.text"));
    label.setBorder(JBUI.Borders.emptyLeft(2));
    if (ScreenReader.isActive()) {
      // Supports TAB/Shift-TAB navigation
      label.setFocusable(true);
    }

    JScrollPane scroll = ScrollPaneFactory.createScrollPane(null, true);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setViewport(new GradientViewport(myConflictsContainer, JBUI.insets(5), false));
    scroll.getVerticalScrollBar().setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.setOpaque(false);

    JBPanel panel = new JBPanel(new BorderLayout());
    panel.add(BorderLayout.NORTH, label);
    panel.add(BorderLayout.CENTER, scroll);

    myConflictsPanel.add(BorderLayout.WEST, icon);
    myConflictsPanel.add(BorderLayout.CENTER, panel);
    myConflictsContainer.setOpaque(false);
    return myConflictsPanel;
  }

  private static boolean equal(Shortcut newShortcut, Shortcut oldShortcut) {
    return newShortcut == null ? oldShortcut == null : newShortcut.equals(oldShortcut);
  }

  private static void fill(SimpleColoredComponent component, String id, String path) {
    if (path == null) {
      component.append(id, SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
    }
    else {
      int index = path.lastIndexOf(" | ");
      if (index < 0) {
        component.append(path, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else {
        component.append(path.substring(index + 3), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        component.append(" in ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        component.append(path.substring(0, index), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
  }
}
