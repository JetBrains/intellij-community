// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.actions;

import com.intellij.coverage.*;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.ToggleModelAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.util.ArrayList;
import java.util.List;

class TrackCoverageAction extends ToggleModelAction {
  private final TestConsoleProperties myProperties;
  private TestFrameworkRunningModel myModel;
  private TreeSelectionListener myTreeSelectionListener;
  private Alarm myUpdateCoverageAlarm;
  /**
   * State memorization via {@link TestConsoleProperties} is replaced with per-session property,
   * as this option affects coverage visible data a lot, while it is hard to notice when enabled accidentally.
   */
  private boolean myIsActive;

  TrackCoverageAction(TestConsoleProperties properties) {
    super(CoverageBundle.message("show.coverage.per.test.action.text"), CoverageBundle.message("show.coverage.per.test.action.description"),
          AllIcons.RunConfigurations.TrackCoverage, properties,
          TestConsoleProperties.TRACK_CODE_COVERAGE);
    myProperties = properties;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void setSelected(final @NotNull AnActionEvent e, final boolean state) {
    myIsActive = state;
    if (state) {
      selectSubCoverageAsync();
    }
    else {
      restoreMergedCoverage();
    }
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return myIsActive;
  }

  private void restoreMergedCoverage() {
    final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(myProperties.getProject());
    if (coverageDataManager.isSubCoverageActive()) {
      CoverageSuitesBundle currentSuite = getCurrentCoverageSuite();
      if (currentSuite != null) {
        coverageDataManager.restoreMergedCoverage(currentSuite);
      }
    }
  }

  @Override
  public void setModel(final TestFrameworkRunningModel model) {
    if (myModel != null) myModel.getTreeView().removeTreeSelectionListener(myTreeSelectionListener);
    myModel = model;
    if (model != null) {
      myTreeSelectionListener = new MyTreeSelectionListener();
      myUpdateCoverageAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myModel);
      model.getTreeView().addTreeSelectionListener(myTreeSelectionListener);
      Disposer.register(model, new Disposable() {
        @Override
        public void dispose() {
          restoreMergedCoverage();
        }
      });
    }
  }

  @Override
  protected boolean isEnabled() {
    final CoverageSuitesBundle suite = getCurrentCoverageSuite();
    return suite != null && suite.isCoverageByTestApplicable() && suite.isCoverageByTestEnabled();
  }

  @Override
  protected boolean isVisible() {
    final CoverageSuitesBundle suite = getCurrentCoverageSuite();
    return suite != null && suite.isCoverageByTestApplicable();
  }

  private @Nullable CoverageSuitesBundle getCurrentCoverageSuite() {
    if (myModel == null) {
      return null;
    }

    final RunProfile runConf = myModel.getProperties().getConfiguration();
    if (runConf instanceof RunConfigurationBase<?> base) {

      // if coverage supported for run configuration
      if (CoverageEnabledConfiguration.isApplicableTo(base)) {

        // Get coverage settings
        Executor executor = myProperties.getExecutor();
        if (executor != null && executor.getId().equals(CoverageExecutor.EXECUTOR_ID)) {
          CoverageSuite suite = CoverageEnabledConfiguration.getOrCreate(base).getCurrentCoverageSuite();
          return ContainerUtil.find(CoverageDataManager.getInstance(myProperties.getProject()).activeSuites(), b -> b.contains(suite));
        }
      }
    }
    return null;
  }

  private void selectSubCoverageAsync() {
    if (myUpdateCoverageAlarm == null || myUpdateCoverageAlarm.isDisposed()) return;
    myUpdateCoverageAlarm.cancelAllRequests();
    myUpdateCoverageAlarm.addRequest(() -> selectSubCoverage(), 300);
  }

  private void selectSubCoverage() {
    CoverageSuitesBundle currentSuite = getCurrentCoverageSuite();
    if (currentSuite != null) {
      final AbstractTestProxy test = myModel.getTreeView().getSelectedTest();
      List<String> testMethods = new ArrayList<>();
      if (test != null && !test.isInProgress()) {
        final List<? extends AbstractTestProxy> list = test.getAllTests();
        for (AbstractTestProxy proxy : list) {
          final Location<?> location = ReadAction.compute(() -> proxy.getLocation(myProperties.getProject(), myProperties.getScope()));
          if (location != null) {
            final PsiElement element = location.getPsiElement();
            final String name = ReadAction.compute(() -> currentSuite.getCoverageEngine().getTestMethodName(element, proxy));
            if (name != null) {
              testMethods.add(name);
            }
          }
        }
      }
      CoverageDataManager.getInstance(myProperties.getProject()).selectSubCoverage(currentSuite, testMethods);
    }
  }

  private class MyTreeSelectionListener implements TreeSelectionListener {

    @Override
    public void valueChanged(final TreeSelectionEvent e) {
      if (!myIsActive || !isEnabled()) return;
      selectSubCoverageAsync();
    }
  }
}
