// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.testframework;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.testframework.actions.ScrollToTestSourceAction;
import com.intellij.execution.testframework.actions.TestFrameworkActions;
import com.intellij.execution.testframework.actions.TestTreeExpander;
import com.intellij.execution.testframework.autotest.AdjustAutotestDelayActionGroup;
import com.intellij.execution.testframework.export.ExportTestResultsAction;
import com.intellij.execution.testframework.ui.AbstractTestTreeBuilderBase;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.config.DumbAwareToggleBooleanProperty;
import com.intellij.util.config.DumbAwareToggleInvertedBooleanProperty;
import com.intellij.util.config.ToggleBooleanProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class ToolbarPanel extends JPanel implements OccurenceNavigator, Disposable {
  private static final Logger LOG = Logger.getInstance(ToolbarPanel.class);
  protected final TestTreeExpander myTreeExpander = new TestTreeExpander();
  protected final FailedTestsNavigator myOccurenceNavigator;
  protected final ScrollToTestSourceAction myScrollToSource;
  private @Nullable ExportTestResultsAction myExportAction;

  private final ArrayList<ToggleModelAction> myActions = new ArrayList<>();

  public ToolbarPanel(final TestConsoleProperties properties,
                      final JComponent parent) {
    super(new BorderLayout());
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.addAction(new DumbAwareToggleInvertedBooleanProperty(ExecutionBundle.message("junit.run.hide.passed.action.name"), ExecutionBundle.message("junit.run.hide.passed.action.description"),
                                                                     AllIcons.RunConfigurations.ShowPassed,
                                                                     properties, TestConsoleProperties.HIDE_PASSED_TESTS));
    actionGroup.add(new DumbAwareToggleInvertedBooleanProperty(TestRunnerBundle.message("action.show.ignored.text"),
                                                               TestRunnerBundle.message("action.show.ignored.description"), AllIcons.RunConfigurations.ShowIgnored,
                                                               properties, TestConsoleProperties.HIDE_IGNORED_TEST));
    actionGroup.addSeparator();



    actionGroup.addAction(new DumbAwareToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.sort.alphabetically.action.name"),
                                                             ExecutionBundle.message("junit.runing.info.sort.alphabetically.action.description"),
                                                             AllIcons.ObjectBrowser.Sorted,
                                                             properties, TestConsoleProperties.SORT_ALPHABETICALLY));
    final ToggleModelAction sortByStatistics = new SortByDurationAction(properties);
    myActions.add(sortByStatistics);
    actionGroup.addAction(sortByStatistics);
    actionGroup.addSeparator();

    AnAction action = CommonActionsManager.getInstance().createExpandAllAction(myTreeExpander, parent);
    action.getTemplatePresentation().setDescription(ExecutionBundle.messagePointer("junit.runing.info.expand.test.action.name"));
    actionGroup.add(action);

    action = CommonActionsManager.getInstance().createCollapseAllAction(myTreeExpander, parent);
    action.getTemplatePresentation().setDescription(ExecutionBundle.messagePointer("junit.runing.info.collapse.test.action.name"));
    actionGroup.add(action);

    actionGroup.addSeparator();
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    myOccurenceNavigator = new FailedTestsNavigator();
    actionGroup.add(actionsManager.createPrevOccurenceAction(myOccurenceNavigator));
    actionGroup.add(actionsManager.createNextOccurenceAction(myOccurenceNavigator));

    for (ToggleModelActionProvider actionProvider : ToggleModelActionProvider.EP_NAME.getExtensionList()) {
      final ToggleModelAction toggleModelAction = actionProvider.createToggleModelAction(properties);
      myActions.add(toggleModelAction);
      actionGroup.add(toggleModelAction);
    }

    final AnAction[] importActions = properties.createImportActions();
    if (importActions != null) {
      actionGroup.addAll(importActions);
    }

    final RunProfile configuration = properties.getConfiguration();
    if (configuration instanceof RunConfiguration) {
      myExportAction = ExportTestResultsAction.create(properties.getExecutor().getToolWindowId(), (RunConfiguration)configuration, parent);
      actionGroup.addAction(myExportAction);
    }

    final DefaultActionGroup secondaryGroup = new DefaultActionGroup();
    secondaryGroup.setPopup(true);
    secondaryGroup.getTemplatePresentation().setIcon(AllIcons.General.GearPlain);
    secondaryGroup.add(new DumbAwareToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.track.test.action.name"),
                                                 ExecutionBundle.message("junit.runing.info.track.test.action.description"),
                                                 null, properties, TestConsoleProperties.TRACK_RUNNING_TEST));
    secondaryGroup.add(new DumbAwareToggleBooleanProperty(TestRunnerBundle.message("action.show.inline.statistics.text"), TestRunnerBundle
      .message("action.toggle.visibility.test.duration.in.tree.description"),
                                                          null, properties, TestConsoleProperties.SHOW_INLINE_STATISTICS));

    secondaryGroup.addSeparator();
    secondaryGroup.add(new DumbAwareToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.scroll.to.stacktrace.action.name"),
                                                 ExecutionBundle.message("junit.runing.info.scroll.to.stacktrace.action.description"),
                                                 null, properties, TestConsoleProperties.SCROLL_TO_STACK_TRACE));
    secondaryGroup.add(new ToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.open.source.at.exception.action.name"),
                                                 ExecutionBundle.message("junit.runing.info.open.source.at.exception.action.description"),
                                                 null, properties, TestConsoleProperties.OPEN_FAILURE_LINE));
    myScrollToSource = new ScrollToTestSourceAction(properties);
    secondaryGroup.add(myScrollToSource);

    secondaryGroup.add(new AdjustAutotestDelayActionGroup(parent));
    secondaryGroup.addSeparator();
    secondaryGroup.add(new DumbAwareToggleBooleanProperty(ExecutionBundle.message("junit.runing.info.select.first.failed.action.name"),
                                                 null, null, properties, TestConsoleProperties.SELECT_FIRST_DEFECT));
    properties.appendAdditionalActions(secondaryGroup, parent, properties);
    actionGroup.add(secondaryGroup);

    add(ActionManager.getInstance().
      createActionToolbar(ActionPlaces.TESTTREE_VIEW_TOOLBAR, actionGroup, true).
      getComponent(), BorderLayout.CENTER);
  }

  public void setModel(final TestFrameworkRunningModel model) {
    TestFrameworkActions.installFilterAction(model);
    myScrollToSource.setModel(model);
    myTreeExpander.setModel(model);
    myOccurenceNavigator.setModel(model);
    if (myExportAction != null) {
      myExportAction.setModel(model);
    }
    for (ToggleModelAction action : myActions) {
      action.setModel(model);
    }
    TestFrameworkActions.addPropertyListener(TestConsoleProperties.SORT_ALPHABETICALLY, createComparatorPropertyListener(model), model, true);
    TestFrameworkActions.addPropertyListener(TestConsoleProperties.SORT_BY_DURATION, createComparatorPropertyListener(model), model, true);
  }

  private static TestFrameworkPropertyListener<Boolean> createComparatorPropertyListener(TestFrameworkRunningModel model) {
    return new TestFrameworkPropertyListener<>() {
      @Override
      public void onChanged(Boolean value) {
        try {
          //todo reflection to avoid binary incompatibility with substeps plugin
          final AbstractTestTreeBuilderBase builder =
            (AbstractTestTreeBuilderBase)model.getClass().getMethod("getTreeBuilder").invoke(model);
          if (builder != null) {
            builder.setTestsComparator(model);
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    };
  }

  @Override
  public boolean hasNextOccurence() {
    return myOccurenceNavigator.hasNextOccurence();
  }

  @Override
  public boolean hasPreviousOccurence() {
    return myOccurenceNavigator.hasPreviousOccurence();
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    return myOccurenceNavigator.goNextOccurence();
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    return myOccurenceNavigator.goPreviousOccurence();
  }

  @NotNull
  @Override
  public String getNextOccurenceActionName() {
    return myOccurenceNavigator.getNextOccurenceActionName();
  }

  @NotNull
  @Override
  public String getPreviousOccurenceActionName() {
    return myOccurenceNavigator.getPreviousOccurenceActionName();
  }

  @Override
  public void dispose() {
    myScrollToSource.setModel(null);
    if (myExportAction != null) {
      myExportAction.setModel(null);
    }
  }

  private static class SortByDurationAction extends ToggleModelAction implements DumbAware {

    private TestFrameworkRunningModel myModel;

    SortByDurationAction(TestConsoleProperties properties) {
      super(ExecutionBundle.message("junit.runing.info.sort.by.statistics.action.name"),
            ExecutionBundle.message("junit.runing.info.sort.by.statistics.action.description"),
            AllIcons.RunConfigurations.SortbyDuration, properties,
            TestConsoleProperties.SORT_BY_DURATION);
    }

    @Override
    protected boolean isEnabled() {
      final TestFrameworkRunningModel model = myModel;
      return model != null && !model.isRunning();
    }

    @Override
    public void setModel(TestFrameworkRunningModel model) {
      myModel = model;
    }
  }
}
