/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

    final StringFinderRecursivePanel panel_1 = new StringFinderRecursivePanel(panel_0);
    panel_1.setSecondComponent(panel_0);
    panel_0.setTestSelectedIndex(0);

    final StringFinderRecursivePanel panel_2 = new StringFinderRecursivePanel(panel_1);
    panel_1.setSecondComponent(panel_2);
    panel_1.setTestSelectedIndex(1);

    final StringFinderRecursivePanel panel_3 = new StringFinderRecursivePanel(panel_2);
    panel_2.setSecondComponent(panel_3);
    panel_2.setTestSelectedIndex(2);

    panel_3.setSecondComponent(new StringFinderRecursivePanel(panel_3));
    panel_3.setTestSelectedIndex(3);

    panel_0.updatePanel();

    assertEquals("a", panel_0.getSelectedValue());
    assertEquals("b", panel_1.getSelectedValue());
    assertEquals("c", panel_2.getSelectedValue());
    assertEquals("d", panel_3.getSelectedValue());
  }


  @SuppressWarnings("InnerClassMayBeStatic")
  private class StringFinderRecursivePanel extends FinderRecursivePanel<String> {

    private JBList<String> myList;

    private StringFinderRecursivePanel(Project project) {
      super(project, "fooPanel");
      initPanel();
    }

    public StringFinderRecursivePanel(StringFinderRecursivePanel panel) {
      super(panel);
      initPanel();
    }

    @NotNull
    @Override
    protected List<String> getListItems() {
      return Arrays.asList("a", "b", "c", "d");
    }

    @NotNull
    @Override
    protected String getItemText(String s) {
      return s;
    }

    @Override
    protected boolean hasChildren(String s) {
      return true;
    }

    @Nullable
    @Override
    protected JComponent createRightComponent(String s) {
      return null;
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
