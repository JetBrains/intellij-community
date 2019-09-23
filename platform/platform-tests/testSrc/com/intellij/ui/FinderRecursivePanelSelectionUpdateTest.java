/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.SkipInHeadlessEnvironment;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

@SkipInHeadlessEnvironment
public class FinderRecursivePanelSelectionUpdateTest extends LightPlatformTestCase {

  public void testUpdate() {
    StringFinderRecursivePanel panel_0 = new StringFinderRecursivePanel(getProject());
    disposeOnTearDown(panel_0);
    panel_0.setTestSelectedIndex(0);

    final StringFinderRecursivePanel panel_1 = new StringFinderRecursivePanel(panel_0);
    panel_1.setTestSelectedIndex(1);

    final StringFinderRecursivePanel panel_2 = new StringFinderRecursivePanel(panel_1);
    panel_2.setTestSelectedIndex(2);

    final StringFinderRecursivePanel panel_3 = new StringFinderRecursivePanel(panel_2);
    panel_3.setTestSelectedIndex(3);

    panel_0.updatePanel();

    assertEquals("a", panel_0.getSelectedValue());
    assertEquals("b", panel_1.getSelectedValue());
    assertEquals("c", panel_2.getSelectedValue());
    assertEquals("d", panel_3.getSelectedValue());
  }

  public void testUpdateSelectedPath() {
    StringFinderRecursivePanel panel_0 = new StringFinderRecursivePanel(getProject());
    disposeOnTearDown(panel_0);

    final StringFinderRecursivePanel panel_1 = new StringFinderRecursivePanel(panel_0);
    panel_0.setRightComponent(panel_1);

    final StringFinderRecursivePanel panel_2 = new StringFinderRecursivePanel(panel_1);
    panel_1.setRightComponent(panel_2);

    panel_0.updateSelectedPath("a", "b", "c");

    assertEquals("a", panel_0.getSelectedValue());
    assertEquals("b", panel_1.getSelectedValue());
    assertEquals("c", panel_2.getSelectedValue());
  }

  public void testUpdateSelectedPathFailsNoRightComponent() {
    StringFinderRecursivePanel panel_0 = new StringFinderRecursivePanel(getProject());
    disposeOnTearDown(panel_0);

    try {
      panel_0.updateSelectedPath("a", "b");
      fail();
    }
    catch (Exception e) {
      final IllegalStateException exception = assertInstanceOf(e, IllegalStateException.class);
      assertEquals("failed to select idx=1: component=null, pathToSelect=[a, b]", exception.getMessage());
    }
  }

  public void testUpdateSelectedPathFailsNoFinderRecursivePanelRightComponent() {
    StringFinderRecursivePanel panel_0 = new StringFinderRecursivePanel(getProject());
    disposeOnTearDown(panel_0);
    final JLabel placeholder = new JLabel("placeholder");
    panel_0.setRightComponent(placeholder);

    try {
      panel_0.updateSelectedPath("a", "b");
      fail();
    }
    catch (Exception e) {
      final IllegalStateException exception = assertInstanceOf(e, IllegalStateException.class);
      assertEquals("failed to select idx=1: component=" + placeholder.toString() + ", pathToSelect=[a, b]", exception.getMessage());
    }
  }

  @SuppressWarnings("InnerClassMayBeStatic")
  private class StringFinderRecursivePanel extends FinderRecursivePanel<String> {

    private JBList<String> myList;
    private JComponent myRightComponent;

    private StringFinderRecursivePanel(Project project) {
      super(project, "fooPanel");
      initPanel();
    }

    StringFinderRecursivePanel(StringFinderRecursivePanel panel) {
      super(panel);
      initPanel();
    }

    public void setRightComponent(JComponent rightComponent) {
      myRightComponent = rightComponent;
    }

    @NotNull
    @Override
    protected List<String> getListItems() {
      return Arrays.asList("a", "b", "c", "d");
    }

    @NotNull
    @Override
    protected String getItemText(@NotNull String s) {
      return s;
    }

    @Override
    protected boolean hasChildren(@NotNull String s) {
      return true;
    }

    @Nullable
    @Override
    protected JComponent createRightComponent(@NotNull String s) {
      return myRightComponent;
    }

    @Override
    protected JBList<String> createList() {
      myList = super.createList();
      ((CollectionListModel<String>)myList.getModel()).replaceAll(getListItems());
      return myList;
    }

    private void setTestSelectedIndex(int index) {
      myList.setSelectedIndex(index);
    }
  }
}
