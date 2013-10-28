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

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

public class FinderRecursivePanelTest extends PlatformTestCase {

  public void testListModelMerge1() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 0, 0, "a");
  }

  public void testListModelMerge2() {
    assertMerge(new String[0], "a", "b", "c", "d");
  }

  public void testListModelMerge3() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 0, -1 /* nothing */);
  }

  public void testListModelMerge4() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 2, 2, "a", "b", "c", "d");
  }

  public void _testListModelMerge5() {
    assertMerge(new String[]{"a", "b", "c", "d"}, "d", "c", "b", "a");
  }

  public void testListModelMerge6() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 2, 1, "b", "c");
  }

  public void testListModelMerge7() {
    assertMerge(new String[]{"a", "b", "c", "d"}, 1, 0, "b", "c", "d", "e");
  }

  public void _testListModelMerge8() {
    assertMerge(new String[]{"a", "a", "b", "b"}, "b", "a", "b", "a");
  }

  public void testListModelMerge9() {
    assertMerge(new String[]{"a", "b"}, 1, 3, "e", "d", "a", "b");
  }

  public void testListModelMerge10() {
    assertMerge(new String[]{"a", "b"}, 0, -1, "c", "d");
  }

  private static void assertMerge(String[] items, int startSelection, int expectedSelection, String... newItems) {
    CollectionListModel<String> model = new CollectionListModel<String>();
    model.add(Arrays.asList(items));
    JBList list = new JBList(model);
    list.setSelectedIndex(startSelection);

    FinderRecursivePanel.mergeListItems(model, Arrays.asList(newItems));
    assertEquals(newItems.length, model.getSize());
    for (int i = 0; i < newItems.length; i++) {
      assertEquals(newItems[i], model.getElementAt(i));
    }
    assertEquals(expectedSelection, list.getSelectedIndex());
  }

  private static void assertMerge(String[] items, String... newItems) {
    assertMerge(items, -1, -1, newItems);
  }

  public void testUpdate() throws InterruptedException {
    StringFinderRecursivePanel panel_0 = new StringFinderRecursivePanel() {
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
    Disposer.register(myTestRootDisposable, panel_0);
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

    private StringFinderRecursivePanel() {
      super(FinderRecursivePanelTest.this.myProject, "fooPanel");
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
