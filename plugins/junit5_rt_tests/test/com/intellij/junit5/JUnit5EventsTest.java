// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.junit5.testData.AnnotationsTestClass;
import com.intellij.junit5.testData.MyTestClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.*;
import org.junit.platform.launcher.TestIdentifier;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class JUnit5EventsTest {

  @Test
  void multipleFailures() throws Exception {
    JUnit5TestRunnerBuilder builder = new JUnit5TestRunnerBuilder();
    JUnit5TestRunnerBuilder.TestDescriptorContext testContext = builder
      .withRootName("testClass")
      .withPresentableName("testClass")
      .withTestMethod(MyTestClass.class, "test1");

    builder.buildTestPlan().execute();
    testContext.startExecution();

    MultipleFailuresError multipleFailuresError = new MultipleFailuresError("2 errors", Arrays.asList(
      new AssertionFailedError("message1", "expected1", "actual1"),
      new AssertionFailedError("message2", "expected2", "actual2")
    ));

    Map<String, String> map = new TreeMap<>();
    map.put("key1", "value1");
    map.put("stdout", "out1");
    ReportEntry reportEntry = ReportEntry.from(map);
    testContext.publishReportEntry(reportEntry)
      .finishWithFailure(multipleFailuresError);

    Assertions.assertEquals("""
                              ##TC[enteredTheMatrix]
                              ##TC[rootName name='testClass' location='java:suite://testClass']
                              ##TC[testStarted id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='test1()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0' locationHint='java:test://com.intellij.junit5.testData.MyTestClass/test1' metainfo='']
                              ##TC[testStdOut id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='test1()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0' out='timestamp = ${timestamp}, key1 = value1, stdout = out1|n']
                              ##TC[testFailed id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='test1()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0' message='message1|nComparison Failure: ' expected='expected1' actual='actual1' details='TRACE']
                              ##TC[testFailed id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='test1()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0' message='message2|nComparison Failure: ' expected='expected2' actual='actual2' details='TRACE']
                              ##TC[testFailed id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='test1()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0' message='2 errors (2 failures)|n\torg.opentest4j.AssertionFailedError: message1|n\torg.opentest4j.AssertionFailedError: message2' details='TRACE']
                              ##TC[testFinished id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='test1()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0']
                              """.replace("${timestamp}", reportEntry.getTimestamp().toString()), builder.getNormalizedTestOutput());
  }

  @Test
  void testsWithExplicitTestSources() {
    JUnit5TestRunnerBuilder builder = new JUnit5TestRunnerBuilder();

    List<JUnit5TestRunnerBuilder.TestDescriptorContext> tests = List.of(
      builder.withTestDescriptor(MyTestClass.class, "test1", "test1 display name",
                                 ClassSource.from(MyTestClass.class, FilePosition.from(111, 222))),
      builder.withTestDescriptor(MyTestClass.class, "test2", "test2 display name", FileSource.from(
        new File("/directory/test2.java"), FilePosition.from(12, 13))),
      builder.withTestDescriptor(MyTestClass.class, "test3", "test3 display name", MethodSource.from(
        "MyTestClass", "test4Method", "java.lang.String,java.util.List")),
      builder.withTestDescriptor(MyTestClass.class, "test4", "test4 display name", CompositeTestSource.from(List.of(
        ClassSource.from(JUnit5EventsTest.class, FilePosition.from(123, 456)),
        ClassSource.from(MyTestClass.class, FilePosition.from(3, 4))
      )))
    );

    builder.buildTestPlan().execute();
    for (JUnit5TestRunnerBuilder.TestDescriptorContext test : tests) {
      test.startTestOnly();
    }

    Assertions.assertEquals(
      """
        ##TC[enteredTheMatrix]
        ##TC[testStarted id='|[engine:engine|]/|[class:testClass|]/|[method:test1|]' name='test1 display name' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:test1|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' locationHint='java:suite://com.intellij.junit5.testData.MyTestClass' metainfo='110:221']
        ##TC[testStarted id='|[engine:engine|]/|[class:testClass|]/|[method:test2|]' name='test2 display name' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:test2|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' locationHint='file:///directory/test2.java:12']
        ##TC[testStarted id='|[engine:engine|]/|[class:testClass|]/|[method:test3|]' name='test3 display name' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:test3|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' locationHint='java:test://MyTestClass/test4Method' metainfo='java.lang.String,java.util.List']
        ##TC[testStarted id='|[engine:engine|]/|[class:testClass|]/|[method:test4|]' name='test4 display name' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:test4|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' locationHint='java:suite://com.intellij.junit5.JUnit5EventsTest']
        """, builder.getNormalizedTestOutput());
  }

  @Test
  void containerFailure() throws Exception {
    JUnit5TestRunnerBuilder builder = new JUnit5TestRunnerBuilder();
    JUnit5TestRunnerBuilder.TestDescriptorContext testContext = builder
      .withRootName("testMethod")
      .withPresentableName("testMethod")
      .withSendTree()
      .withTestFactory(MyTestClass.class, "brokenStream");

    builder.buildTestPlan().execute();

    testContext.startTestOnly().finishWithFailure(new IllegalStateException());

    Assertions.assertEquals("""
                              ##TC[enteredTheMatrix]
                              ##TC[suiteTreeStarted id='|[engine:engine|]/|[class:testClass|]' name='MyTestClass' nodeId='|[engine:engine|]/|[class:testClass|]' parentNodeId='0' locationHint='java:suite://com.intellij.junit5.testData.MyTestClass']
                              ##TC[suiteTreeEnded id='|[engine:engine|]/|[class:testClass|]' name='MyTestClass' nodeId='|[engine:engine|]/|[class:testClass|]' parentNodeId='0']
                              ##TC[treeEnded]
                              ##TC[rootName name='testMethod' location='java:suite://testMethod']
                              ##TC[testStarted id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='Class Configuration' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' locationHint='java:test://com.intellij.junit5.testData.MyTestClass/brokenStream' metainfo='']
                              ##TC[testFailed id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='Class Configuration' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' error='true' message='' details='TRACE']
                              ##TC[testFinished id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='Class Configuration' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]']
                              """, builder.getNormalizedTestOutput());
  }

  @Test
  void nestedEngines() {
    JUnit5TestRunnerBuilder builder = new JUnit5TestRunnerBuilder();
    builder.withSendTree()
      .withNestedEngine("secondEngine", MyTestClass.class)
      .buildTestPlan()
      .execute();

    TestDescriptor theSameDescriptor = builder.getNestedClassDescriptor();
    TestIdentifier testIdentifier = TestIdentifier.from(theSameDescriptor);

    builder.getExecutionListener().executionStarted(testIdentifier);
    builder.getExecutionListener().executionFinished(testIdentifier, TestExecutionResult.successful());

    Assertions.assertEquals("""
                              ##TC[enteredTheMatrix]
                              ##TC[suiteTreeStarted id='|[engine:engine|]/|[suite:suiteClass|]' name='MyTestClass' nodeId='|[engine:engine|]/|[suite:suiteClass|]' parentNodeId='0' locationHint='java:suite://com.intellij.junit5.testData.MyTestClass']
                              ##TC[suiteTreeStarted id='|[engine:secondEngine|]/|[class:testClass|]' name='MyTestClass' nodeId='|[engine:secondEngine|]/|[class:testClass|]' parentNodeId='|[engine:engine|]/|[suite:suiteClass|]' locationHint='java:suite://com.intellij.junit5.testData.MyTestClass']
                              ##TC[suiteTreeEnded id='|[engine:secondEngine|]/|[class:testClass|]' name='MyTestClass' nodeId='|[engine:secondEngine|]/|[class:testClass|]' parentNodeId='|[engine:engine|]/|[suite:suiteClass|]']
                              ##TC[suiteTreeEnded id='|[engine:engine|]/|[suite:suiteClass|]' name='MyTestClass' nodeId='|[engine:engine|]/|[suite:suiteClass|]' parentNodeId='0']
                              ##TC[treeEnded]
                              ##TC[testSuiteStarted id='|[engine:secondEngine|]/|[class:testClass|]' name='MyTestClass' nodeId='|[engine:secondEngine|]/|[class:testClass|]' parentNodeId='|[engine:engine|]/|[suite:suiteClass|]' locationHint='java:suite://com.intellij.junit5.testData.MyTestClass']
                              ##TC[testSuiteFinished id='|[engine:secondEngine|]/|[class:testClass|]' name='MyTestClass' nodeId='|[engine:secondEngine|]/|[class:testClass|]' parentNodeId='|[engine:engine|]/|[suite:suiteClass|]']
                              """, builder.getNormalizedTestOutput());
  }

  @Test
  void containerDisabled() throws Exception {
    JUnit5TestRunnerBuilder builder = new JUnit5TestRunnerBuilder();
    JUnit5TestRunnerBuilder.TestDescriptorContext testContext = builder
      .withSendTree()
      .withTestFactory(MyTestClass.class, "brokenStream");

    builder.buildTestPlan().execute();
    testContext.startTestOnly().finishAborted();

    Assertions.assertEquals("""
                              ##TC[enteredTheMatrix]
                              ##TC[suiteTreeStarted id='|[engine:engine|]/|[class:testClass|]' name='MyTestClass' nodeId='|[engine:engine|]/|[class:testClass|]' parentNodeId='0' locationHint='java:suite://com.intellij.junit5.testData.MyTestClass']
                              ##TC[suiteTreeStarted id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='brokenStream()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' locationHint='java:test://com.intellij.junit5.testData.MyTestClass/brokenStream' metainfo='']
                              ##TC[suiteTreeEnded id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='brokenStream()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]']
                              ##TC[suiteTreeEnded id='|[engine:engine|]/|[class:testClass|]' name='MyTestClass' nodeId='|[engine:engine|]/|[class:testClass|]' parentNodeId='0']
                              ##TC[treeEnded]
                              ##TC[testSuiteStarted id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='brokenStream()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' locationHint='java:test://com.intellij.junit5.testData.MyTestClass/brokenStream' metainfo='']
                              ##TC[testIgnored id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='brokenStream()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]']
                              ##TC[testSuiteFinished id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='brokenStream()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]']
                              """, builder.getNormalizedTestOutput());
  }

  @Test
  void singleMethodTestOneFromMyTest() {
    var request =
      new JUnit5TestRunnerHelper().buildRequest(new String[]{"com.intellij.junit5.testData.InitStaticField$MyTest,testOne"}, new String[1], null);
    var selectors = request.getSelectorsByType(DiscoverySelector.class);
    Assertions.assertFalse(selectors.isEmpty(), "Selectors should not be empty");

    var selector = selectors.get(0);
    Assertions.assertInstanceOf(ClassSelector.class, selector);
    Assertions.assertEquals("com.intellij.junit5.testData.InitStaticField$MyTest", ((ClassSelector)selector).getClassName());
  }

  @Test
  void testEscaping() throws NoSuchMethodException {
    JUnit5TestRunnerBuilder builder = new JUnit5TestRunnerBuilder();
    JUnit5TestRunnerBuilder.TestDescriptorContext testContext = builder
      .withRootName("testClass")
      .withPresentableName("testClass")
      .withTestMethod(AnnotationsTestClass.class, "test1");

    builder.buildTestPlan().execute();
    testContext.startExecution().finish();

    Assertions.assertEquals("""
                              ##TC[enteredTheMatrix]
                              ##TC[rootName name='testClass' location='java:suite://testClass']
                              ##TC[testStarted id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='|[test|'s method|]' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0' locationHint='java:test://com.intellij.junit5.testData.AnnotationsTestClass/test1' metainfo='']
                              ##TC[testFinished id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='|[test|'s method|]' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0']
                              """, builder.getNormalizedTestOutput());
  }
}
