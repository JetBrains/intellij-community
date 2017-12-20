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

package com.intellij.execution.testframework.actions;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.diff.impl.DiffWindowBase;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.TestTreeViewAction;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ViewAssertEqualsDiffAction extends AnAction implements TestTreeViewAction, DumbAware {
  @NonNls public static final String ACTION_ID = "openAssertEqualsDiff";

  public void actionPerformed(final AnActionEvent e) {
    if (!e.getPresentation().isVisible()) {
      return;
    }
    if (!openDiff(e.getDataContext(), null)) {
      final Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
      Messages.showInfoMessage(component, "Comparison error was not found", "No Comparison Data Found");
    }
  }

  public static boolean openDiff(DataContext context, @Nullable DiffHyperlink currentHyperlink) {
    final AbstractTestProxy testProxy = AbstractTestProxy.DATA_KEY.getData(context);
    final Project project = CommonDataKeys.PROJECT.getData(context);
    if (testProxy != null) {
      DiffHyperlink diffViewerProvider = testProxy.getDiffViewerProvider();
      if (diffViewerProvider != null) {
        final List<DiffHyperlink> providers = collectAvailableProviders(TestTreeView.MODEL_DATA_KEY.getData(context));
        int index = currentHyperlink != null ? providers.indexOf(currentHyperlink) : -1;
        if (index == -1) index = providers.indexOf(diffViewerProvider);
        new MyDiffWindow(project, providers, Math.max(0, index)).show();
        return true;
      }
    }
    if (currentHyperlink != null) {
      new MyDiffWindow(project, currentHyperlink).show();
      return true;
    }
    return false;
  }

  private static List<DiffHyperlink> collectAvailableProviders(TestFrameworkRunningModel model) {
    final List<DiffHyperlink> providers = new ArrayList<>();
    if (model != null) {
      final AbstractTestProxy root = model.getRoot();
      final List<? extends AbstractTestProxy> allTests = root.getAllTests();
      for (AbstractTestProxy test : allTests) {
        if (test.isLeaf()) {
          providers.addAll(test.getDiffViewerProviders());
        }
      }
    }
    return providers;
  }

  public void update(final AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (e.getProject() == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    AbstractTestProxy test = AbstractTestProxy.DATA_KEY.getData(e.getDataContext());
    boolean visible = test != null && test.getDiffViewerProvider() != null;

    presentation.setEnabled(test != null);
    presentation.setVisible(visible);
  }

  private static class MyDiffWindow extends DiffWindowBase {
    @NotNull private final List<DiffHyperlink> myRequests;
    private final int myIndex;

    public MyDiffWindow(@Nullable Project project, @NotNull DiffHyperlink request) {
      this(project, Collections.singletonList(request), 0);
    }

    public MyDiffWindow(@Nullable Project project, @NotNull List<DiffHyperlink> requests, int index) {
      super(project, DiffDialogHints.DEFAULT);
      myRequests = requests;
      myIndex = index;
    }

    @NotNull
    @Override
    protected DiffRequestProcessor createProcessor() {
      return new MyTestDiffRequestProcessor(myProject, myRequests, myIndex);
    }

    private class MyTestDiffRequestProcessor extends TestDiffRequestProcessor {
      public MyTestDiffRequestProcessor(@Nullable Project project, @NotNull List<DiffHyperlink> requests, int index) {
        super(project, requests, index);
        putContextUserData(DiffUserDataKeys.DIALOG_GROUP_KEY, "#com.intellij.execution.junit2.states.ComparisonFailureState$DiffDialog");
      }

      @Override
      protected void setWindowTitle(@NotNull String title) {
        getWrapper().setTitle(title);
      }

      @Override
      protected void onAfterNavigate() {
        DiffUtil.closeWindow(getWrapper().getWindow(), true, true);
      }
    }
  }
}
