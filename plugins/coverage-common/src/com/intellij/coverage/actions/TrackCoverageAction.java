/*
 * User: anna
 * Date: 19-Nov-2007
 */
package com.intellij.coverage.actions;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageExecutor;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.ToggleModelAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.util.ArrayList;
import java.util.List;

public class TrackCoverageAction extends ToggleModelAction {
  private final TestConsoleProperties myProperties;
  private TestFrameworkRunningModel myModel;
  private TreeSelectionListener myTreeSelectionListener;

  public TrackCoverageAction(TestConsoleProperties properties) {
    super("Show coverage per test", "Show coverage per test", AllIcons.RunConfigurations.TrackCoverage, properties,
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

  @Override
  public boolean isSelected(AnActionEvent e) {
    return super.isSelected(e) && CoverageDataManager.getInstance(myProperties.getProject()).isSubCoverageActive();
  }

  private void restoreMergedCoverage() {
    final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(myProperties.getProject());
    if (coverageDataManager.isSubCoverageActive()) {
      final CoverageSuitesBundle currentSuite = coverageDataManager.getCurrentSuitesBundle();
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
    final CoverageSuitesBundle suite = getCurrentCoverageSuite();
    return suite != null && suite.isCoverageByTestApplicable() && suite.isCoverageByTestEnabled();
  }

  @Override
  protected boolean isVisible() {
    final CoverageSuitesBundle suite = getCurrentCoverageSuite();
    return suite != null && suite.isCoverageByTestApplicable();
  }

  @Nullable
  private CoverageSuitesBundle getCurrentCoverageSuite() {
    if (myModel == null) {
      return null;
    }

    final RunProfile runConf = myModel.getProperties().getConfiguration();
    if (runConf instanceof ModuleBasedConfiguration) {

      // if coverage supported for run configuration
      if (CoverageEnabledConfiguration.isApplicableTo((ModuleBasedConfiguration) runConf)) {

        // Get coverage settings
        Executor executor = myProperties.getExecutor();
        if (executor != null && executor.getId().equals(CoverageExecutor.EXECUTOR_ID)) {
          return CoverageDataManager.getInstance(myProperties.getProject()).getCurrentSuitesBundle();
        }
      }
    }
    return null;
  }

  private void selectSubCoverage() {
    final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(myProperties.getProject());
    final CoverageSuitesBundle currentSuite = coverageDataManager.getCurrentSuitesBundle();
    if (currentSuite != null) {
      final AbstractTestProxy test = myModel.getTreeView().getSelectedTest();
      List<String> testMethods = new ArrayList<>();
      if (test != null && !test.isInProgress()) {
        final List<? extends AbstractTestProxy> list = test.getAllTests();
        for (AbstractTestProxy proxy : list) {
          final Location location = proxy.getLocation(myProperties.getProject(), myProperties.getScope());
          if (location != null) {
            final PsiElement element = location.getPsiElement();
            final String name = currentSuite.getCoverageEngine().getTestMethodName(element, proxy);
            if (name != null) {
              testMethods.add(name);
            }
          }
        }
      }
      coverageDataManager.selectSubCoverage(currentSuite, testMethods);
    }
  }

  private class MyTreeSelectionListener implements TreeSelectionListener {
    private final Alarm myUpdateCoverageAlarm;

    public MyTreeSelectionListener() {
      myUpdateCoverageAlarm = new Alarm(myModel);
    }

    public void valueChanged(final TreeSelectionEvent e) {
      if (myUpdateCoverageAlarm.isDisposed()) return;
      if (!TestConsoleProperties.TRACK_CODE_COVERAGE.value(myModel.getProperties()) || !isEnabled()) return;
      myUpdateCoverageAlarm.cancelAllRequests();
      final Project project = myModel.getProperties().getProject();
      final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(project);
      final CoverageSuitesBundle currentSuite = coverageDataManager.getCurrentSuitesBundle();
      if (currentSuite != null) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          myUpdateCoverageAlarm.addRequest(() -> selectSubCoverage(), 300);
        } else {
          if (coverageDataManager.isSubCoverageActive()) coverageDataManager.restoreMergedCoverage(currentSuite);
        }
      }
    }
  }
}
