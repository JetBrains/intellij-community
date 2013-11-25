/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.idea.Bombed;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class FinderRecursivePanelSelectionUpdateTest extends PlatformTestCase {

  @Bombed(year = 2014, month = Calendar.JANUARY, day = 31, user = "Yann Cebron")
  public void testUpdate() throws InterruptedException {
    StringFinderRecursivePanel panel_0 = new StringFinderRecursivePanel(getProject()) {
      @NotNull
      @Override
      protected JComponent createRightComponent(String s) {
        return new StringFinderRecursivePanel(this) {
          @Override
          @NotNull
          protected JComponent createRightComponent(String s) {
            return new StringFinderRecursivePanel(this) {
              @Override
              @NotNull
              protected JComponent createRightComponent(String s) {
                return new StringFinderRecursivePanel(this);
              }
            };
          }
        };
      }
    };
    disposeOnTearDown(panel_0);

    panel_0.setTestSelectedIndex(0);
    //panel_0.updateRightComponent(true);

    StringFinderRecursivePanel panel_1 = (StringFinderRecursivePanel)panel_0.getSecondComponent();
    panel_1.setTestSelectedIndex(1);

    StringFinderRecursivePanel panel_2 = (StringFinderRecursivePanel)panel_1.getSecondComponent();
    panel_2.setTestSelectedIndex(2);

    StringFinderRecursivePanel panel_3 = (StringFinderRecursivePanel)panel_2.getSecondComponent();
    panel_3.setTestSelectedIndex(3);

    panel_0.updatePanel();

    assertEquals("a", panel_0.getSelectedValue());
    assertEquals("b", panel_1.getSelectedValue());
    assertEquals("c", panel_2.getSelectedValue());
    assertEquals("d", panel_3.getSelectedValue());
  }


  private class StringFinderRecursivePanel extends FinderRecursivePanel<String> {

    private JBList myList;

    private StringFinderRecursivePanel(Project project) {
      super(project, "fooPanel");
      init();
    }

    public StringFinderRecursivePanel(StringFinderRecursivePanel panel) {
      super(panel);
      init();
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
    protected JBList createList() {
      myList = super.createList();
      ((CollectionListModel)myList.getModel()).replaceAll(getListItems());
      return myList;
    }

    private void setTestSelectedIndex(int index) {
      myList.setSelectedIndex(index);
    }
  }
}
