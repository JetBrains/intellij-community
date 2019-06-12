// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.junit.Assert.assertNotNull;

public class IdeSettingsDialogFixture extends IdeaDialogFixture<SettingsDialog> {
  @NotNull
  public static IdeSettingsDialogFixture find(@NotNull Robot robot) {
    return new IdeSettingsDialogFixture(robot, find(robot, SettingsDialog.class, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        String expectedTitle = SystemInfo.isMac ? "Preferences" : "Settings";
        return expectedTitle.equals(dialog.getTitle()) && dialog.isShowing();
      }
    }));
  }

  private IdeSettingsDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<SettingsDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public List<String> getProjectSettingsNames() {
    List<String> names = new ArrayList<>();
    JPanel optionsEditor = field("myEditor").ofType(JPanel.class).in(getDialogWrapper()).get();
    assertNotNull(optionsEditor);

    List<JComponent> trees = findComponentsOfType(optionsEditor, "com.intellij.openapi.options.newEditor.SettingsTreeView");
    assertThat(trees).hasSize(1);
    JComponent tree = trees.get(0);

    CachingSimpleNode root = field("myRoot").ofType(CachingSimpleNode.class).in(tree).get();
    assertNotNull(root);

    ConfigurableGroup[] groups = field("myGroups").ofType(ConfigurableGroup[].class).in(root).get();
    assertNotNull(groups);
    for (ConfigurableGroup current : groups) {
      Configurable[] configurables = current.getConfigurables();
      for (Configurable configurable : configurables) {
        names.add(configurable.getDisplayName());
      }
    }
    return names;
  }

  @NotNull
  private static List<JComponent> findComponentsOfType(@NotNull JComponent parent, @NotNull String typeName) {
    List<JComponent> result = new ArrayList<>();
    findComponentsOfType(typeName, result, parent);
    return result;
  }

  private static void findComponentsOfType(@NotNull String typeName, @NotNull List<? super JComponent> result, @Nullable JComponent parent) {
    if (parent == null) {
      return;
    }
    if (parent.getClass().getName().equals(typeName)) {
      result.add(parent);
    }
    for (Component c : parent.getComponents()) {
      if (c instanceof JComponent) {
        findComponentsOfType(typeName, result, (JComponent)c);
      }
    }
  }
}
