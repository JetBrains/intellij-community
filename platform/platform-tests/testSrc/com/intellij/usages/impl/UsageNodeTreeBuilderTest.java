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

package com.intellij.usages.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.usages.*;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleProvider;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class UsageNodeTreeBuilderTest extends LightPlatformTestCase {
  public void testNoGroupingRules() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{2, 3, 0}, UsageGroupingRule.EMPTY_ARRAY);

    assertNotNull(groupNode);
    
    assertNull(groupNode.getParent());

    assertEquals("Root [0, 2, 3]", groupNode.toString());
  }

  public void testOneGroupingRuleOnly() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{0, 1, 0, 1 , 1}, new UsageGroupingRule[] {new OddEvenGroupingRule()});
    assertEquals("Root [Even[0, 0], Odd[1, 1, 1]]", groupNode.toString());
  }

  public void testNotGroupedItemsComeToEnd() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{0, 1, 0, 1 , 1, 1003, 1002, 1001}, new UsageGroupingRule[] {new OddEvenGroupingRule()});
    assertEquals("Root [Even[0, 0], Odd[1, 1, 1], 1001, 1002, 1003]", groupNode.toString());
  }

  public void test2Groupings() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{0, 1, 2, 3, 12, 13, 14, 15, 101, 103, 102, 105, 10001, 10002, 10003}, new UsageGroupingRule[] {
      new OddEvenGroupingRule(),
      new LogGroupingRule()});

    assertEquals("Root [Even[1[0, 2], 2[12, 14], 3[102]], Odd[1[1, 3], 2[13, 15], 3[101, 103, 105]], 5[10001, 10002, 10003]]", groupNode.toString());
  }

  public void testDifferentRulesDontDependOnOrder() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{10003, 0}, new UsageGroupingRule[] {
      new OddEvenGroupingRule(),
      new LogGroupingRule()});

    assertEquals("Root [Even[1[0]], 5[10003]]", groupNode.toString());
  }

  public void testGroupsFromDifferentRulesAreCorrectlySorted() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{10003, 0, 1, 2, 3, 12, 13, 14, 15, 101, 103, 102, 105, 10001, 10002}, new UsageGroupingRule[] {
      new OddEvenGroupingRule(),
      new LogGroupingRule()});

    assertEquals("Root [Even[1[0, 2], 2[12, 14], 3[102]], Odd[1[1, 3], 2[13, 15], 3[101, 103, 105]], 5[10001, 10002, 10003]]", groupNode.toString());
  }

  private static Usage createUsage(int index) {
    return new MockUsage(index);
  }

  private GroupNode buildUsageTree(int[] indices, UsageGroupingRule[] rules) {
    Usage[] usages = new Usage[indices.length];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = createUsage(indices[i]);
    }

    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setUsagesString("searching for mock usages");

    ExtensionsArea area = Extensions.getRootArea();
    ExtensionPoint<UsageGroupingRuleProvider> point = area.getExtensionPoint(UsageGroupingRuleProvider.EP_NAME);
    UsageGroupingRuleProvider provider = new UsageGroupingRuleProvider() {
      @NotNull
      @Override
      public UsageGroupingRule[] getActiveRules(Project project) {
        return rules;
      }

      @NotNull
      @Override
      public AnAction[] createGroupingActions(UsageView view) {
        return AnAction.EMPTY_ARRAY;
      }
    };
    point.registerExtension(provider);
    try {
      UsageViewImpl usageView = new UsageViewImpl(getProject(), presentation, UsageTarget.EMPTY_ARRAY, null);
      Disposer.register(getTestRootDisposable(), usageView);
      for (Usage usage : usages) {
        usageView.appendUsage(usage);
      }
      UIUtil.dispatchAllInvocationEvents();

      return usageView.getRoot();
    }
    finally {
      point.unregisterExtension(provider);
    }
  }

  private static class LogGroupingRule implements UsageGroupingRule {
    @Override
    public UsageGroup groupUsage(@NotNull Usage usage) {
      return new LogUsageGroup(usage.toString().length());
    }
  }

  private static class LogUsageGroup implements UsageGroup {
    private final int myPower;

    LogUsageGroup(int power) {
      myPower = power;
    }

    @Override
    public void update() {
    }

    @Override
    public Icon getIcon(boolean isOpen) { return null; }
    @Override
    @NotNull
    public String getText(UsageView view) { return String.valueOf(myPower); }

    @Override
    public FileStatus getFileStatus() {
      return null;
    }

    @Override
    public boolean isValid() {
      return false;
    }

    public String toString() {
      return getText(null);
    }

    @Override
    public int compareTo(@NotNull UsageGroup o) {
      if (!(o instanceof LogUsageGroup)) return 1;
      return myPower - ((LogUsageGroup)o).myPower;
    }

    public boolean equals(Object o) {
      return o instanceof LogUsageGroup && myPower == ((LogUsageGroup)o).myPower;
    }
    public int hashCode() { return myPower; }

    @Override
    public void navigate(boolean requestFocus) { }

    @Override
    public boolean canNavigate() { return false; }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }
  }

  private static class OddEvenGroupingRule implements UsageGroupingRule {
    private static final UsageGroup EVEN = new UsageGroup() {
      @Override
      public Icon getIcon(boolean isOpen) { return null; }
      @Override
      @NotNull
      public String getText(UsageView view) { return "Even"; }

      @Override
      public void update() {
      }

      @Override
      public FileStatus getFileStatus() {
        return null;
      }

      @Override
      public boolean isValid() {
        return false;
      }

      @Override
      public void navigate(boolean focus) throws UnsupportedOperationException { }
      @Override
      public boolean canNavigate() { return false; }

      @Override
      public boolean canNavigateToSource() {
        return false;
      }

      @Override
      public int compareTo(@NotNull UsageGroup o) { return o == ODD ? -1 : 0; }
      public String toString() { return getText(null); }
    };

    private static final UsageGroup ODD = new UsageGroup() {
      @Override
      public Icon getIcon(boolean isOpen) { return null; }
      @Override
      @NotNull
      public String getText(UsageView view) { return "Odd"; }

      @Override
      public void update() {
      }

      @Override
      public FileStatus getFileStatus() {
        return null;
      }

      @Override
      public boolean isValid() {
        return false;
      }

      @Override
      public void navigate(boolean focus) throws UnsupportedOperationException { }
      @Override
      public boolean canNavigate() { return false; }

      @Override
      public boolean canNavigateToSource() {
        return false;
      }

      @Override
      public int compareTo(@NotNull UsageGroup o) { return o == EVEN ? 1 : 0; }
      @Override
      public String toString() { return getText(null); }
    };

    @Override
    public UsageGroup groupUsage(@NotNull Usage usage) {
      MockUsage mockUsage = (MockUsage)usage;

      if (mockUsage.getId() > 1000) return null;

      return mockUsage.getId() % 2 == 0 ? EVEN : ODD;
    }
  }

  private static class MockUsage implements Usage {
    private final int myId;

    MockUsage(int index) {
      myId = index;
    }

    public int getId() {
      return myId;
    }

    @Override
    @NotNull
    public UsagePresentation getPresentation() {
      return new UsagePresentation() {
        @Override
        @NotNull
        public TextChunk[] getText() {
          return TextChunk.EMPTY_ARRAY;
        }

        @Override
        @NotNull
        public String getPlainText() {
          return "";
        }

        @Override
        public Icon getIcon() {
          return null;
        }

        @Override
        public String getTooltipText() {
          return null;
        }
      };
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public FileEditorLocation getLocation() {
      return null;
    }

    @Override
    public void selectInEditor() {
    }

    @Override
    public void highlightInEditor() {
    }

    public String toString() {
      return String.valueOf(myId);
    }

    @Override
    public void navigate(boolean requestFocus) {
    }

    @Override
    public boolean canNavigate() {
      return false;
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public boolean isReadOnly() {
      return false;
    }
  }
}
