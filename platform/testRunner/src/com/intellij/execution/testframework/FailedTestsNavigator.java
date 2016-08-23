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
package com.intellij.execution.testframework;

import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

import java.util.ArrayList;
import java.util.List;

public class FailedTestsNavigator implements OccurenceNavigator {
  private static final String NEXT_NAME = ExecutionBundle.message("next.faled.test.action.name");
  private static final String PREVIOUS_NAME = ExecutionBundle.message("prev.faled.test.action.name");
  private TestFrameworkRunningModel myModel;

  public boolean hasNextOccurence() {
    return myModel != null && getNextOccurenceInfo().hasNextOccurence();
  }

  public boolean hasPreviousOccurence() {
    return myModel != null && getPreviousOccurenceInfo().hasNextOccurence();
  }

  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    final FailedTestInfo result = getNextOccurenceInfo();
    myModel.selectAndNotify(result.getDefect());
    return new OccurenceInfo(TestsUIUtil.getOpenFileDescriptor(result.myDefect, myModel), result.getDefectNumber(),
                             result.getDefectsCount());
  }

  public void setModel(final TestFrameworkRunningModel model) {
    myModel = model;
    Disposer.register(myModel, new Disposable() {
      public void dispose() {
        myModel = null;
      }
    });
  }

  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    final FailedTestInfo result = getPreviousOccurenceInfo();
    myModel.selectAndNotify(result.getDefect());
    return new OccurenceInfo(TestsUIUtil.getOpenFileDescriptor(result.myDefect, myModel), result.getDefectNumber(),
                             result.getDefectsCount());
  }

  public String getNextOccurenceActionName() {
    return NEXT_NAME;
  }

  public String getPreviousOccurenceActionName() {
    return PREVIOUS_NAME;
  }

  private FailedTestInfo getNextOccurenceInfo() {
    return new NextFailedTestInfo().execute();
  }

  private FailedTestInfo getPreviousOccurenceInfo() {
    return new PreviousFailedTestInfo().execute();
  }

  protected abstract class FailedTestInfo {
    private AbstractTestProxy myDefect = null;
    private List<AbstractTestProxy> myAllTests;
    private List<AbstractTestProxy> myDefects;

    public AbstractTestProxy getDefect() {
      return myDefect;
    }

    private int getDefectNumber() {
      return myDefect == null ? getDefectsCount() : myDefects.indexOf(myDefect) + 1;
    }

    public FailedTestInfo execute() {
      myAllTests = new ArrayList<>(myModel.getRoot().getAllTests());
      myDefects = Filter.DEFECTIVE_LEAF.select(myAllTests);
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
    protected int nextIndex(final int defectIndex) {
      return defectIndex + 1;
    }

    protected int getBoundIndex() {
      return getDefectsCount() - 1;
    }
  }

  private class PreviousFailedTestInfo extends FailedTestInfo {
    protected int nextIndex(final int defectIndex) {
      return defectIndex - 1;
    }

    protected int getBoundIndex() {
      return 0;
    }
  }
}
