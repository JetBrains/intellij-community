// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.AbstractTestProxy.SuiteResultPolicy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.export.ExportTestResultsAction;
import com.intellij.execution.testframework.export.ExportTestResultsConfiguration;
import com.intellij.execution.testframework.sm.Marker;
import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestIgnoredEvent;
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent;
import com.intellij.execution.testframework.sm.runner.events.TreeNodeEvent;
import com.intellij.execution.testframework.sm.runner.history.ImportedToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.execution.testframework.ui.TestsOutputConsolePrinter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Roman Chernyatchik
 */
public class GeneralToSMTRunnerEventsConvertorTest extends BaseSMTRunnerTestCase {
  private SMTRunnerConsoleView myConsole;
  private GeneralToSMTRunnerEventsConvertor myEventsProcessor;
  private TreeModel myTreeModel;
  private SMTestRunnerResultsForm myResultsViewer;
  private MockPrinter myMockResettablePrinter;
  private File myTempFile;

  private final class MyConsoleView extends SMTRunnerConsoleView {
    private final TestsOutputConsolePrinter myTestsOutputConsolePrinter;

    private MyConsoleView(final TestConsoleProperties consoleProperties) {
      super(consoleProperties);

      myTestsOutputConsolePrinter = new TestsOutputConsolePrinter(this, consoleProperties, null) {
        @Override
        public void print(final @NotNull String text, final @NotNull ConsoleViewContentType contentType) {
          myMockResettablePrinter.print(text, contentType);
        }
      };
    }

    @Override
    public TestsOutputConsolePrinter getPrinter() {
      return myTestsOutputConsolePrinter;
    }
  }

  @BeforeEach
  void setUp() {
    runInEdtAndWait(() -> {
      TestConsoleProperties consoleProperties = createConsoleProperties();
      TestConsoleProperties.HIDE_PASSED_TESTS.set(consoleProperties, false);
      TestConsoleProperties.OPEN_FAILURE_LINE.set(consoleProperties, false);
      TestConsoleProperties.SCROLL_TO_SOURCE.set(consoleProperties, false);
      TestConsoleProperties.SELECT_FIRST_DEFECT.set(consoleProperties, false);
      TestConsoleProperties.TRACK_RUNNING_TEST.set(consoleProperties, false);

      myMockResettablePrinter = new MockPrinter();
      myConsole = new MyConsoleView(consoleProperties);
      myConsole.initUI();
      myResultsViewer = myConsole.getResultsViewer();
      myEventsProcessor = new GeneralToSMTRunnerEventsConvertor(consoleProperties.getProject(), myResultsViewer.getTestsRootNode(), "SMTestFramework");
      myEventsProcessor.addEventsListener(myResultsViewer);
      myTreeModel = myResultsViewer.getTreeView().getModel();

      myEventsProcessor.onStartTesting();
      PlatformTestUtil.waitWhileBusy(myResultsViewer.getTreeView());
    });
  }

  @AfterEach
  void tearDown() {
    runInEdtAndWait(() -> {
      Disposer.dispose(myEventsProcessor);
      Disposer.dispose(myConsole);
      if (myTempFile != null) {
        assertFalse(myTempFile.exists());
      }
    });
  }

  @Test
  public void testOnStartedTesting() {
    runInEdtAndWait(() -> {
      final Object rootTreeNode = myTreeModel.getRoot();
      assertEquals(0, myTreeModel.getChildCount(rootTreeNode));

      final SMTRunnerNodeDescriptor nodeDescriptor =
          (SMTRunnerNodeDescriptor)((DefaultMutableTreeNode)rootTreeNode).getUserObject();
      assertFalse(nodeDescriptor.expandOnDoubleClick());

      final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
      assertNotNull(rootProxy);

      assertTrue(rootProxy.wasLaunched());
      assertTrue(rootProxy.isInProgress());
      assertTrue(rootProxy.isLeaf());

      assertEquals("[root]", rootTreeNode.toString());
    });
  }

  @Test
  public void testOnTestStarted() {
    runInEdtAndWait(() -> {
      onTestStarted("some_test");
      final String fullName = myEventsProcessor.getFullTestName("some_test");
      final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

      assertNotNull(proxy);
      assertTrue(proxy.isInProgress());

      final Object rootTreeNode = (myTreeModel.getRoot());

      assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
      final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
      assertNotNull(rootProxy);
      assertEquals(List.of(proxy), rootProxy.getChildren());


      onTestStarted("some_test2");
      final String fullName2 = myEventsProcessor.getFullTestName("some_test2");
      final SMTestProxy proxy2 = myEventsProcessor.getProxyByFullTestName(fullName2);
      assertEquals(List.of(proxy, proxy2), rootProxy.getChildren());
    });
  }

