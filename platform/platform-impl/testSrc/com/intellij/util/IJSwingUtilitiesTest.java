/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class IJSwingUtilitiesTest extends TestCase {
  private final JPanel myPanel = new JPanel();
  private final Assertion CHECK = new Assertion();

  public void testNoChildren() {
    CHECK.empty(getChildren());
  }

  public void testOneLevel() {
    MockComponent label1 = new MockComponent("1");
    myPanel.add(label1);
    MockComponent label2 = new MockComponent("2");
    myPanel.add(label2);
    CHECK.compareAll(new JComponent[]{label1, label2}, getChildren());
  }

  public void testubTree() {
    MockComponent label1 = new MockComponent("1");
    MockComponent label2 = new MockComponent("2");
    MockComponent label3 = new MockComponent("3");
    MockComponent label4 = new MockComponent("4");
    myPanel.add(label1);
    JPanel subPanel = new JPanel();
    myPanel.add(subPanel);
    subPanel.add(label2);
    subPanel.add(label3);
    myPanel.add(label4);
    CHECK.compareAll(new JComponent[]{label1, subPanel, label2, label3, label4}, getChildren());
  }

  private List<Component> getChildren() {
    return ContainerUtil.collect(IJSwingUtilities.getChildren(myPanel));
  }

  private static class MockComponent extends JComponent {
    private final String myName;

    public MockComponent(String name) {
      myName = name;
    }

    public String toString() {
      return myName;
    }
  }
}
