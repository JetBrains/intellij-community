// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.junit5.RunInEdt;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.ui.FilterComponent;
import com.intellij.util.ui.UIUtil;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.Component;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The split mode host creates every settings page on a background thread, see IJPL-259245.
 * So the page must create its Swing component only in {@link KeymapPanel#createComponent()}.
 */
@RunInEdt(writeIntent = true)
@TestApplication
public class KeymapPanelTest {
  @Test
  void pageIsNotSwingComponent() {
    assertFalse(Component.class.isAssignableFrom(KeymapPanel.class));
  }

  @Test
  void backgroundThreadCreatesPageWithoutUi() throws Exception {
    KeymapPanel panel = ApplicationManager.getApplication().executeOnPooledThread(() -> new KeymapPanel()).get(1, TimeUnit.MINUTES);

    assertFalse(panel.isModified());
    assertNull(panel.getCurrentShortcuts(IdeActions.ACTION_EDITOR_COPY));
    panel.reset();
    panel.apply();
    panel.selectAction(IdeActions.ACTION_EDITOR_COPY);
    panel.showOption("copy");
    panel.disposeUIResources();
  }

  @Test
  void componentLifecycle() {
    KeymapPanel panel = new KeymapPanel();
    panel.selectAction(IdeActions.ACTION_EDITOR_COPY);

    JComponent component = panel.createComponent();
    assertNotNull(UIUtil.findComponentOfType(component, JTree.class));
    assertSame(component, panel.createComponent());

    panel.reset();
    assertNotNull(panel.getCurrentShortcuts(IdeActions.ACTION_EDITOR_COPY));
    assertFalse(panel.isModified());

    panel.disposeUIResources();
    assertNull(panel.getCurrentShortcuts(IdeActions.ACTION_EDITOR_COPY));

    JComponent recreated = panel.createComponent();
    assertNotSame(component, recreated);
    panel.reset();
    assertFalse(panel.isModified());
    panel.disposeUIResources();
  }

  @Test
  void showOptionBeforeComponentIsAppliedOnReset() {
    KeymapPanel panel = new KeymapPanel();
    panel.reset();
    panel.showOption("copy");

    JComponent component = panel.createComponent();
    FilterComponent filter = UIUtil.findComponentOfType(component, FilterComponent.class);
    assertNotNull(filter);
    assertEquals("", filter.getFilter());

    panel.reset();
    assertEquals("copy", filter.getFilter());
    Group copiedActions = getMainGroup(component);
    assertTrue(copiedActions.containsId(IdeActions.ACTION_EDITOR_COPY));
    assertFalse(copiedActions.containsId(IdeActions.ACTION_EDITOR_PASTE));

    panel.showOption("paste");
    assertEquals("paste", filter.getFilter());
    Group pastedActions = getMainGroup(component);
    assertTrue(pastedActions.containsId(IdeActions.ACTION_EDITOR_PASTE));
    assertFalse(pastedActions.containsId(IdeActions.ACTION_EDITOR_COPY));

    panel.reset();
    assertEquals("paste", filter.getFilter());
    panel.disposeUIResources();
  }

  @Test
  void showOptionKeepsOrRebuildsTheTreeAsNeeded() {
    KeymapPanel panel = new KeymapPanel();
    JComponent component = panel.createComponent();
    panel.reset();
    Group initialGroup = getMainGroup(component);

    panel.showOption("");
    assertSame(initialGroup, getMainGroup(component));

    panel.showOption("copy");
    Group filteredGroup = getMainGroup(component);
    assertNotSame(initialGroup, filteredGroup);
    assertTrue(filteredGroup.containsId(IdeActions.ACTION_EDITOR_COPY));
    assertFalse(filteredGroup.containsId(IdeActions.ACTION_EDITOR_PASTE));

    panel.showOption("copy");
    assertSame(filteredGroup, getMainGroup(component));
    panel.disposeUIResources();
  }

  private static Group getMainGroup(JComponent component) {
    JTree tree = UIUtil.findComponentOfType(component, JTree.class);
    assertNotNull(tree);
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)tree.getModel().getRoot();
    return (Group)root.getUserObject();
  }
}
