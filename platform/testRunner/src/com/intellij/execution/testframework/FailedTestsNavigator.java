// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.testframework.ui.BaseTestProxyNodeDescriptor;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.List;

class FailedTestsNavigator implements OccurenceNavigator {
  private TestFrameworkRunningModel myModel;

  @Override
  public boolean hasNextOccurence() {
    return myModel != null && getNextOccurenceInfo().hasNextOccurence();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return myModel != null && getPreviousOccurenceInfo().hasNextOccurence();
  }

  @Override
  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    final FailedTestInfo result = getNextOccurenceInfo();
    myModel.selectAndNotify(result.getDefect());
    return new OccurenceInfo(TestsUIUtil.getOpenFileDescriptor(result.myDefect, myModel), result.getDefectNumber(),
                             result.getDefectsCount());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  public void setModel(final TestFrameworkRunningModel model) {
    myModel = model;
    Disposer.register(myModel, new Disposable() {
      @Override
      public void dispose() {
        myModel = null;
      }
    });
  }

  @Override
  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    final FailedTestInfo result = getPreviousOccurenceInfo();
    myModel.selectAndNotify(result.getDefect());
    return new OccurenceInfo(TestsUIUtil.getOpenFileDescriptor(result.myDefect, myModel), result.getDefectNumber(),
                             result.getDefectsCount());
  }

  @Override
  public @NotNull String getNextOccurenceActionName() {
    return getNextName();
  }

  @Override
  public @NotNull String getPreviousOccurenceActionName() {
    return getPreviousName();
  }

  private FailedTestInfo getNextOccurenceInfo() {
    return new NextFailedTestInfo().execute();
  }

  private FailedTestInfo getPreviousOccurenceInfo() {
    return new PreviousFailedTestInfo().execute();
  }

  private abstract class FailedTestInfo {
    private AbstractTestProxy myDefect = null;
    private List<AbstractTestProxy> myAllTests;
    private List<AbstractTestProxy> myDefects;

    public AbstractTestProxy getDefect() {
      return myDefect;
    }

    private int getDefectNumber() {
      return myDefect == null ? getDefectsCount() : myDefects.indexOf(myDefect) + 1;
    }

    FailedTestInfo execute() {
      myAllTests = new ArrayList<>();
      collectTests(myAllTests, (TreeNode)myModel.getTreeView().getModel().getRoot());
      myDefects = Filter.DEFECTIVE_LEAF.select(myAllTests);
      if (myDefects.isEmpty()) {
        return this;
      }
      final AbstractTestProxy selectedTest = myModel.getTreeView().getSelectedTest();
      final int selectionIndex = myAllTests.indexOf(selectedTest);
      if (selectionIndex == -1)
        return this;
      final AbstractTestProxy defect = findNextDefect(selectionIndex);
      if (defect == null)
        return this;
      if (defect != selectedTest) {
        myDefect = defect;
        return this;
      }
      final int defectIndex = myDefects.indexOf(defect);
      if (defectIndex == -1 || defectIndex == getBoundIndex())
        return this;
      myDefect = myDefects.get(nextIndex(defectIndex));
      return this;
    }

    private static void collectTests(List<? super AbstractTestProxy> tests, TreeNode node) {
      if (node == null) return;
      Object elementFor = TreeUtil.getUserObject(node);
      if (elementFor instanceof BaseTestProxyNodeDescriptor) {
        elementFor = ((BaseTestProxyNodeDescriptor<?>)elementFor).getElement();
      }
      if (elementFor instanceof AbstractTestProxy) tests.add((AbstractTestProxy)elementFor);
      for(int i = 0; i < node.getChildCount(); i++) {
        collectTests(tests, node.getChildAt(i));
      }
    }


    private AbstractTestProxy findNextDefect(final int startIndex) {
      for (int i = nextIndex(startIndex); 0 <= i && i < myAllTests.size(); i = nextIndex(i)) {
        final AbstractTestProxy nextDefect = myAllTests.get(i);
        if (Filter.DEFECTIVE_LEAF.shouldAccept(nextDefect))
          return nextDefect;
      }
      return null;
    }

    protected abstract int nextIndex(int defectIndex);

    protected abstract int getBoundIndex();

    protected int getDefectsCount() {
      return myDefects.size();
    }

    private boolean hasNextOccurence() {
      return myDefect != null;
    }
  }

  private class NextFailedTestInfo extends FailedTestInfo {
    @Override
    protected int nextIndex(final int defectIndex) {
      return defectIndex + 1;
    }

    @Override
    protected int getBoundIndex() {
      return getDefectsCount() - 1;
    }
  }

  private class PreviousFailedTestInfo extends FailedTestInfo {
    @Override
    protected int nextIndex(final int defectIndex) {
      return defectIndex - 1;
    }

    @Override
    protected int getBoundIndex() {
      return 0;
    }
  }

  static @NlsActions.ActionText String getNextName() {
    return ExecutionBundle.message("next.failed.test.action.name");
  }

  static @NlsActions.ActionText String getPreviousName() {
    return ExecutionBundle.message("prev.failed.test.action.name");
  }
}
