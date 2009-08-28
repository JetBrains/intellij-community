/*
 * User: anna
 * Date: 19-Nov-2007
 */
package com.intellij.coverage.actions;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageDataManagerImpl;
import com.intellij.coverage.CoverageSuite;
import com.intellij.coverage.CoverageSuiteImpl;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.testframework.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Alarm;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.util.ArrayList;
import java.util.List;

public class TrackCoverageAction extends ToggleModelAction {
  private TestConsoleProperties myProperties;
  private TestFrameworkRunningModel myModel;
  private TreeSelectionListener myTreeSelectionListener;

  public TrackCoverageAction(TestConsoleProperties properties) {
    super("Show coverage per test", "Show coverage per test", TestsUIUtil.loadIcon("trackCoverage"), properties,
          TestConsoleProperties.TRACK_CODE_COVERAGE);
    myProperties = properties;

  }

  public void setSelected(final AnActionEvent e, final boolean state) {
    super.setSelected(e, state);
    if (!TestConsoleProperties.TRACK_CODE_COVERAGE.value(myProperties)) {
      restoreMergedCoverage();
    } else {
      selectSubCoverage();
    }
  }

  private void restoreMergedCoverage() {
    final CoverageDataManagerImpl coverageDataManager = (CoverageDataManagerImpl)CoverageDataManager.getInstance(myProperties.getProject());
    if (coverageDataManager.isSubCoverageActive()) {
      final CoverageSuite currentSuite = coverageDataManager.getCurrentSuite();
      if (currentSuite != null) {
        coverageDataManager.restoreMergedCoverage(currentSuite);
      }
    }
  }

  public void setModel(final TestFrameworkRunningModel model) {
    if (myModel != null) myModel.getTreeView().removeTreeSelectionListener(myTreeSelectionListener);
    myModel = model;
    if (model != null) {
      myTreeSelectionListener = new MyTreeSelectionListener();
      model.getTreeView().addTreeSelectionListener(myTreeSelectionListener);
      Disposer.register(model, new Disposable() {
        public void dispose() {
          restoreMergedCoverage();
        }
      });
    }
  }

  protected boolean isEnabled() {
    if (myModel == null) return false;
    final RunConfigurationBase configuration = myModel.getProperties().getConfiguration();
    final CoverageEnabledConfiguration coverageEnabledConfiguration = CoverageEnabledConfiguration.get((ModuleBasedConfiguration)configuration);
    if (!coverageEnabledConfiguration.isCoverageEnabled()) return false;
    final CoverageSuiteImpl suite = (CoverageSuiteImpl)CoverageDataManager.getInstance(myProperties.getProject()).getCurrentSuite();
    return suite != null && suite.isCoverageByTestApplicable() && suite.isCoverageByTestEnabled();
  }

  private void selectSubCoverage() {
    final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(myProperties.getProject());
    final CoverageSuite currentSuite = coverageDataManager.getCurrentSuite();
    final AbstractTestProxy test = myModel.getTreeView().getSelectedTest();
    List<String> testMethods = new ArrayList<String>();
    if (test != null) {
      final List<? extends AbstractTestProxy> list = test.getAllTests();
      for (AbstractTestProxy proxy : list) {
        final Location location = proxy.getLocation(myProperties.getProject());
        if (location != null) {
          final PsiElement element = location.getPsiElement();
          if (element instanceof PsiMethod) {
            final PsiMethod method = (PsiMethod)element;
            testMethods.add(method.getContainingClass().getQualifiedName() + "." + method.getName());
          }
        }
      }
    }
    coverageDataManager.selectSubCoverage(currentSuite, testMethods);
  }

  private class MyTreeSelectionListener implements TreeSelectionListener {
    private final Alarm myUpdateCoverageAlarm;

    public MyTreeSelectionListener() {
      myUpdateCoverageAlarm = new Alarm(myModel);
    }

    public void valueChanged(final TreeSelectionEvent e) {
      if (!TestConsoleProperties.TRACK_CODE_COVERAGE.value(myModel.getProperties()) || !isEnabled()) return;
      myUpdateCoverageAlarm.cancelAllRequests();
      final Project project = myModel.getProperties().getProject();
      final CoverageDataManagerImpl coverageDataManager = (CoverageDataManagerImpl)CoverageDataManager.getInstance(project);
      final CoverageSuite currentSuite = coverageDataManager.getCurrentSuite();
      if (currentSuite != null) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          myUpdateCoverageAlarm.addRequest(new Runnable() {
            public void run() {
              selectSubCoverage();
            }
          }, 300);
        } else {
          if (coverageDataManager.isSubCoverageActive()) coverageDataManager.restoreMergedCoverage(currentSuite);
        }
      }
    }
  }
}