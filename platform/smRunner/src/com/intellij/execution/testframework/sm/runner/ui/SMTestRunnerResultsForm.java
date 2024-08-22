// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.actions.ScrollToTestSourceAction;
import com.intellij.execution.testframework.export.TestResultsXmlFormatter;
import com.intellij.execution.testframework.sm.SMStacktraceParser;
import com.intellij.execution.testframework.sm.SmRunnerBundle;
import com.intellij.execution.testframework.sm.TestHistoryConfiguration;
import com.intellij.execution.testframework.sm.runner.*;
import com.intellij.execution.testframework.sm.runner.history.ImportedTestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.history.LocalHistory;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

/**
 * @author Roman Chernyatchik
 */
public class SMTestRunnerResultsForm extends TestResultsPanel
  implements TestFrameworkRunningModel, TestResultsViewer, SMTRunnerEventsListener {
  @NonNls public static final String HISTORY_DATE_FORMAT = "yyyy.MM.dd 'at' HH'h' mm'm' ss's'";
  @NonNls private static final String DEFAULT_SM_RUNNER_SPLITTER_PROPERTY = "SMTestRunner.Splitter.Proportion";

  private static final Logger LOG = Logger.getInstance(SMTestRunnerResultsForm.class);
  private static final Color RED = new JBColor(new Color(250, 220, 220), new Color(104, 67, 67));
  private static final Color GREEN = new JBColor(new Color(220, 250, 220), new Color(44, 66, 60));

  private SMTRunnerTestTreeView myTreeView;

  /**
   * Fake parent suite for all tests and suites
   */
  private final SMTestProxy.SMRootTestProxy myTestsRootNode;
  private SMTRunnerTreeBuilder myTreeBuilder;

  private final List<EventsListener> myEventListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final Project myProject;
  private final ConsoleView myConsoleView;

  private int myTotalTestCount = 0;
  private int myStartedTestCount = 0;
  private int myFinishedTestCount = 0;
  private int myFailedTestCount = 0;
  private int myIgnoredTestCount = 0;
  private long myStartTime;
  private long myEndTime;

  // custom progress
  private String myCurrentCustomProgressCategory;
  private final Set<String> myMentionedCategories = new LinkedHashSet<>();
  private volatile boolean myTestsRunning = true;
  private volatile AbstractTestProxy myLastSelected;
  private volatile boolean myDisposed = false;
  private SMTestProxy myLastFailed;
  private final Set<Update> myRequests = Collections.synchronizedSet(new HashSet<>());
  private final Alarm myUpdateTreeRequests = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

  private final String myHistoryFileName;

  @ApiStatus.Internal
  public SMTestRunnerResultsForm(@NotNull ConsoleView consoleView,
                                 @NotNull TestConsoleProperties consoleProperties,
                                 @Nullable String splitterPropertyName) {
    super(consoleView.getComponent(), consoleView.createConsoleActions(), consoleProperties,
          StringUtil.notNullize(splitterPropertyName, DEFAULT_SM_RUNNER_SPLITTER_PROPERTY), 0.2f);
    myProject = consoleProperties.getProject();
    myConsoleView = consoleView;

    //Create tests common suite root
    myTestsRootNode = new SMTestProxy.SMRootTestProxy(consoleProperties.isPreservePresentableName(), consoleView.getComponent());
    myTestsRootNode.setTestConsoleProperties(consoleProperties);
    //todo myTestsRootNode.setOutputFilePath(runConfiguration.getOutputFilePath());

    // Fire selection changed and move focus on SHIFT+ENTER
    //TODO[romeo] improve
    /*
    final ArrayList<Component> components = new ArrayList<Component>();
    components.add(myTreeView);
    components.add(myTabs.getComponent());
    myContentPane.setFocusTraversalPolicy(new MyFocusTraversalPolicy(components));
    myContentPane.setFocusCycleRoot(true);
    */
    myHistoryFileName = PathUtil.suggestFileName(consoleProperties.getConfiguration().getName()) + " - " +
                        new SimpleDateFormat(HISTORY_DATE_FORMAT).format(new Date());
  }

  @ApiStatus.Internal
  @Override
  protected ToolbarPanel createToolbarPanel() {
    ToolbarPanel toolbarPanel = new ToolbarPanel(myProperties, this);
    toolbarPanel.setModel(this);
    return toolbarPanel;
  }

  @ApiStatus.Internal
  @Override
  protected JComponent createTestTreeView() {
    myTreeView = new SMTRunnerTestTreeView();

    myTreeView.setLargeModel(true);
    myTreeView.attachToModel(this);
    myTreeView.setTestResultsViewer(this);
    final SMTRunnerTreeStructure structure = new SMTRunnerTreeStructure(myProject, myTestsRootNode);
    myTreeBuilder = new SMTRunnerTreeBuilder(myTreeView, structure);
    StructureTreeModel structureTreeModel = new StructureTreeModel<>(structure, IndexComparator.getInstance(), myProject);
    AsyncTreeModel asyncTreeModel = new AsyncTreeModel(structureTreeModel, true, myProject);
    myTreeView.setModel(asyncTreeModel);
    myTreeBuilder.setModel(structureTreeModel);
    myTreeBuilder.setTestsComparator(this);
    Disposer.register(this, myTreeBuilder);
    Disposer.register(this, asyncTreeModel);

    TrackRunningTestUtil.installStopListeners(myTreeView, myProperties, testProxy -> {
      if (testProxy == null) return;
      setLastSelected(testProxy);

      //ensure scroll to source on explicit selection only
      if (ScrollToTestSourceAction.isScrollEnabled(this)) {
        ReadAction
          .nonBlocking(() -> TestsUIUtil.getOpenFileDescriptor(testProxy, this))
          .finishOnUiThread(ModalityState.nonModal(), descriptor -> {
            if (descriptor != null) {
              OpenSourceUtil.navigate(false, descriptor);
            }
          })
          .expireWith(this)
          .submit(AppExecutorUtil.getAppExecutorService());
      }
    });

    //TODO always hide root node
    //myTreeView.setRootVisible(false);
    return myTreeView;
  }

  public void addTestsTreeSelectionListener(final TreeSelectionListener listener) {
    myTreeView.getSelectionModel().addTreeSelectionListener(listener);
  }

  /**
   * Returns root node, fake parent suite for all tests and suites
   *
   */
  @ApiStatus.Internal
  @Override
  public void onTestingStarted(@NotNull SMTestProxy.SMRootTestProxy testsRoot) {
    myTotalTestCount = 0;
    myStartedTestCount = 0;
    myFinishedTestCount = 0;
    myFailedTestCount = 0;
    myIgnoredTestCount = 0;
    myTestsRunning = true;
    myLastFailed = null;
    setLastSelected(null);
    myMentionedCategories.clear();

    if (myEndTime != 0) { // no need to reset when running for the first time
      resetTreeAndConsoleOnSubsequentTestingStarted();
      myEndTime = 0;
    }
    myTreeBuilder.updateFromRoot();

    // Status line
    myStatusLine.setStatus(JBUI.CurrentTheme.ProgressBar.passedStatusValue());

    // Tests tree
    selectAndNotify(myTestsRootNode);

    myStartTime = System.currentTimeMillis();
    boolean printTestingStartedTime = true;
    if (myProperties instanceof SMTRunnerConsoleProperties) {
      printTestingStartedTime = ((SMTRunnerConsoleProperties)myProperties).isPrintTestingStartedTime();
    }
    if (printTestingStartedTime) {
      myTestsRootNode.addSystemOutput("Testing started at " + DateFormatUtil.formatTime(myStartTime) + " ...\n");
    }

    // update status text
    updateStatusLabel(false);
    myStatusLine.setIndeterminate(isUndefined());

    fireOnTestingStarted();
  }

  private void resetTreeAndConsoleOnSubsequentTestingStarted() {
    myTestsRootNode.testingRestarted();
    myConsoleView.clear();
    ProcessHandler handler = myTestsRootNode.getHandler();
    if (handler instanceof BaseOSProcessHandler) {
      handler.notifyTextAvailable(((BaseOSProcessHandler)handler).getCommandLineForLog() + "\n", ProcessOutputTypes.SYSTEM);
    }
  }

  @ApiStatus.Internal
  @Override
  public void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy testsRoot) {
    myEndTime = System.currentTimeMillis();

    if (myTotalTestCount == 0) {
      myTotalTestCount = myStartedTestCount;
      myStatusLine.setFraction(1);
    }

    updateStatusLabel(true);
    updateIconProgress(true);

    myRequests.clear();
    myUpdateTreeRequests.cancelAllRequests();
    myTreeBuilder.updateFromRoot();

    addLabel(this);

    if (myLastSelected == null) {
      selectAndNotify(myTestsRootNode);
    }

    myTestsRunning = false;
    if (TestConsoleProperties.SORT_BY_DURATION.value(myProperties)) {
      myTreeBuilder.setTestsComparator(this);
    }

    fireOnTestingFinished();

    if (testsRoot.isEmptySuite() &&
        testsRoot.isTestsReporterAttached() &&
        myProperties instanceof SMTRunnerConsoleProperties &&
        ((SMTRunnerConsoleProperties)myProperties).fixEmptySuite()) {
      return;
    }
    final TestsUIUtil.TestResultPresentation presentation = new TestsUIUtil.TestResultPresentation(testsRoot, myStartTime > 0, null)
      .getPresentation(myFailedTestCount,
                       Math.max(0, myFinishedTestCount - myFailedTestCount - myIgnoredTestCount),
                       myTotalTestCount - myStartedTestCount,
                       myIgnoredTestCount);
    UIUtil.invokeLaterIfNeeded(() -> {
      WriteIntentReadAction.run((Runnable)() -> {
        TestsUIUtil.notifyByBalloon(myProperties.getProject(), testsRoot, myProperties, presentation);
        addToHistory(testsRoot, myProperties, this);
      });
    });

  }

  private void addToHistory(final SMTestProxy.SMRootTestProxy root,
                            TestConsoleProperties consoleProperties,
                            Disposable parentDisposable) {
    final RunProfile configuration = consoleProperties.getConfiguration();
    if (configuration instanceof RunConfiguration &&
        !(consoleProperties instanceof ImportedTestConsoleProperties) &&
        !isDisposed()) {
      final MySaveHistoryTask backgroundable =
        new MySaveHistoryTask(consoleProperties, root, (RunConfiguration)configuration, myHistoryFileName);
      Disposer.register(parentDisposable, new Disposable() {
        @Override
        public void dispose() {
          backgroundable.dispose();
        }
      });
      ProgressManager.getInstance().run(backgroundable);
    }
  }

  @ApiStatus.Internal
  @Override
  public void onTestsCountInSuite(final int count) {
    updateCountersAndProgressOnTestCount(count, false);
  }

  /**
   * Adds test to tree and updates status line.
   * Test proxy should be initialized, proxy parent must be some suite (already added to tree)
   *
   * @param testProxy Proxy
   */
  @ApiStatus.Internal
  @Override
  public void onTestStarted(@NotNull final SMTestProxy testProxy) {
    if (!testProxy.isConfig() && !TestListenerProtocol.CLASS_CONFIGURATION.equals(testProxy.getName())) {
      updateOnTestStarted(false);
    }
    _addTestOrSuite(testProxy);
    fireOnTestNodeAdded(testProxy);
  }

  @ApiStatus.Internal
  @Override
  public void onSuiteTreeNodeAdded(SMTestProxy testProxy) {
    if (!testProxy.isSuite()) {
      myTotalTestCount++;
    }
  }

  @ApiStatus.Internal
  @Override
  public void onSuiteTreeStarted(SMTestProxy suite) {
  }

  @ApiStatus.Internal
  @Override
  public void onTestFailed(@NotNull final SMTestProxy test) {
    if (Comparing.equal(test, myLastFailed)) return;
    myLastFailed = test;
    updateOnTestFailed(false);
    if (test.isConfig()) {
      myStartedTestCount++;
      myFinishedTestCount++;
    }
    else if (test.isSuite()) {
      myStartedTestCount++;
      updateTotalCount();
    }
    updateIconProgress(false);

    //still expand failure when user selected another test
    if (myLastSelected != null &&
        TestConsoleProperties.TRACK_RUNNING_TEST.value(myProperties) &&
        TestConsoleProperties.HIDE_PASSED_TESTS.value(myProperties)) {
      myTreeBuilder.expand(test);
    }
  }

  @ApiStatus.Internal
  @Override
  public void onTestIgnored(@NotNull final SMTestProxy test) {
    updateOnTestIgnored(test);
  }

  /**
   * Adds suite to tree
   * Suite proxy should be initialized, proxy parent must be some suite (already added to tree)
   * If parent is null, then suite will be added to tests root.
   *
   * @param newSuite Tests suite
   */
  @ApiStatus.Internal
  @Override
  public void onSuiteStarted(@NotNull final SMTestProxy newSuite) {
    _addTestOrSuite(newSuite);
  }

  @ApiStatus.Internal
  @Override
  public void onCustomProgressTestsCategory(@Nullable String categoryName, int testCount) {
    myCurrentCustomProgressCategory = categoryName;
    updateCountersAndProgressOnTestCount(testCount, true);
  }

  @ApiStatus.Internal
  @Override
  public void onCustomProgressTestStarted() {
    updateOnTestStarted(true);
  }

  @ApiStatus.Internal
  @Override
  public void onCustomProgressTestFailed() {
    updateOnTestFailed(true);
  }

  @ApiStatus.Internal
  @Override
  public void onCustomProgressTestFinished() {
    updateOnTestFinished(true);
  }

  @ApiStatus.Internal
  @Override
  public void onTestFinished(@NotNull final SMTestProxy test) {
    if (!test.isConfig()) {
      updateOnTestFinished(false);
    }
    updateIconProgress(false);
  }

  @ApiStatus.Internal
  @Override
  public void onSuiteFinished(@NotNull final SMTestProxy suite) {
    //Do nothing
  }

  @Override
  @NotNull
  public SMTestProxy.SMRootTestProxy getTestsRootNode() {
    return myTestsRootNode;
  }

  @Override
  public TestConsoleProperties getProperties() {
    return myProperties;
  }

  @Override
  public void setFilter(@NotNull final Filter filter) {
    // is used by Test Runner actions, e.g. hide passed, etc
    final SMTRunnerTreeStructure treeStructure = myTreeBuilder.getTreeStructure();
    treeStructure.setFilter(filter);

    // TODO - show correct info if no children are available
    // (e.g no tests found or all tests passed, etc.)
    // treeStructure.getChildElements(treeStructure.getRootElement()).length == 0

    myTreeBuilder.updateFromRoot();
  }

  @Override
  public boolean isRunning() {
    return myTestsRunning;
  }

  @Override
  public TestTreeView getTreeView() {
    return myTreeView;
  }

  @Override
  public SMTRunnerTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  @Override
  public boolean hasTestSuites() {
    return !getRoot().getChildren().isEmpty();
  }

  @Override
  @NotNull
  public AbstractTestProxy getRoot() {
    return myTestsRootNode;
  }

  /**
   * Manual test proxy selection in tests tree. E.g. do select root node on
   * testing started or do select current node if TRACK_RUNNING_TEST is enabled
   * <p/>
   * <p/>
   * Will select proxy in Event Dispatch Thread. Invocation of this
   * method may be not in event dispatch thread
   *
   * @param testProxy Test or suite
   */
  @Override
  public void selectAndNotify(AbstractTestProxy testProxy) {
    selectAndNotify(testProxy, null);
  }

  private void selectAndNotify(@Nullable final AbstractTestProxy testProxy, @Nullable Runnable onDone) {
    selectWithoutNotify(testProxy, onDone);


  }

  @Override
  public void addEventsListener(final EventsListener listener) {
    myEventListeners.add(listener);
    addTestsTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(final TreeSelectionEvent e) {
        //We should fire event only if it was generated by this component,
        //e.g. it is focused. Otherwise it is side effect of selecting proxy in
        //try by other component
        //if (myTreeView.isFocusOwner()) {
        @Nullable final SMTestProxy selectedProxy = (SMTestProxy)getTreeView().getSelectedTest();
        listener.onSelected(selectedProxy, SMTestRunnerResultsForm.this, SMTestRunnerResultsForm.this);
        //}
      }
    });
  }

  private boolean isDisposed() {
    return myDisposed || Disposer.isDisposed(this);
  }

  @Override
  public void dispose() {
    super.dispose();
    myEventListeners.clear();
    myDisposed = true;
  }

  protected int getTotalTestCount() {
    return myTotalTestCount;
  }

  protected int getStartedTestCount() {
    return myStartedTestCount;
  }

  public int getFinishedTestCount() {
    return myFinishedTestCount;
  }

  public int getFailedTestCount() {
    return myFailedTestCount;
  }

  public int getIgnoredTestCount() {
    return myIgnoredTestCount;
  }

  @ApiStatus.Internal
  public String getTestsStatus() {
    return myStatusLine.getStatus();
  }

  @ApiStatus.Internal
  public Set<String> getMentionedCategories() {
    return myMentionedCategories;
  }

  protected long getStartTime() {
    return myStartTime;
  }

  protected long getEndTime() {
    return myEndTime;
  }

  private void _addTestOrSuite(@NotNull final SMTestProxy newTestOrSuite) {

    final SMTestProxy parentSuite = newTestOrSuite.getParent();

    final Update update = new Update(ObjectUtils.notNull(parentSuite, getRoot())) {
      @Override
      public void run() {
        if (parentSuite == null || parentSuite.getParent() == null) {
          myUpdateTreeRequests.cancelAllRequests();
          myRequests.clear();
          myTreeBuilder.updateFromRoot();
        }
        else {
          myRequests.remove(this);
          myTreeBuilder.updateTestsSubtree(parentSuite);
        }
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      update.run();
    }
    else if (!isDisposed() && myRequests.add(update)) {
      myUpdateTreeRequests.addRequest(update, 50);
    }

    if (TestConsoleProperties.TRACK_RUNNING_TEST.value(myProperties)) {
      if (myLastSelected == null || myLastSelected == newTestOrSuite || isFiltered(myLastSelected)) {
        setLastSelected(null);
        selectAndNotify(newTestOrSuite);
      }
    }
  }

  private boolean isFiltered(AbstractTestProxy proxy) {
    return proxy instanceof SMTestProxy && 
           !getTreeBuilder().getTreeStructure().getFilter().shouldAccept((SMTestProxy)proxy);
  }

  private void setLastSelected(AbstractTestProxy proxy) {
    myLastSelected = proxy;
  }

  private void fireOnTestNodeAdded(@NotNull SMTestProxy test) {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestNodeAdded(this, test);
    }
  }

  private void fireOnTestingFinished() {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestingFinished(this);
    }
  }

  private void fireOnTestingStarted() {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestingStarted(this);
    }
  }

  private void selectWithoutNotify(final AbstractTestProxy testProxy, @Nullable final Runnable onDone) {
    if (testProxy == null) {
      return;
    }

    if (myTreeBuilder.isDisposed()) {
      return;
    }
    myTreeBuilder.select(testProxy, onDone);
  }

  private void updateStatusLabel(final boolean testingFinished) {
    if (myFailedTestCount > 0) {
      myStatusLine.setStatus(JBUI.CurrentTheme.ProgressBar.failedStatusValue());
    }

    // launchedAndFinished - is launched and not in progress. If we remove "launched' that onTestingStarted() before
    // initializing will be "launchedAndFinished"
    final boolean launchedAndFinished = myTestsRootNode.wasLaunched() && !myTestsRootNode.isInProgress();
    if (!TestsPresentationUtil.hasNonDefaultCategories(myMentionedCategories)) {
      myStatusLine.formatTestMessage(isUndefined() ? -1 : myTotalTestCount, myFinishedTestCount, myFailedTestCount, myIgnoredTestCount, myTestsRootNode.getDuration(), myEndTime);
    }
    else {
      myStatusLine.setText(TestsPresentationUtil.getProgressStatus_Text(myStartTime, myEndTime,
                                                                        myTotalTestCount, myFinishedTestCount,
                                                                        myFailedTestCount, myMentionedCategories,
                                                                        launchedAndFinished));
    }

    if (testingFinished) {
      boolean noTestsWereRun = myTotalTestCount == 0 && (myTestsRootNode.wasLaunched() || !myTestsRootNode.isTestsReporterAttached());
      myStatusLine.onTestsDone(noTestsWereRun ? null : () -> {
        TestStateInfo.Magnitude magnitude = myTestsRootNode.getMagnitudeInfo();
        if (magnitude == null) return null;
        return TestIconMapper.getToolbarIcon(magnitude, myTestsRootNode.hasErrors(), () -> myTestsRootNode.hasPassedTests());
      });
      final Color editorBackground = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
      myConsole.setBorder(new CompoundBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT | SideBorder.TOP),
                                             new SideBorder(editorBackground, SideBorder.LEFT)));
      revalidate();
      repaint();
      // else color will be according failed/passed tests
    }

    UIUtil.invokeLaterIfNeeded(() -> {
      myTreeView.setAccessibleStatus(myStatusLine.getStateText());
    });
  }

  private boolean isUndefined() {
    return myProperties instanceof SMTRunnerConsoleProperties && ((SMTRunnerConsoleProperties)myProperties).isUndefined();
  }

  /**
   * for java unit tests
   */
  @ApiStatus.Internal
  public void performUpdate() {
    myTreeBuilder.updateFromRoot();
  }

  private void updateIconProgress(boolean updateWithAttention) {
    final int totalTestCount, doneTestCount;
    if (myTotalTestCount == 0) {
      totalTestCount = 2;
      doneTestCount = 1;
    }
    else {
      totalTestCount = myTotalTestCount;
      doneTestCount = myFinishedTestCount;
    }
    UIUtil.invokeLaterIfNeeded(() -> TestsUIUtil.showIconProgress(myProject, doneTestCount, totalTestCount, myFailedTestCount, updateWithAttention));
  }


  private void updateCountersAndProgressOnTestCount(final int count, final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;

    //This is for better support groups of TestSuites
    //Each group notifies about it's size
    myTotalTestCount += count;
    updateStatusLabel(false);
  }

  private void updateOnTestStarted(final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;

    // for mixed tests results : mention category only if it contained tests
    myMentionedCategories
      .add(myCurrentCustomProgressCategory != null ? myCurrentCustomProgressCategory : TestsPresentationUtil.DEFAULT_TESTS_CATEGORY);

    myStartedTestCount++;
    updateTotalCount();

    updateStatusLabel(false);
  }

  private void updateTotalCount() {
    // fix total count if it is corrupted
    // but if test count wasn't set at all let's process such case separately
    if (myStartedTestCount > myTotalTestCount && myTotalTestCount != 0) {
      myTotalTestCount = myStartedTestCount;
    }
  }

  private void updateProgressOnTestDone() {
    int doneTestCount = myFinishedTestCount;
    // update progress
    if (isUndefined()) {
      myStatusLine.setFraction(1.0);
    }
    else if (myTotalTestCount != 0) {
      // if total is set
      myStatusLine.setFraction((double) doneTestCount / myTotalTestCount);
    }
    else {
      // if at least one test was launcher than just set progress in the middle to show user that tests are running
      myStatusLine.setFraction(doneTestCount > 0 ? 0.5 : 0);
    }
  }

  private void updateOnTestFailed(final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;
    myFailedTestCount++;
    updateProgressOnTestDone();
    updateStatusLabel(false);
  }

  private void updateOnTestFinished(final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;
    myFinishedTestCount++;
    updateProgressOnTestDone();
    updateStatusLabel(false);
  }

  private void updateOnTestIgnored(@NotNull final SMTestProxy test) {
    if (!test.isSuite()) {
      myIgnoredTestCount++;
    }
    updateProgressOnTestDone();
    updateStatusLabel(false);
  }

  private boolean isModeConsistent(boolean isCustomMessage) {
    // check that we are in consistent mode
    return isCustomMessage != (myCurrentCustomProgressCategory == null);
  }

  @ApiStatus.Internal
  public void setIncompleteIndexUsed() {
    myStatusLine.setWarning(SmRunnerBundle.message("suffix.incomplete.index.was.used"));
  }

  @ApiStatus.Internal
  public String getHistoryFileName() {
    return myHistoryFileName;
  }

  @ApiStatus.Internal
  AnAction[] getToolbarActions() { return myToolbarPanel.getActionsToMerge(); }

  @ApiStatus.Internal
  AnAction[] getAdditionalToolbarActions() { return myToolbarPanel.getAdditionalActionsToMerge(); }


  @Override
  protected void hideToolbar() {
    super.hideToolbar();
    myToolbarPanel.setVisible(false);
  }

  private static void addLabel(final TestFrameworkRunningModel model) {

    AbstractTestProxy root = model.getRoot();

    if (root.isInterrupted()) return;

    TestConsoleProperties consoleProperties = model.getProperties();
    String configName = consoleProperties.getConfiguration().getName();

    String name;
    int color;

    if (root.isPassed() || root.isIgnored()) {
      color = GREEN.getRGB();
      name = ExecutionBundle.message("junit.running.info.tests.passed.with.test.name.label", configName);
    }
    else {
      color = RED.getRGB();
      name = ExecutionBundle.message("junit.running.info.tests.failed.with.test.name.label", configName);
    }

    Project project = consoleProperties.getProject();
    if (project.isDisposed()) return;

    LocalHistory.getInstance().putSystemLabel(project, name, color);
  }

  private static class MySaveHistoryTask extends Task.Backgroundable {

    private final TestConsoleProperties myConsoleProperties;
    private SMTestProxy.SMRootTestProxy myRoot;
    private RunConfiguration myConfiguration;
    private File myOutputFile;
    MySaveHistoryTask(TestConsoleProperties consoleProperties,
                      SMTestProxy.SMRootTestProxy root,
                      RunConfiguration configuration, 
                      String outputFile) {
      super(consoleProperties.getProject(), SmRunnerBundle.message("sm.test.runner.results.form.save.test.results.title"), true);
      myConsoleProperties = consoleProperties;
      myRoot = root;
      myConfiguration = configuration;
      myOutputFile = new File(TestStateStorage.getTestHistoryRoot(myProject), outputFile + ".xml");
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      writeState();
      DaemonCodeAnalyzer.getInstance(getProject()).restart();
      try {
        SAXTransformerFactory transformerFactory = (SAXTransformerFactory)TransformerFactory.newDefaultInstance();
        TransformerHandler handler = transformerFactory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.getTransformer().setOutputProperty(OutputKeys.VERSION, "1.1");
        handler.getTransformer().setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        FileUtilRt.createParentDirs(myOutputFile);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(myOutputFile, StandardCharsets.UTF_8))) {
          handler.setResult(new StreamResult(writer));
          final SMTestProxy.SMRootTestProxy root = myRoot;
          final RunConfiguration configuration = myConfiguration;
          if (root != null && configuration != null) {
            TestResultsXmlFormatter.execute(root, configuration, myConsoleProperties, handler);
          }
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.info("Export to history failed", e);
      }
    }

    private void writeState() {
      if (myRoot == null) return;
      List<SMTestProxy> tests = myRoot.getAllTests();
      for (SMTestProxy proxy : tests) {
        String url = proxy.getLocationUrl();
        if (url != null && proxy.getLocator() != null) {
          String configurationName = myConfiguration != null ? myConfiguration.getName() : null;
          boolean isConfigurationDumbAware = myConfiguration != null && myConfiguration.getType().isDumbAware();
          boolean isSMTestLocatorDumbAware = DumbService.isDumbAware(proxy.getLocator());
          if (isConfigurationDumbAware && isSMTestLocatorDumbAware) {
            ApplicationManager.getApplication().runReadAction(() -> {
              writeTestState(proxy, url, configurationName);
            });
          }
          else {
            if (isConfigurationDumbAware /*&& !isSMTestLocatorDumbAware*/) {
              LOG.warn("Configuration " + myConfiguration.getType() +
                       " is dumb aware, but it's test locator " + proxy.getLocator() + " is not. " +
                       "It leads to an hanging update task on finishing a test case in dumb mode.");
            }
            DumbService.getInstance(getProject()).runReadActionInSmartMode(() -> {
              writeTestState(proxy, url, configurationName);
            });
          }
        }
      }
    }

    private void writeTestState(@NotNull SMTestProxy proxy, @NotNull String url, @Nullable String configurationName) {
      Project project = getProject();
      TestStackTraceParser info = getStackTraceParser(proxy, url, project);
      TestStateStorage storage = TestStateStorage.getInstance(project);
      storage.writeState(url, new TestStateStorage.Record(proxy.getMagnitude(), new Date(),
                                                          configurationName == null ? 0 : configurationName.hashCode(),
                                                          info.getFailedLine(), info.getFailedMethodName(),
                                                          info.getErrorMessage(), info.getTopLocationLine()));
    }

    private TestStackTraceParser getStackTraceParser(@NotNull SMTestProxy proxy, @NotNull String url, @NotNull Project project) {
      if (myConsoleProperties instanceof SMStacktraceParser) {
        return ((SMStacktraceParser)myConsoleProperties).getTestStackTraceParser(url, proxy, project);
      }
      else {
        return new TestStackTraceParser(url, proxy.getStacktrace(), proxy.getErrorMessage(), proxy.getLocator(), project);
      }
    }

    @Override
    public void onSuccess() {
      if (myOutputFile != null && myOutputFile.exists()) {
        AbstractImportTestsAction.adjustHistory(myProject);
        TestHistoryConfiguration.getInstance(myProject).registerHistoryItem(myOutputFile.getName(),
                                                                            myConfiguration.getName(),
                                                                            myConfiguration.getType().getId());
      }
    }

    public void dispose() {
      myConfiguration = null;
      myRoot = null;
      myOutputFile = null;
    }
  }
}