  @Test
  public void testOnTestStarted_Twice() {
    runInEdtAndWait(() -> {
      onTestStarted("some_test");
      onTestStarted("some_test");

      assertEquals(1, myEventsProcessor.getRunningTestsQuantity());
    });
  }

  @Test
  public void testCheckMetaUpdateWithValue() {
    runInEdtAndWait(() -> checkMetainfo("suiteMetaUpdate", "testMetaUpdate"));
  }

  @Test
  public void testCheckMetaUpdateWithNull() {
    runInEdtAndWait(() -> checkMetainfo(null, null));
  }

  private void checkMetainfo(@Nullable String updatedSuiteMetainfo, @Nullable String updatedTestMetainfo) {
    final String suiteName = "some_suite";
    final String testName = "some_test";
    final String initSuiteMeta = "suiteMeta";
    final String initTestMeta = "testMeta";

    myEventsProcessor.onSuiteTreeNodeAdded(true, suiteName, suiteName, initSuiteMeta, suiteName, TreeNodeEvent.ROOT_NODE_ID);
    myEventsProcessor.onSuiteTreeNodeAdded(false, testName, testName, initTestMeta, testName, suiteName);
    myEventsProcessor.onSuiteTreeEnded(testName);
    myEventsProcessor.onSuiteTreeEnded(suiteName);
    myEventsProcessor.onBuildTreeEnded();

    myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent(suiteName, suiteName, updatedSuiteMetainfo));
    myEventsProcessor.onTestStarted(new TestStartedEvent(testName, testName, updatedTestMetainfo));

    myResultsViewer.performUpdate();
    PlatformTestUtil.waitWhileBusy(myResultsViewer.getTreeView());

    final SMTestProxy suiteProxy = myEventsProcessor.findChild(null, suiteName, true);
    assertEquals(updatedSuiteMetainfo == null ? initSuiteMeta : updatedSuiteMetainfo, suiteProxy.getMetainfo());

