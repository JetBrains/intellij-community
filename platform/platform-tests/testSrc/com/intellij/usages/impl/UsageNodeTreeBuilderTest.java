// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.usages.impl.rules.FileGroupingRule;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class UsageNodeTreeBuilderTest extends LightPlatformTestCase {
  public void testNoGroupingRules() {
    GroupNode groupNode = buildUsageTree(new int[]{2, 3, 0}, UsageGroupingRule.EMPTY_ARRAY);

    assertNotNull(groupNode);

    assertNull(groupNode.getParent());

    assertEquals("Root [0, 2, 3]", groupNode.toString());
  }

  public void testOneGroupingRuleOnly() {
    GroupNode groupNode = buildUsageTree(new int[]{0, 1, 0, 1 , 1}, new UsageGroupingRule[] {new OddEvenGroupingRule()});
    assertEquals("Root [Even[0, 0], Odd[1, 1, 1]]", groupNode.toString());
  }

  public void testNotGroupedItemsComeToEnd() {
    GroupNode groupNode = buildUsageTree(new int[]{0, 1, 0, 1 , 1, 1003, 1002, 1001}, new UsageGroupingRule[] {new OddEvenGroupingRule()});
    assertEquals("Root [Even[0, 0], Odd[1, 1, 1], 1001, 1002, 1003]", groupNode.toString());
  }

  public void test2Groupings() {
    GroupNode groupNode = buildUsageTree(new int[]{0, 1, 2, 3, 12, 13, 14, 15, 101, 103, 102, 105, 10001, 10002, 10003}, new UsageGroupingRule[] {
      new OddEvenGroupingRule(),
      new LogGroupingRule()});

    assertEquals("Root [Even[1[0, 2], 2[12, 14], 3[102]], Odd[1[1, 3], 2[13, 15], 3[101, 103, 105]], 5[10001, 10002, 10003]]", groupNode.toString());
  }

  public void testDifferentRulesDontDependOnOrder() {
    GroupNode groupNode = buildUsageTree(new int[]{10003, 0}, new UsageGroupingRule[] {
      new OddEvenGroupingRule(),
      new LogGroupingRule()});

    assertEquals("Root [Even[1[0]], 5[10003]]", groupNode.toString());
  }

  public void testGroupsFromDifferentRulesAreCorrectlySorted() {
    GroupNode groupNode = buildUsageTree(new int[]{10003, 0, 1, 2, 3, 12, 13, 14, 15, 101, 103, 102, 105, 10001, 10002}, new UsageGroupingRule[] {
      new OddEvenGroupingRule(),
      new LogGroupingRule()});

    assertEquals("Root [Even[1[0, 2], 2[12, 14], 3[102]], Odd[1[1, 3], 2[13, 15], 3[101, 103, 105]], 5[10001, 10002, 10003]]", groupNode.toString());
  }

  private static Usage createUsage(int index) {
    return new MockUsage(index);
  }

  private GroupNode buildUsageTree(int[] indices, UsageGroupingRule[] rules) {
    List<Usage> usages = IntStream.of(indices).mapToObj(UsageNodeTreeBuilderTest::createUsage).collect(Collectors.toList());

    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setUsagesString("searching for mock usages");

    ExtensionPoint<UsageGroupingRuleProvider> point = UsageGroupingRuleProvider.EP_NAME.getPoint();
    UsageGroupingRuleProvider provider = new UsageGroupingRuleProvider() {
      @Override
      public @NotNull UsageGroupingRule @NotNull [] getActiveRules(@NotNull Project project) {
        return rules;
      }
    };

    Disposable disposable = Disposer.newDisposable();
    point.registerExtension(provider, disposable);
    try {
      UsageViewImpl usageView = new UsageViewImpl(getProject(), presentation, UsageTarget.EMPTY_ARRAY, null);
      Disposer.register(getTestRootDisposable(), usageView);
      usageView.appendUsagesInBulk(usages);
      UIUtil.dispatchAllInvocationEvents();
      ProgressManager.getInstance().run(new Task.Modal(getProject(), "Waiting", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          usageView.waitForUpdateRequestsCompletion();
        }
      });
      UIUtil.dispatchAllInvocationEvents();

      return usageView.getRoot();
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  private static class LogGroupingRule extends SingleParentUsageGroupingRule {
    @Nullable
    @Override
    protected UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
      return new LogUsageGroup(usage.toString().length());
    }
  }

  private static class LogUsageGroup implements UsageGroup {
    private final int myPower;

    LogUsageGroup(int power) {
      myPower = power;
    }

    @Override
    @NotNull
    public String getPresentableGroupText() { return String.valueOf(myPower); }

    public String toString() {
      return getPresentableGroupText();
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

  private static class OddEvenGroupingRule extends SingleParentUsageGroupingRule {
    private static final UsageGroup EVEN = new UsageGroup() {

      @Override
      @NotNull
      public String getPresentableGroupText() { return "Even"; }

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
      public String toString() { return getPresentableGroupText(); }
    };

    private static final UsageGroup ODD = new UsageGroup() {

      @Override
      @NotNull
      public String getPresentableGroupText() { return "Odd"; }

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
      public String toString() { return getPresentableGroupText(); }
    };

    @Nullable
    @Override
    protected UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
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
        public TextChunk @NotNull [] getText() {
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

  public void testFilesWithTheSameNameButDifferentPathsEndUpInDifferentGroups() throws IOException {
    File ioDir = FileUtil.createTempDirectory("t", null, false);
    VirtualFile dir = null;
    try {
      dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioDir);
      PsiFile f1 = getPsiManager().findFile(VfsTestUtil.createFile(dir, "/x/X.java", "class X{}"));
      PsiFile f2 = getPsiManager().findFile(VfsTestUtil.createFile(dir, "/y/X.java", "class X{}"));
      PsiElement class1 = ArrayUtil.getLastElement(f1.getChildren());
      PsiElement class2 = ArrayUtil.getLastElement(f2.getChildren());
      FileGroupingRule fileGroupingRule = new FileGroupingRule(getProject());
      UsageGroup group1 = fileGroupingRule.getParentGroupFor(new UsageInfo2UsageAdapter(new UsageInfo(class1)), UsageTarget.EMPTY_ARRAY);
      UsageGroup group2 = fileGroupingRule.getParentGroupFor(new UsageInfo2UsageAdapter(new UsageInfo(class2)), UsageTarget.EMPTY_ARRAY);
      int compareTo = group1.compareTo(group2);
      assertTrue(String.valueOf(compareTo), compareTo < 0);
    }
    finally {
      if (dir != null) {
        VfsTestUtil.deleteFile(dir);
      }
      FileUtil.delete(ioDir);
    }
  }
}
