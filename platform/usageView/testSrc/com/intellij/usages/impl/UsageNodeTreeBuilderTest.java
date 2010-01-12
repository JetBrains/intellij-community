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

package com.intellij.usages.impl;

import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.usages.*;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class UsageNodeTreeBuilderTest extends TestCase {
  protected void setUp() throws Exception {
    super.setUp();
  }

  public void testNoGroupingRules() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{2, 3, 0}, new UsageGroupingRule[] {});

    assertNotNull(groupNode);
    
    assertNull(groupNode.getParent());

    assertEquals("[2, 3, 0]", groupNode.toString());
  }

  public void testOneGroupingRuleOnly() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{0, 1, 0, 1 , 1}, new UsageGroupingRule[] {new OddEvenGroupingRule()});
    assertEquals("[Even[0, 0], Odd[1, 1, 1]]", groupNode.toString());
  }

  public void testNotGroupedItemsComeToEnd() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{0, 1, 0, 1 , 1, 1003, 1002, 1001}, new UsageGroupingRule[] {new OddEvenGroupingRule()});
    assertEquals("[Even[0, 0], Odd[1, 1, 1], 1003, 1002, 1001]", groupNode.toString());
  }

  public void test2Groupings() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{0, 1, 2, 3, 12, 13, 14, 15, 101, 103, 102, 105, 10001, 10002, 10003}, new UsageGroupingRule[] {
      new OddEvenGroupingRule(),
      new LogGroupingRule()});

    assertEquals("[Even[1[0, 2], 2[12, 14], 3[102]], Odd[1[1, 3], 2[13, 15], 3[101, 103, 105]], 5[10001, 10002, 10003]]", groupNode.toString());
  }

  public void testDifferentRulesDontDependOnOrder() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{10003, 0}, new UsageGroupingRule[] {
      new OddEvenGroupingRule(),
      new LogGroupingRule()});

    assertEquals("[Even[1[0]], 5[10003]]", groupNode.toString());
  }

  public void testGroupsFromDifferentRulesAreCorrectlySorted() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{10003, 0, 1, 2, 3, 12, 13, 14, 15, 101, 103, 102, 105, 10001, 10002}, new UsageGroupingRule[] {
      new OddEvenGroupingRule(),
      new LogGroupingRule()});

    assertEquals("[Even[1[0, 2], 2[12, 14], 3[102]], Odd[1[1, 3], 2[13, 15], 3[101, 103, 105]], 5[10003, 10001, 10002]]", groupNode.toString());
  }

  private static Usage createUsage(int index) {
    return new MockUsage(index);
  }

  private static GroupNode buildUsageTree(int[] indices, UsageGroupingRule[] rules) {
    Usage[] usages = new Usage[indices.length];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = createUsage(indices[i]);
    }

    //DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("temp"));
    UsageViewTreeModelBuilder model = new UsageViewTreeModelBuilder(new UsageViewPresentation(), new UsageTarget[0]);
    GroupNode rootNode = new GroupNode(null, 0, model);
    model.setRoot(rootNode);
    UsageNodeTreeBuilder usageNodeTreeBuilder = new UsageNodeTreeBuilder(rules, UsageFilteringRule.EMPTY_ARRAY, rootNode);
    for (Usage usage : usages) {
      usageNodeTreeBuilder.appendUsage(usage);
      UIUtil.pump();
    }

    return rootNode;
  }

  private static class LogGroupingRule implements UsageGroupingRule {
    public UsageGroup groupUsage(Usage usage) {
      return new LogUsageGroup(usage.toString().length());
    }
  }

  private static class LogUsageGroup implements UsageGroup {
    private final int myPower;

    public LogUsageGroup(int power) {
      myPower = power;
    }

    public void update() {
    }

    public Icon getIcon(boolean isOpen) { return null; }
    @NotNull
    public String getText(UsageView view) { return String.valueOf(myPower); }

    public FileStatus getFileStatus() {
      return null;
    }

    public boolean isValid() {
      return false;
    }

    public String toString() {
      return getText(null);
    }

    public int compareTo(UsageGroup o) {
      if (!(o instanceof LogUsageGroup)) return 1;
      return myPower - ((LogUsageGroup)o).myPower;
    }

    public boolean equals(Object o) {
      if (!(o instanceof LogUsageGroup)) return false;
      return myPower == ((LogUsageGroup)o).myPower;
    }
    public int hashCode() { return myPower; }

    public void navigate(boolean requestFocus) { }

    public boolean canNavigate() { return false; }

    public boolean canNavigateToSource() {
      return false;
    }
  }

  private static class OddEvenGroupingRule implements UsageGroupingRule {
    private static final UsageGroup EVEN = new UsageGroup() {
      public Icon getIcon(boolean isOpen) { return null; }
      @NotNull
      public String getText(UsageView view) { return "Even"; }

      public void update() {
      }

      public FileStatus getFileStatus() {
        return null;
      }

      public boolean isValid() {
        return false;
      }

      public void navigate(boolean focus) throws UnsupportedOperationException { }
      public boolean canNavigate() { return false; }

      public boolean canNavigateToSource() {
        return false;
      }

      public int compareTo(UsageGroup o) { return o == ODD ? -1 : 0; }
      public String toString() { return getText(null); }
    };

    private static final UsageGroup ODD = new UsageGroup() {
      public Icon getIcon(boolean isOpen) { return null; }
      @NotNull
      public String getText(UsageView view) { return "Odd"; }

      public void update() {
      }

      public FileStatus getFileStatus() {
        return null;
      }

      public boolean isValid() {
        return false;
      }

      public void navigate(boolean focus) throws UnsupportedOperationException { }
      public boolean canNavigate() { return false; }

      public boolean canNavigateToSource() {
        return false;
      }

      public int compareTo(UsageGroup o) { return o == EVEN ? 1 : 0; }
      public String toString() { return getText(null); }
    };

    public UsageGroup groupUsage(Usage usage) {
      MockUsage mockUsage = ((MockUsage)usage);

      if (mockUsage.getId() > 1000) return null;

      if (mockUsage.getId() % 2 == 0) {
        return EVEN;
      }
      else {
        return ODD;
      }
    }
  }

  private static class MockUsage implements Usage {
    private final int myId;

    public MockUsage(int index) {
      myId = index;
    }


    public int getId() {
      return myId;
    }

    @NotNull
    public UsagePresentation getPresentation() {
      return new UsagePresentation() {
        @NotNull
        public TextChunk[] getText() {
          return new TextChunk[0];
        }

        @NotNull
        public String getPlainText() {
          return "";
        }

        public Icon getIcon() {
          return null;
        }

        public String getTooltipText() {
          return null;
        }
      };
    }

    public boolean isValid() // ?
    {
      return false;
    }

    public FileEditorLocation getLocation() {
      return null;
    }

    public void selectInEditor() {
    }

    public void highlightInEditor() {
    }

    public String toString() {
      return String.valueOf(myId);
    }

    public int compareTo(Usage o) {
      return getId() - ((MockUsage)o).getId();
    }

    public void navigate(boolean requestFocus) {
    }

    public boolean canNavigate() {
      return false;
    }

    public boolean canNavigateToSource() {
      return false;
    }

    public boolean isReadOnly() {
      return false;
    }
  }
}