    final SMTestProxy testProxy = myEventsProcessor.findChild(suiteProxy, testName, false);
    assertEquals(updatedTestMetainfo == null ? initTestMeta : updatedTestMetainfo, testProxy.getMetainfo());
  }

  @Test
  public void testOnTestStarted_WithLocation() {
    runInEdtAndWait(() -> {
      onTestStarted("some_test", "file://some/file.rb:1");
      final String fullName = myEventsProcessor.getFullTestName("some_test");
      final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

      assertNotNull(proxy);
      assertEquals("file://some/file.rb:1", proxy.getLocationUrl());
    });
  }

  @Test
  public void testOnTestFailure() {
    runInEdtAndWait(() -> {
      onTestStarted("some_test");
      myEventsProcessor.onTestFailure(new TestFailedEvent("some_test", "", "", false, null, null));

      final String fullName = myEventsProcessor.getFullTestName("some_test");
      final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

      assertNotNull(proxy);
      assertTrue(proxy.isDefect());
      assertFalse(proxy.isInProgress());
    });
  }

  @Test
  public void testOnTestComparisonFailure() {
    runInEdtAndWait(() -> {
      onTestStarted("some_test");
      myEventsProcessor.onTestFailure(new TestFailedEvent("some_test", "", "", false, "actual", "expected"));

      final String fullName = myEventsProcessor.getFullTestName("some_test");
      final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

      assertNotNull(proxy);
      assertTrue(proxy.isDefect());
      assertFalse(proxy.isInProgress());
    });
  }

  @Test
  public void testOnTestComparisonFailureLongExpectedText() throws IOException {
    runInEdtAndWait(() -> {
      onTestStarted("some_test");
      myTempFile = FileUtil.createTempFile("expected", "");
      FileUtil.writeToFile(myTempFile, "expected text");
      myEventsProcessor.onTestFailure(new TestFailedEvent("some_test", "", "", null, false, "actual",
                                                          "expected", myTempFile.getCanonicalPath(), null, true, false, -1));

      final String fullName = myEventsProcessor.getFullTestName("some_test");
      final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

      assertNotNull(proxy);
      assertTrue(proxy.isDefect());
      assertFalse(proxy.isInProgress());
    });
  }

  @Test
  public void testOnTestTruncatedComparisonFailure() {
    runInEdtAndWait(() -> {
      onTestStarted("some_test");
      myEventsProcessor.onTestFailure(new TestFailedEvent("some_test", "", "", false, "actual", null));

      final String fullName = myEventsProcessor.getFullTestName("some_test");
      final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

      assertNotNull(proxy);
      assertTrue(proxy.isDefect());
      assertFalse(proxy.isInProgress());
    });
  }

  @Test
  public void testOnTestFailure_Twice() throws InterruptedException {
    runInEdtAndWait(() -> {
      myMockResettablePrinter.resetIfNecessary();
      onTestStarted("some_test");
      myEventsProcessor.onTestFailure(new TestFailedEvent("some_test", "msg 1", "trace 1", false, null, null));
      myEventsProcessor.onTestFailure(new TestFailedEvent("some_test", "msg 2", "trace 2", false, null, null));

      assertEquals(1, myEventsProcessor.getRunningTestsQuantity());
      Thread.sleep(150);
      PlatformTestUtil.waitWhileBusy(myResultsViewer.getTreeView());
      assertEquals("\nmsg 1\ntrace 1\n\nmsg 2\ntrace 2\n", myMockResettablePrinter.getStdErr());
    });
  }

  @Test
  public void testOnTestError() {
    runInEdtAndWait(() -> {
      onTestStarted("some_test");
      myEventsProcessor.onTestFailure(new TestFailedEvent("some_test", "", "", true, null, null));

      final String fullName = myEventsProcessor.getFullTestName("some_test");
      final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

      assertNotNull(proxy);
      assertTrue(proxy.isDefect());
      assertFalse(proxy.isInProgress());
    });
  }

  @Test
  public void testOnTestIgnored() {
    runInEdtAndWait(() -> {
      onTestStarted("some_test");
      myEventsProcessor.onTestIgnored(new TestIgnoredEvent("some_test", "", null));

      final String fullName = myEventsProcessor.getFullTestName("some_test");
      final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);

      assertNotNull(proxy);
      assertTrue(proxy.isDefect());
      assertFalse(proxy.isInProgress());

      final Filter filter = AbstractRerunFailedTestsAction.getFailuresFilter(myConsole.getProperties());
      assertFalse(filter.shouldAccept(proxy));
    });
  }

  @Test
  public void testOnTestFinished() {
    runInEdtAndWait(() -> {
      onTestStarted("some_test");
      final String fullName = myEventsProcessor.getFullTestName("some_test");
      final SMTestProxy proxy = myEventsProcessor.getProxyByFullTestName(fullName);
      myEventsProcessor.onTestFinished(new TestFinishedEvent("some_test", 10L));
      PlatformTestUtil.waitWhileBusy(myResultsViewer.getTreeView());

      assertEquals(0, myEventsProcessor.getRunningTestsQuantity());

      assertNotNull(proxy);
      assertFalse(proxy.isDefect());
      assertFalse(proxy.isInProgress());

      //Tree
      final Object rootTreeNode = myTreeModel.getRoot();
      PlatformTestUtil.waitWhileBusy(myResultsViewer.getTreeView());
      assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
      final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
      assertNotNull(rootProxy);
      assertEquals(List.of(proxy), rootProxy.getChildren());
    });
  }

  //TODO[romeo] catch assertion
  //public void testFinished_Twice() {
  //  myEventsProcessor.onTestStarted("some_test");
  //  myEventsProcessor.onTestFinished("some_test");
  //  myEventsProcessor.onTestFinished("some_test");
  //
  //  assertEquals(1, myEventsProcessor.getTestsCurrentCount());
  //  assertEquals(0, myEventsProcessor.getRunningTestsFullNameToProxy().size());
  //  assertEquals(0, myEventsProcessor.getFailedTestsSet().size());
  //
  //}

  @Test
  public void testOnTestFinished_EmptySuite() {
    runInEdtAndWait(() -> {
      myEventsProcessor.onFinishTesting();

      //Tree
      final Object rootTreeNode = myTreeModel.getRoot();
      assertEquals(0, myTreeModel.getChildCount(rootTreeNode));
      final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
      assertNotNull(rootProxy);

      assertFalse(rootProxy.isInProgress());
      assertFalse(rootProxy.isDefect());
    });
  }

  @Test
  public void testOnFinishedTesting_WithFailure() {
    runInEdtAndWait(() -> {
      onTestStarted("test");
      myEventsProcessor.onTestFailure(new TestFailedEvent("test", "", "", false, null, null));
      myEventsProcessor.onTestFinished(new TestFinishedEvent("test", 10L));
      myEventsProcessor.onFinishTesting();
      PlatformTestUtil.waitWhileBusy(myResultsViewer.getTreeView());

      //Tree
      final Object rootTreeNode = myTreeModel.getRoot();
      assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
      final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
      assertNotNull(rootProxy);

      assertFalse(rootProxy.isInProgress());
      assertTrue(rootProxy.isDefect());
    });
  }

  @Test
  public void testOnFinishedTesting_WithError() {
    runInEdtAndWait(() -> {
      onTestStarted("test");
      myEventsProcessor.onTestFailure(new TestFailedEvent("test", "", "", true, null, null));
      myEventsProcessor.onTestFinished(new TestFinishedEvent("test", 10L));
      myEventsProcessor.onFinishTesting();
      PlatformTestUtil.waitWhileBusy(myResultsViewer.getTreeView());

      //Tree
      final Object rootTreeNode = myTreeModel.getRoot();
      assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
      final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
      assertNotNull(rootProxy);

      assertFalse(rootProxy.isInProgress());
      assertTrue(rootProxy.isDefect());
    });
  }

  @Test
  public void testOnFinishedTesting_WithIgnored() {
    runInEdtAndWait(() -> {
      onTestStarted("test");
      myEventsProcessor.onTestIgnored(new TestIgnoredEvent("test", "", null));
      myEventsProcessor.onTestFinished(new TestFinishedEvent("test", 10L));
      myEventsProcessor.onFinishTesting();
      PlatformTestUtil.waitWhileBusy(myResultsViewer.getTreeView());

      //Tree
      final Object rootTreeNode = myTreeModel.getRoot();
      assertEquals(1, myTreeModel.getChildCount(rootTreeNode));
      final SMTestProxy rootProxy = SMTRunnerTestTreeView.getTestProxyFor(rootTreeNode);
      assertNotNull(rootProxy);

      assertFalse(rootProxy.isInProgress());
      assertTrue(rootProxy.isDefect());
    });
  }

  @Test
  public void testOnFinishedTesting_twice() {
    runInEdtAndWait(() -> {
      myEventsProcessor.onFinishTesting();

      final Marker finishedMarker = new Marker();
      myEventsProcessor.addEventsListener(new SMTRunnerEventsAdapter(){
        @Override
        public void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy testsRoot) {
          finishedMarker.set();
        }
      });
      myEventsProcessor.onFinishTesting();
      assertFalse(finishedMarker.isSet());
    });
  }

  @Test
  public void testOnSuiteStarted() {
    runInEdtAndWait(() -> {
      onTestSuiteStarted("suite1");

      //lets check that new tests have right parent
      onTestStarted("test1");
      final SMTestProxy test1 =
          myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test1"));
      assertNotNull(test1);
      assertEquals("suite1", test1.getParent().getName());

      //lets check that new suits have righ parent
      onTestSuiteStarted("suite2");
      onTestSuiteStarted("suite3");
      onTestStarted("test2");
      final SMTestProxy test2 =
          myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test2"));
      assertNotNull(test2);
      assertEquals("suite3", test2.getParent().getName());
      assertEquals("suite2", test2.getParent().getParent().getName());

      myEventsProcessor.onTestFinished(new TestFinishedEvent("test2", 10L));

      //check that after finishing suite (suite3), current will be parent of finished suite (i.e. suite2)
      myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite3"));
      onTestStarted("test3");
      final SMTestProxy test3 =
          myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test3"));
      assertNotNull(test3);
      assertEquals("suite2", test3.getParent().getName());

      //clean up
      myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite2"));
      myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite1"));
    });
  }

  @Test
  public void testOnSuiteStarted_WithLocation() {
    runInEdtAndWait(() -> {
      onTestSuiteStarted("suite1", "file://some/file.rb:1");

      //lets check that new tests have right parent
      onTestStarted("test1", "file://some/file.rb:4");
      final SMTestProxy test1 =
          myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("test1"));

      assertNotNull(test1);
      assertEquals("file://some/file.rb:1", test1.getParent().getLocationUrl());
      assertEquals("file://some/file.rb:4", test1.getLocationUrl());
    });
  }

  @Test
  public void testGetCurrentTestSuite() {
    runInEdtAndWait(() -> {
      assertEquals(myResultsViewer.getTestsRootNode(), myEventsProcessor.getCurrentSuite());

      onTestSuiteStarted("my_suite");
      assertEquals("my_suite", myEventsProcessor.getCurrentSuite().getName());
    });
  }

  @Test
  public void testConcurrentSuite_intersected() {
    runInEdtAndWait(() -> {
      myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent("suite1", null));
      myEventsProcessor.onTestStarted(new TestStartedEvent("suite2.test1", null));

      final SMTestProxy test1 =
          myEventsProcessor.getProxyByFullTestName(myEventsProcessor.getFullTestName("suite2.test1"));

      myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite1"));

      myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent("suite2", null));
      myEventsProcessor.onTestFinished(new TestFinishedEvent("suite2.test1", 10L));
      myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("suite2"));

      assertNotNull(test1);
      assertEquals("suite1", test1.getParent().getName());

      final List<? extends SMTestProxy> children =
          myResultsViewer.getTestsRootNode().getChildren();
      assertEquals(2, children.size());
      assertEquals("suite1", children.get(0).getName());
      assertEquals(1, children.get(0).getChildren().size());
      assertEquals("suite2", children.get(1).getName());
      assertEquals(0, children.get(1).getChildren().size());
    });
  }

  @Test
  public void testClassConfigurationFailedCount() {
    runInEdtAndWait(() -> {
      myEventsProcessor.onTestStarted(new TestStartedEvent("Class Configuration", "java:suite://com.jetbrains.testing.ATestCase"));
      myEventsProcessor.onTestFailure(new TestFailedEvent("Class Configuration", "", "", true, null, null));
      myEventsProcessor.onTestFinished(new TestFinishedEvent("Class Configuration", null));
      myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent("ATestCase", "java:suite://com.jetbrains.testing.ATestCase"));
      myEventsProcessor.onTestStarted(new TestStartedEvent("ATestCase.test1", "java:test://com.jetbrains.testing.ATestCase/test1"));
      myEventsProcessor.onTestFinished(new TestFinishedEvent("ATestCase.test1", null));
      myEventsProcessor.onTestStarted(new TestStartedEvent("ATestCase.test2", "java:test://com.jetbrains.testing.ATestCase/test2"));
      myEventsProcessor.onTestFinished(new TestFinishedEvent("ATestCase.test2", null));

      myEventsProcessor.onTestStarted(new TestStartedEvent("Class Configuration", "java:suite://com.jetbrains.testing.BTestCase"));
      myEventsProcessor.onTestFailure(new TestFailedEvent("Class Configuration", "", "", true, null, null));
      myEventsProcessor.onTestFinished(new TestFinishedEvent("Class Configuration", null));
      myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("ATestCase"));
      myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent("BTestCase", "java:suite://com.jetbrains.testing.BTestCase"));
      myEventsProcessor.onTestStarted(new TestStartedEvent("BTestCase.test1", "java:test://com.jetbrains.testing.BTestCase/test1"));
      myEventsProcessor.onTestFinished(new TestFinishedEvent("BTestCase.test1", null));
      myEventsProcessor.onSuiteFinished(new TestSuiteFinishedEvent("BTestCase"));

      assertEquals(5, myResultsViewer.getFinishedTestCount());
      assertEquals(2, myResultsViewer.getFailedTestCount());
    });
  }

  @Test
  public void test3212() {
    runInEdtAndWait(() -> {
      // let's make
      myEventsProcessor.clearInternalSuitesStack();

      assertEquals(myResultsViewer.getTestsRootNode(), myEventsProcessor.getCurrentSuite());
    });
  }

  private void onTestStarted(final String testName) {
    onTestStarted(testName, null);
  }

  private void onTestStarted(final String testName, @Nullable final String locationUrl) {
    myEventsProcessor.onTestStarted(new TestStartedEvent(testName, locationUrl));
    myResultsViewer.performUpdate();
    PlatformTestUtil.waitWhileBusy(myResultsViewer.getTreeView());
  }

  private void onTestSuiteStarted(final String suiteName) {
    onTestSuiteStarted(suiteName, null);
  }

  private void onTestSuiteStarted(final String suiteName, @Nullable final String locationUrl) {
    myEventsProcessor.onSuiteStarted(new TestSuiteStartedEvent(suiteName, locationUrl));
    myResultsViewer.performUpdate();
  }

  @Test
  public void testSampleImportTest() throws Exception {
    runInEdtAndWait(() -> {
      myEventsProcessor.onStartTesting();
      ImportedToGeneralTestEventsConverter.parseTestResults(() -> new StringReader("""
                                                                                     <?xml version="1.0" encoding="UTF-8"?><testrun duration="592" footerText="Generated by IntelliJ IDEA" name="suite1">
                                                                                         <suite name="suite1" status="failed">
                                                                                             <test name="ATest" status="failed"/>
                                                                                         </suite>
                                                                                     </testrun>
                                                                                     """),
                                                            myEventsProcessor);
      myEventsProcessor.onFinishTesting();

      final List<? extends SMTestProxy> children = myResultsViewer.getTestsRootNode().getChildren();
      assertEquals(1, children.size());
      final SMTestProxy suite = children.get(0);
      assertEquals("suite1", suite.getName());
      final List<? extends SMTestProxy> tests = suite.getChildren();
      assertEquals(1, tests.size());
      assertEquals("ATest", tests.get(0).getName());
    });
  }

  @Test
  public void testComparisonFailure() throws Exception {
    runInEdtAndWait(() -> {
      myEventsProcessor.onStartTesting();
      ImportedToGeneralTestEventsConverter.parseTestResults(() -> new StringReader("""
                                                                                     <?xml version="1.0" encoding="UTF-8"?><testrun duration="592" footerText="Generated by IntelliJ IDEA" name="suite1">
                                                                                         <suite name="suite1" status="failed">
                                                                                             <test name="ATest" status="failed">
                                                                                                <output type="stderr">java.lang.AssertionError:\s
                                                                                                </output>
                                                                                                <output type="stdout">Expected :
                                                                                                </output>
                                                                                                <output type="stderr">2
                                                                                                </output>
                                                                                                <output type="stdout">Actual   :
                                                                                                </output>           <diff actual="3" expected="2"/></test>    </suite>
                                                                                     </testrun>
                                                                                     """),
                                                            myEventsProcessor);
      myEventsProcessor.onFinishTesting();

      final List<? extends SMTestProxy> children = myResultsViewer.getTestsRootNode().getChildren();
      assertEquals(1, children.size());
      final SMTestProxy suite = children.get(0);
      assertEquals("suite1", suite.getName());
      final List<? extends SMTestProxy> tests = suite.getChildren();
      assertEquals(1, tests.size());
      SMTestProxy proxy = tests.get(0);
      assertEquals("ATest", proxy.getName());
      DiffHyperlink diffHyperlink = proxy.getDiffViewerProvider();
      assertNotNull(diffHyperlink);
      assertEquals("2", diffHyperlink.getLeft());
    });
  }

  @Test
  public void testPreserveOutputOnImport() throws Exception {
    runInEdtAndWait(() -> {
      myEventsProcessor.onStartTesting();
      ImportedToGeneralTestEventsConverter.parseTestResults(() -> new StringReader("""
                                                                                     <?xml version="1.0" encoding="UTF-8"?><testrun duration="592" footerText="Generated by IntelliJ IDEA" name="suite1">
                                                                                         <suite name="suite1" status="failed">
                                                                                             <test name="ATest" status="failed">
                                                                                               <output type="stdout">test output</output>          <output type="stderr">error output</output>        </test>    </suite>
                                                                                     </testrun>
                                                                                     """),
                                                            myEventsProcessor);
      myEventsProcessor.onFinishTesting();

      final List<? extends SMTestProxy> children = myResultsViewer.getTestsRootNode().getChildren();
      assertEquals(1, children.size());
      final SMTestProxy suite = children.get(0);
      assertEquals("suite1", suite.getName());
      final List<? extends SMTestProxy> tests = suite.getChildren();
      assertEquals(1, tests.size());
      SMTestProxy proxy = tests.get(0);
      assertEquals("ATest", proxy.getName());
      MockPrinter printer = new MockPrinter();
      proxy.printOn(printer);
      assertEquals("test output", printer.getStdOut().trim());
      assertEquals("error output", printer.getStdErr().trim());
    });
  }

  @Test
  public void testSampleImportTestWithMetainfo() throws Exception {
    runInEdtAndWait(() -> {
      myEventsProcessor.onStartTesting();
      ImportedToGeneralTestEventsConverter.parseTestResults(() -> new StringReader("""
                                                                                     <?xml version="1.0" encoding="UTF-8"?><testrun duration="592" footerText="Generated by IntelliJ IDEA" name="suite1">
                                                                                         <suite name="suite1" status="failed" metainfo="any:info:string:that:can:help?navigation">
                                                                                             <test name="ATest" status="failed" metainfo="but is not a part of primary key"/>
                                                                                         </suite>
                                                                                     </testrun>
                                                                                     """),
                                                            myEventsProcessor);
      myEventsProcessor.onFinishTesting();

      final List<? extends SMTestProxy> children = myResultsViewer.getTestsRootNode().getChildren();
      assertEquals(1, children.size());
      final SMTestProxy suite = children.get(0);
      assertEquals("suite1", suite.getName());
      assertEquals("any:info:string:that:can:help?navigation", suite.getMetainfo());
      final List<? extends SMTestProxy> tests = suite.getChildren();
      assertEquals(1, tests.size());
      final SMTestProxy test = tests.get(0);
      assertEquals("ATest", test.getName());
      assertEquals("but is not a part of primary key", test.getMetainfo());
    });
  }

  @Test
  public void testEscapedImportTest() throws Exception {
    runInEdtAndWait(() -> {
      myEventsProcessor.onStartTesting();

      ImportedToGeneralTestEventsConverter.parseTestResults(() -> new StringReader("""
                                                                                     <?xml version="1.0" encoding="UTF-8"?><testrun duration="592" footerText="Generated by IntelliJ IDEA" name="suite1">
                                                                                         <suite name="sui&amp;te1" status="failed">
                                                                                             <test name="ATe&amp;st" status="failed"/>
                                                                                         </suite>
                                                                                     </testrun>
                                                                                     """), myEventsProcessor);
      myEventsProcessor.onFinishTesting();

      final List<? extends SMTestProxy> children = myResultsViewer.getTestsRootNode().getChildren();
      assertEquals(1, children.size());
      final SMTestProxy suite = children.get(0);
      assertEquals("sui&te1", suite.getName());
      final List<? extends SMTestProxy> tests = suite.getChildren();
      assertEquals(1, tests.size());
      assertEquals("ATe&st", tests.get(0).getName());
    });
  }

  @Test
  public void testIntellijExportXslEscaping() throws Exception {
    runInEdtAndWait(() -> {
      String systemOutput = "<span onclick=\"alert(\"Ops!\")\">SYSTEM_OUTPUT\n<span>";
      String escapedSystemOutput = "&lt;span onclick=\"alert(\"Ops!\")\"&gt;SYSTEM_OUTPUT<br/>&lt;span&gt;<br/>";
      String normalOutput = "<div onclick=\"alert(\"Ops!!\")\">NORMAL_OUTPUT<span>";
      String escapedNormalOutput = "&lt;div onclick=\"alert(\"Ops!!\")\"&gt;NORMAL_OUTPUT&lt;span&gt;<br/>";
      String errorOutput = "<p onclick=\"alert(\"Ops!!!\")\">ERROR_OUTPUT<div>";
      String escapedErrorOutput = "&lt;p onclick=\"alert(\"Ops!!!\")\"&gt;ERROR_OUTPUT&lt;div&gt;<br/>";

      mySuite.addChild(mySimpleTest);
      mySimpleTest.addLast(printer -> {
        printer.print(systemOutput, ConsoleViewContentType.SYSTEM_OUTPUT);
        printer.print(normalOutput, ConsoleViewContentType.NORMAL_OUTPUT);
        printer.print(errorOutput, ConsoleViewContentType.ERROR_OUTPUT);
      });
      mySimpleTest.setFinished();
      mySuite.setFinished();

      String renderedResult = exportTestResults(mySuite, ExportTestResultsConfiguration.ExportFormat.BundledTemplate);

      assertThat(renderedResult).contains(escapedSystemOutput);
      assertThat(renderedResult).contains(escapedNormalOutput);
      assertThat(renderedResult).contains(escapedErrorOutput);
    });
  }

  private @NotNull String exportTestResults(
    @NotNull AbstractTestProxy root,
    @NotNull ExportTestResultsConfiguration.ExportFormat exportFormat
  ) throws IOException, TransformerException, SAXException {
    Path outputFile = Files.createTempFile("output", "");
    try {
      MockRuntimeConfiguration configuration = new MockRuntimeConfiguration(getProject());
      ExportTestResultsAction.writeOutputFile(new ExportTestResultsAction.ExportContext(
        exportFormat,
        null,
        root,
        configuration,
        new SMTRunnerConsoleProperties(configuration, "framework", DefaultRunExecutor.getRunExecutorInstance()),
        outputFile
      ));
      return Files.readString(outputFile);
    }
    finally {
      Files.delete(outputFile);
    }
  }

  @Test
  public void testSuiteResultPolicySurvivesNodeReuse() {
    var root = new SMTestProxy.SMRootTestProxy();
    var processor = new GeneralToSMTRunnerEventsConvertor(getProject(), root, "SMTestFramework");
    try {
      processor.onStartTesting();
      processor.onSuiteTreeStarted("retry", "java:test://Tests/retry", null, null, null);
      processor.onSuiteTreeEnded("retry");
      processor.onBuildTreeEnded();
      var started = new TestSuiteStartedEvent("retry", "java:test://Tests/retry");
      started.setSuiteResultPolicy(SuiteResultPolicy.LAST_CHILD);
      processor.onSuiteStarted(started);
      processor.onSuiteFinished(new TestSuiteFinishedEvent("retry"));
      processor.onSuiteStarted(new TestSuiteStartedEvent("retry", "java:test://Tests/retry"));

      assertEquals(1, root.getChildren().size());
      assertEquals(SuiteResultPolicy.LAST_CHILD, processor.getCurrentSuite().getSuiteResultPolicy());

      processor.onSuiteFinished(new TestSuiteFinishedEvent("retry"));
      started.setSuiteResultPolicy(SuiteResultPolicy.ALL_CHILDREN);
      processor.onSuiteStarted(started);
      assertEquals(SuiteResultPolicy.ALL_CHILDREN, processor.getCurrentSuite().getSuiteResultPolicy());
      processor.onSuiteFinished(new TestSuiteFinishedEvent("retry"));
      processor.onFinishTesting();
    }
    finally {
      Disposer.dispose(processor);
      Disposer.dispose(root);
    }
  }

  @Test
  public void testSuiteResultPolicySurvivesHistoryRoundTrip() throws Exception {
    mySuite.setSuiteStarted();
    var retrySuite = createSuiteProxy("retry", mySuite);
    retrySuite.setSuiteResultPolicy(SuiteResultPolicy.LAST_CHILD);
    retrySuite.setSuiteStarted();
    retrySuite.addChild(mySimpleTest);
    mySimpleTest.setStarted();
    mySimpleTest.setTestFailed("failure", "trace", false);
    var last = createTestProxy("last", retrySuite);
    last.setStarted();
    last.setFinished();
    retrySuite.setFinished();
    mySuite.setFinished();

    String savedText = exportTestResults(mySuite, ExportTestResultsConfiguration.ExportFormat.Xml);
    assertTrue(savedText.contains("resultPolicy=\"lastChild\""));
    assertSuiteResultPolicyAfterImport(savedText, SuiteResultPolicy.LAST_CHILD);
    assertSuiteResultPolicyAfterImport(savedText.replace(" resultPolicy=\"lastChild\"", ""), SuiteResultPolicy.ALL_CHILDREN);
    assertSuiteResultPolicyAfterImport(savedText.replace("lastChild", "unknown"), SuiteResultPolicy.ALL_CHILDREN);
  }

  private void assertSuiteResultPolicyAfterImport(String savedText, SuiteResultPolicy expectedPolicy) throws Exception {
    var root = new SMTestProxy.SMRootTestProxy();
    var processor = new GeneralToSMTRunnerEventsConvertor(getProject(), root, "SMTestFramework");
    try {
      processor.onStartTesting();
      ImportedToGeneralTestEventsConverter.parseTestResults(() -> new StringReader(savedText), processor);
      processor.onFinishTesting();

      SMTestProxy retry = root.getChildren().getFirst();
      assertEquals(expectedPolicy, retry.getSuiteResultPolicy());
      assertEquals(expectedPolicy == SuiteResultPolicy.LAST_CHILD, retry.isPassed());
      assertTrue(retry.getChildren().getFirst().isDefect());
      assertTrue(retry.getChildren().getLast().isPassed());
      assertEquals(expectedPolicy == SuiteResultPolicy.LAST_CHILD, root.isPassed());
    }
    finally {
      Disposer.dispose(processor);
      Disposer.dispose(root);
    }
  }

  @Test
  public void testPreserveFullOutputAfterImport() throws Exception {
    runInEdtAndWait(() -> {

      mySuite.addChild(mySimpleTest);
      for (int i = 0; i < 550; i++) {
        String message = "line" + i + "\u0000 a < b \n";
        mySimpleTest.addLast(printer -> printer.print(message, ConsoleViewContentType.NORMAL_OUTPUT));
      }
      mySimpleTest.setTestComparisonFailed("message", "stacktrace", "\u0000", "a < b");
      mySimpleTest.setFinished();
      mySuite.setFinished();

      String savedText = exportTestResults(mySuite, ExportTestResultsConfiguration.ExportFormat.Xml);
      assertTrue(savedText.split("\n").length > 550);

      myEventsProcessor.onStartTesting();
      ImportedToGeneralTestEventsConverter.parseTestResults(() -> new StringReader(savedText), myEventsProcessor);
      myEventsProcessor.onFinishTesting();

      List<? extends SMTestProxy> children = myResultsViewer.getTestsRootNode().getChildren();
      assertThat(children).hasSize(1);
      SMTestProxy testProxy = children.get(0);
      MockPrinter mockPrinter = new MockPrinter();
      mockPrinter.setShowHyperLink(true);
      testProxy.printOn(mockPrinter);
      String allOut = mockPrinter.getAllOut();
      assertThat(allOut).contains(StringsKt.trimMargin("""
        |line549 a < b\s
        |
        |Expected :a < b
        |Actual   :
        |<Click to see difference>
        |
        |messagestacktrace
      """, "|"));
      assertThat(allOut.split("\n")).hasSize(558);
    });
  }

  @Test
  public void testComparisonFailureImport() throws Exception {
    runInEdtAndWait(() -> {

      mySuite.addChild(mySimpleTest);
      mySimpleTest.addLast(printer -> printer.print("execution output", ConsoleViewContentType.NORMAL_OUTPUT));
      mySimpleTest.setTestComparisonFailed("message", "stacktrace", "actual", "expected");
      mySimpleTest.setFinished();
      mySuite.setFinished();

      String savedText = exportTestResults(mySuite, ExportTestResultsConfiguration.ExportFormat.Xml);
      assertTrue(StringUtil.convertLineSeparators(savedText)
                   .endsWith("""
                               <count name="total" value="1"/>
                                   <count name="failed" value="1"/>
                                   <config configId="MockRuntimeConfiguration" name="">
                                       <method v="2"/>
                                   </config>
                                   <test locationUrl="file://test.text" name="test" status="failed">
                                       <diff actual="actual" expected="expected"/>
                                       <output type="stdout">execution output
                               </output>
                                       <output type="stderr">messagestacktrace
                               </output>
                                   </test>
                               </testrun>
                               """));

      myEventsProcessor.onStartTesting();
      ImportedToGeneralTestEventsConverter.parseTestResults(() -> new StringReader(savedText), myEventsProcessor);
      myEventsProcessor.onFinishTesting();

      List<? extends SMTestProxy> children = myResultsViewer.getTestsRootNode().getChildren();
      assertThat(children).hasSize(1);
      SMTestProxy testProxy = children.get(0);
      MockPrinter mockPrinter = new MockPrinter();
      mockPrinter.setShowHyperLink(true);
      testProxy.printOn(mockPrinter);
      assertEquals(StringsKt.trimMargin("""
        |execution output
        |
        |Expected :expected
        |Actual   :actual
        |<Click to see difference>
        |
        |messagestacktrace
      """, "|"), StringUtil.convertLineSeparators(mockPrinter.getAllOut().trim()));
    });
  }
}
