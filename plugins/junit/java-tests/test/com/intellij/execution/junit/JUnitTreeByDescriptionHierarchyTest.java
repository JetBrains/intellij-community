// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.junit4.JUnit4TestListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.DebugUtil;
import org.junit.Assert;
import org.junit.ComparisonFailure;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runners.model.TestClass;
import org.opentest4j.MultipleFailuresError;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class JUnitTreeByDescriptionHierarchyTest {
  @Test
  public void testEmptySuite() {
    doTest(Description.createSuiteDescription("empty suite"), """
      ##teamcity[enteredTheMatrix]
      ##teamcity[suiteTreeStarted name='empty suite' locationHint='java:suite://empty suite']
      ##teamcity[suiteTreeEnded name='empty suite']
      ##teamcity[treeEnded]
      """);
  }

  @Test
  public void test2Parameterized() throws Exception {
    final Description root = Description.createSuiteDescription("root");
    final ArrayList<Description> tests = new ArrayList<>();
    for (String className : new String[]{"a.TestA", "a.TestB"}) {
      final Description aTestClass = Description.createSuiteDescription(className);
      root.addChild(aTestClass);
      attachParameterizedTests(className, aTestClass, tests);
    }
    doTest(root, tests,
           """
             ##teamcity[enteredTheMatrix]
             ##teamcity[suiteTreeStarted name='TestA' locationHint='java:suite://a.TestA']
             ##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://a.TestA.|[0|]']
             ##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://a.TestA/testName|[0|]']
             ##teamcity[suiteTreeEnded name='|[0|]']
             ##teamcity[suiteTreeStarted name='|[1|]' locationHint='java:suite://a.TestA.|[1|]']
             ##teamcity[suiteTreeNode name='testName|[1|]' locationHint='java:test://a.TestA/testName|[1|]']
             ##teamcity[suiteTreeEnded name='|[1|]']
             ##teamcity[suiteTreeEnded name='TestA']
             ##teamcity[suiteTreeStarted name='TestB' locationHint='java:suite://a.TestB']
             ##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://a.TestB.|[0|]']
             ##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://a.TestB/testName|[0|]']
             ##teamcity[suiteTreeEnded name='|[0|]']
             ##teamcity[suiteTreeStarted name='|[1|]' locationHint='java:suite://a.TestB.|[1|]']
             ##teamcity[suiteTreeNode name='testName|[1|]' locationHint='java:test://a.TestB/testName|[1|]']
             ##teamcity[suiteTreeEnded name='|[1|]']
             ##teamcity[suiteTreeEnded name='TestB']
             ##teamcity[treeEnded]
             """,


           """
             ##teamcity[rootName name = 'root' location = 'java:suite://root']
             ##teamcity[testSuiteStarted name='TestA' locationHint='java:suite://a.TestA']
             ##teamcity[testSuiteStarted name='|[0|]' locationHint='java:suite://a.TestA.|[0|]']
             ##teamcity[testStarted name='testName|[0|]' locationHint='java:test://a.TestA/testName|[0|]']
             ##teamcity[testFinished name='testName|[0|]']
             ##teamcity[testSuiteFinished name='|[0|]']
             ##teamcity[testSuiteStarted name='|[1|]' locationHint='java:suite://a.TestA.|[1|]']
             ##teamcity[testStarted name='testName|[1|]' locationHint='java:test://a.TestA/testName|[1|]']
             ##teamcity[testFinished name='testName|[1|]']
             ##teamcity[testSuiteFinished name='|[1|]']
             ##teamcity[testSuiteFinished name='TestA']
             ##teamcity[testSuiteStarted name='TestB' locationHint='java:suite://a.TestB']
             ##teamcity[testSuiteStarted name='|[0|]' locationHint='java:suite://a.TestB.|[0|]']
             ##teamcity[testStarted name='testName|[0|]' locationHint='java:test://a.TestB/testName|[0|]']
             ##teamcity[testFinished name='testName|[0|]']
             ##teamcity[testSuiteFinished name='|[0|]']
             ##teamcity[testSuiteStarted name='|[1|]' locationHint='java:suite://a.TestB.|[1|]']
             ##teamcity[testStarted name='testName|[1|]' locationHint='java:test://a.TestB/testName|[1|]']
             ##teamcity[testFinished name='testName|[1|]']
             ##teamcity[testSuiteFinished name='|[1|]']
             ##teamcity[testSuiteFinished name='TestB']
             """);
  }

  @Test
  public void testClassWithMethodsWithoutSendTreeBefore() throws Exception {
    Description root = Description.createSuiteDescription("ATest");
    List<Description> tests = new ArrayList<>();
    tests.add(Description.createTestDescription("ATest", "test1"));
    tests.add(Description.createTestDescription("ATest", "test2"));

    for (Description test : tests) {
      root.addChild(test);
    }

    final StringBuffer buf = new StringBuffer();
    JUnit4TestListener sender = createListener(buf);

    sender.testRunStarted(root);
    for (Description test : tests) {
      sender.testStarted(test);
      sender.testFinished(test);
    }
    sender.testRunFinished(new Result());
    Assert.assertEquals("output: " + buf, """
      ##teamcity[enteredTheMatrix]
      ##teamcity[testSuiteStarted name='ATest' locationHint='java:suite://ATest']
      ##teamcity[testStarted name='ATest.test1' locationHint='java:test://ATest/test1']
      ##teamcity[testFinished name='ATest.test1']
      ##teamcity[testStarted name='ATest.test2' locationHint='java:test://ATest/test2']
      ##teamcity[testFinished name='ATest.test2']
      ##teamcity[testSuiteFinished name='ATest']
      """, StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testSameShortNames() throws Exception {
    final Description rootDescription = Description.createSuiteDescription("root");
    final ArrayList<Description> tests = new ArrayList<>();
    for (String className : new String[]{"a.MyTest", "b.MyTest"}) {
      final Description aTestClass = Description.createSuiteDescription(className);
      rootDescription.addChild(aTestClass);
      final Description testDescription = Description.createTestDescription(className, "testMe");
      tests.add(testDescription);
      aTestClass.addChild(testDescription);
    }
    doTest(rootDescription, tests, """
             ##teamcity[enteredTheMatrix]
             ##teamcity[suiteTreeStarted name='MyTest' locationHint='java:suite://a.MyTest']
             ##teamcity[suiteTreeNode name='MyTest.testMe' locationHint='java:test://a.MyTest/testMe']
             ##teamcity[suiteTreeEnded name='MyTest']
             ##teamcity[suiteTreeStarted name='MyTest' locationHint='java:suite://b.MyTest']
             ##teamcity[suiteTreeNode name='MyTest.testMe' locationHint='java:test://b.MyTest/testMe']
             ##teamcity[suiteTreeEnded name='MyTest']
             ##teamcity[treeEnded]
             """,
           """
             ##teamcity[rootName name = 'root' location = 'java:suite://root']
             ##teamcity[testSuiteStarted name='MyTest' locationHint='java:suite://a.MyTest']
             ##teamcity[testStarted name='MyTest.testMe' locationHint='java:test://a.MyTest/testMe']
             ##teamcity[testFinished name='MyTest.testMe']
             ##teamcity[testSuiteFinished name='MyTest']
             ##teamcity[testSuiteStarted name='MyTest' locationHint='java:suite://b.MyTest']
             ##teamcity[testStarted name='MyTest.testMe' locationHint='java:test://b.MyTest/testMe']
             ##teamcity[testFinished name='MyTest.testMe']
             ##teamcity[testSuiteFinished name='MyTest']
             """);
  }

  @Test
  public void testSingleParameterizedClass() throws Exception {
    final String className = "a.TestA";
    final Description aTestClassDescription = Description.createSuiteDescription(className);
    final ArrayList<Description> tests = new ArrayList<>();
    attachParameterizedTests(className, aTestClassDescription, tests);
    doTest(aTestClassDescription, tests,
           //tree
           """
             ##teamcity[enteredTheMatrix]
             ##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://a.TestA.|[0|]']
             ##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://a.TestA/testName|[0|]']
             ##teamcity[suiteTreeEnded name='|[0|]']
             ##teamcity[suiteTreeStarted name='|[1|]' locationHint='java:suite://a.TestA.|[1|]']
             ##teamcity[suiteTreeNode name='testName|[1|]' locationHint='java:test://a.TestA/testName|[1|]']
             ##teamcity[suiteTreeEnded name='|[1|]']
             ##teamcity[treeEnded]
             """,
           //start
           """
             ##teamcity[rootName name = 'TestA' comment = 'a' location = 'java:suite://a.TestA']
             ##teamcity[testSuiteStarted name='|[0|]' locationHint='java:suite://a.TestA.|[0|]']
             ##teamcity[testStarted name='testName|[0|]' locationHint='java:test://a.TestA/testName|[0|]']
             ##teamcity[testFinished name='testName|[0|]']
             ##teamcity[testSuiteFinished name='|[0|]']
             ##teamcity[testSuiteStarted name='|[1|]' locationHint='java:suite://a.TestA.|[1|]']
             ##teamcity[testStarted name='testName|[1|]' locationHint='java:test://a.TestA/testName|[1|]']
             ##teamcity[testFinished name='testName|[1|]']
             ##teamcity[testSuiteFinished name='|[1|]']
             """);
  }

  @Test
  public void testParameterizedClassWithSameParameters() throws Exception {
    final String className = "a.TestA";
    final Description aTestClassDescription = Description.createSuiteDescription(className);
    final ArrayList<Description> tests = new ArrayList<>();
    for (String paramName : new String[]{"[0]", "[0]"}) {
      final Description param1 = Description.createSuiteDescription(paramName);
      aTestClassDescription.addChild(param1);
      final Description testDescription = Description.createTestDescription(className, "testName" + paramName);
      tests.add(testDescription);
      param1.addChild(testDescription);
    }
    doTest(aTestClassDescription, tests,
           //tree
           """
             ##teamcity[enteredTheMatrix]
             ##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://a.TestA.|[0|]']
             ##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://a.TestA/testName|[0|]']
             ##teamcity[suiteTreeEnded name='|[0|]']
             ##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://a.TestA.|[0|]']
             ##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://a.TestA/testName|[0|]']
             ##teamcity[suiteTreeEnded name='|[0|]']
             ##teamcity[treeEnded]
             """,
           //start
           """
             ##teamcity[rootName name = 'TestA' comment = 'a' location = 'java:suite://a.TestA']
             ##teamcity[testSuiteStarted name='|[0|]' locationHint='java:suite://a.TestA.|[0|]']
             ##teamcity[testStarted name='testName|[0|]' locationHint='java:test://a.TestA/testName|[0|]']
             ##teamcity[testFinished name='testName|[0|]']
             ##teamcity[testSuiteFinished name='|[0|]']
             ##teamcity[testSuiteStarted name='|[0|]' locationHint='java:suite://a.TestA.|[0|]']
             ##teamcity[testStarted name='testName|[0|]' locationHint='java:test://a.TestA/testName|[0|]']
             ##teamcity[testFinished name='testName|[0|]']
             ##teamcity[testSuiteFinished name='|[0|]']
             """);
  }

  @Test
  public void testParameterizedClassWithParamsWithDots() throws Exception {
    final String className = "a.TestA";
    final Description aTestClassDescription = Description.createSuiteDescription(className);
    final ArrayList<Description> tests = new ArrayList<>();
    for (String paramName : new String[]{"[0: with - 1.1]", "[1: with - 2.1]"}) {
      final Description param1 = Description.createSuiteDescription(paramName);
      aTestClassDescription.addChild(param1);
      final Description testDescription = Description.createTestDescription(className, "testName" + paramName);
      tests.add(testDescription);
      param1.addChild(testDescription);
    }
    doTest(aTestClassDescription, tests,
           //tree
           """
             ##teamcity[enteredTheMatrix]
             ##teamcity[suiteTreeStarted name='|[0: with - 1.1|]' locationHint='java:suite://a.TestA.|[0: with - 1.1|]']
             ##teamcity[suiteTreeNode name='testName|[0: with - 1.1|]' locationHint='java:test://a.TestA/testName|[0: with - 1.1|]']
             ##teamcity[suiteTreeEnded name='|[0: with - 1.1|]']
             ##teamcity[suiteTreeStarted name='|[1: with - 2.1|]' locationHint='java:suite://a.TestA.|[1: with - 2.1|]']
             ##teamcity[suiteTreeNode name='testName|[1: with - 2.1|]' locationHint='java:test://a.TestA/testName|[1: with - 2.1|]']
             ##teamcity[suiteTreeEnded name='|[1: with - 2.1|]']
             ##teamcity[treeEnded]
             """,
           //start
           """
             ##teamcity[rootName name = 'TestA' comment = 'a' location = 'java:suite://a.TestA']
             ##teamcity[testSuiteStarted name='|[0: with - 1.1|]' locationHint='java:suite://a.TestA.|[0: with - 1.1|]']
             ##teamcity[testStarted name='testName|[0: with - 1.1|]' locationHint='java:test://a.TestA/testName|[0: with - 1.1|]']
             ##teamcity[testFinished name='testName|[0: with - 1.1|]']
             ##teamcity[testSuiteFinished name='|[0: with - 1.1|]']
             ##teamcity[testSuiteStarted name='|[1: with - 2.1|]' locationHint='java:suite://a.TestA.|[1: with - 2.1|]']
             ##teamcity[testStarted name='testName|[1: with - 2.1|]' locationHint='java:test://a.TestA/testName|[1: with - 2.1|]']
             ##teamcity[testFinished name='testName|[1: with - 2.1|]']
             ##teamcity[testSuiteFinished name='|[1: with - 2.1|]']
             """);
  }

  @Test
  public void test2SuitesWithTheSameTest() throws Exception {
    final Description root = Description.createSuiteDescription("root");
    final String className = "ATest";
    final String methodName = "test1";
    final List<Description> tests = new ArrayList<>();
    for( String suiteName : new String[] {"ASuite1", "ASuite2"}) {
      final Description aSuite = Description.createSuiteDescription(suiteName);
      root.addChild(aSuite);
      final Description aTest = Description.createSuiteDescription(className);
      aSuite.addChild(aTest);
      final Description testDescription = Description.createTestDescription(className, methodName);
      tests.add(testDescription);
      aTest.addChild(testDescription);
    }

    doTest(root, tests,
           //expected tree
           """
             ##teamcity[enteredTheMatrix]
             ##teamcity[suiteTreeStarted name='ASuite1' locationHint='java:suite://ASuite1']
             ##teamcity[suiteTreeStarted name='ATest' locationHint='java:suite://ATest']
             ##teamcity[suiteTreeNode name='ATest.test1' locationHint='java:test://ATest/test1']
             ##teamcity[suiteTreeEnded name='ATest']
             ##teamcity[suiteTreeEnded name='ASuite1']
             ##teamcity[suiteTreeStarted name='ASuite2' locationHint='java:suite://ASuite2']
             ##teamcity[suiteTreeStarted name='ATest' locationHint='java:suite://ATest']
             ##teamcity[suiteTreeNode name='ATest.test1' locationHint='java:test://ATest/test1']
             ##teamcity[suiteTreeEnded name='ATest']
             ##teamcity[suiteTreeEnded name='ASuite2']
             ##teamcity[treeEnded]
             """,

           //started
           """
             ##teamcity[rootName name = 'root' location = 'java:suite://root']
             ##teamcity[testSuiteStarted name='ASuite1' locationHint='java:suite://ASuite1']
             ##teamcity[testSuiteStarted name='ATest' locationHint='java:suite://ATest']
             ##teamcity[testStarted name='ATest.test1' locationHint='java:test://ATest/test1']
             ##teamcity[testFinished name='ATest.test1']
             ##teamcity[testSuiteFinished name='ATest']
             ##teamcity[testSuiteFinished name='ASuite1']
             ##teamcity[testSuiteStarted name='ASuite2' locationHint='java:suite://ASuite2']
             ##teamcity[testSuiteStarted name='ATest' locationHint='java:suite://ATest']
             ##teamcity[testStarted name='ATest.test1' locationHint='java:test://ATest/test1']
             ##teamcity[testFinished name='ATest.test1']
             ##teamcity[testSuiteFinished name='ATest']
             ##teamcity[testSuiteFinished name='ASuite2']
             """);
  }

  private static void doTest(Description root, List<Description> tests, String expectedTree, String expectedStart) throws Exception {
    final StringBuffer buf = new StringBuffer();
    final JUnit4TestListener sender = createListener(buf);
    sender.sendTree(root);

    Assert.assertEquals("output: " + buf, expectedTree, StringUtil.convertLineSeparators(buf.toString()));

    buf.setLength(0);

    sender.testRunStarted(root);
    for (Description test : tests) {
      sender.testStarted(test);
      sender.testFinished(test);
    }
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, expectedStart, StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testSetupClassAssumptionFailure() throws Exception {
    final Description root = Description.createSuiteDescription("root");
    final Description testA = Description.createSuiteDescription("TestA");
    root.addChild(testA);
    final Description testName = Description.createTestDescription("TestA", "testName");
    testA.addChild(testName);

    final Description testB = Description.createSuiteDescription("TestB");
    root.addChild(testB);
    final Description testNameB = Description.createTestDescription("TestB", "testNameB");
    testB.addChild(testNameB);

    final StringBuffer buf = new StringBuffer();
    final JUnit4TestListener sender = createListener(buf);
    sender.sendTree(root);

    Assert.assertEquals("output: " + buf, """
      ##teamcity[enteredTheMatrix]
      ##teamcity[suiteTreeStarted name='TestA' locationHint='java:suite://TestA']
      ##teamcity[suiteTreeNode name='TestA.testName' locationHint='java:test://TestA/testName']
      ##teamcity[suiteTreeEnded name='TestA']
      ##teamcity[suiteTreeStarted name='TestB' locationHint='java:suite://TestB']
      ##teamcity[suiteTreeNode name='TestB.testNameB' locationHint='java:test://TestB/testNameB']
      ##teamcity[suiteTreeEnded name='TestB']
      ##teamcity[treeEnded]
      """, StringUtil.convertLineSeparators(buf.toString()));

    buf.setLength(0);

    sender.testRunStarted(root);
    final Exception exception = new Exception();
    exception.setStackTrace(new StackTraceElement[0]);
    sender.testAssumptionFailure(new Failure(testA, exception));
    sender.testAssumptionFailure(new Failure(testB, exception));
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, """
      ##teamcity[rootName name = 'root' location = 'java:suite://root']
      ##teamcity[testSuiteStarted name='TestA' locationHint='java:suite://TestA']
      ##teamcity[testStarted name='TestA.testName' locationHint='java:test://TestA/testName']
      ##teamcity[testIgnored name='TestA.testName' error='true' message='' details='java.lang.Exception|n']
      ##teamcity[testFinished name='TestA.testName']
      ##teamcity[testSuiteFinished name='TestA']
      ##teamcity[testSuiteStarted name='TestB' locationHint='java:suite://TestB']
      ##teamcity[testStarted name='TestB.testNameB' locationHint='java:test://TestB/testNameB']
      ##teamcity[testIgnored name='TestB.testNameB' error='true' message='' details='java.lang.Exception|n']
      ##teamcity[testFinished name='TestB.testNameB']
      ##teamcity[testSuiteFinished name='TestB']
      """, StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testSetupClassFailure() throws Exception {
    final Description root = Description.createSuiteDescription("root");
    final Description testA = Description.createSuiteDescription("TestA");
    root.addChild(testA);
    final Description testName = Description.createTestDescription("TestA", "testName");
    testA.addChild(testName);

    final StringBuffer buf = new StringBuffer();
    final JUnit4TestListener sender = createListener(buf);
    sender.sendTree(root);

    Assert.assertEquals("output: " + buf, """
      ##teamcity[enteredTheMatrix]
      ##teamcity[suiteTreeStarted name='TestA' locationHint='java:suite://TestA']
      ##teamcity[suiteTreeNode name='TestA.testName' locationHint='java:test://TestA/testName']
      ##teamcity[suiteTreeEnded name='TestA']
      ##teamcity[treeEnded]
      """, StringUtil.convertLineSeparators(buf.toString()));

    buf.setLength(0);

    sender.testRunStarted(testA);
    final Exception exception = new Exception();
    exception.setStackTrace(new StackTraceElement[0]);
    sender.testFailure(new Failure(testA, exception));
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, """
      ##teamcity[rootName name = 'root' location = 'java:suite://root']
      ##teamcity[testStarted name='Class Configuration' locationHint='java:suite://TestA' ]
      ##teamcity[testFailed name='Class Configuration' error='true' message='' details='java.lang.Exception|n']
      ##teamcity[testFinished name='Class Configuration']
      ##teamcity[testSuiteStarted name='TestA' locationHint='java:suite://TestA']
      ##teamcity[testStarted name='TestA.testName' locationHint='java:test://TestA/testName']
      ##teamcity[testIgnored name='TestA.testName']
      ##teamcity[testFinished name='TestA.testName']
      ##teamcity[testSuiteFinished name='TestA']
      """, StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testLongOutputPreservesTestName() {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      buf.append(DebugUtil.currentStackTrace());
    }

    final StringBuffer output = new StringBuffer();
    final JUnit4TestListener sender = createListener(output);

    final Description description = Description.createTestDescription("A", "a");
    sender.testFailure(new Failure(description, new ComparisonFailure(buf.toString(), buf.toString(), "diff" + buf)));

    final String startMessage = "##teamcity[enteredTheMatrix]\n" +

                                "##teamcity[testFailed name='A.a' ";
    Assert.assertEquals(startMessage, StringUtil.convertLineSeparators(output.toString()).substring(0, startMessage.length()));
  }

  @Test
  public void testMultipleFailures() {
    final Description root = Description.createSuiteDescription("root");
    Description testA = Description.createTestDescription("TestA", "test1");
    root.addChild(testA);

    final StringBuffer buf = new StringBuffer();
    final JUnit4TestListener sender = createListener(buf);
    sender.testRunStarted(root);
    sender.testStarted(testA);

    final Exception ex1 = new Exception();
    ex1.setStackTrace(new StackTraceElement[0]);
    final Exception ex2 = new Exception();
    ex2.setStackTrace(new StackTraceElement[0]);
    final Throwable exception = new MultipleFailuresError("Multiple errors", List.of(ex1, ex2));
    
    exception.setStackTrace(new StackTraceElement[0]);
    sender.testFailure(new Failure(testA, exception));
    sender.testFinished(testA);
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, """
      ##teamcity[enteredTheMatrix]
      ##teamcity[testSuiteStarted name='TestA' locationHint='java:suite://TestA']
      ##teamcity[testStarted name='TestA.test1' locationHint='java:test://TestA/test1']
      ##teamcity[testFailed name='TestA.test1' error='true' message='' details='java.lang.Exception|n']
      ##teamcity[testFailed name='TestA.test1' error='true' message='' details='java.lang.Exception|n']
      ##teamcity[testFinished name='TestA.test1']
      ##teamcity[testSuiteFinished name='TestA']
      """, StringUtil.convertLineSeparators(buf.toString()));
  
  }

  @Test
  public void testParallelExecution() throws Exception {
    final Description root = Description.createSuiteDescription("root");
    Description testA = Description.createTestDescription("TestA", "test1");
    root.addChild(testA);

    Description testB = Description.createTestDescription("TestB", "test2");
    root.addChild(testB);

    final StringBuffer buf = new StringBuffer();
    final JUnit4TestListener sender = createListener(buf);
    sender.sendTree(root);

    Assert.assertEquals("output: " + buf, """
      ##teamcity[enteredTheMatrix]
      ##teamcity[suiteTreeNode name='TestA.test1' locationHint='java:test://TestA/test1']
      ##teamcity[suiteTreeNode name='TestB.test2' locationHint='java:test://TestB/test2']
      ##teamcity[treeEnded]
      """, StringUtil.convertLineSeparators(buf.toString()));

    buf.setLength(0);

    sender.testRunStarted(root);
    sender.testStarted(testA);

    sender.testStarted(testB);
    sender.testFinished(testB);

    final Exception exception = new Exception();
    exception.setStackTrace(new StackTraceElement[0]);
    sender.testFailure(new Failure(testA, exception));
    sender.testFinished(testA);
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, """
      ##teamcity[rootName name = 'root' location = 'java:suite://root']
      ##teamcity[testSuiteStarted name='TestA' locationHint='java:suite://TestA']
      ##teamcity[testStarted name='TestA.test1' locationHint='java:test://TestA/test1']
      ##teamcity[testFailed name='TestA.test1' error='true' message='' details='java.lang.Exception|n']
      ##teamcity[testFinished name='TestA.test1']
      ##teamcity[testSuiteFinished name='TestA']
      ##teamcity[testSuiteStarted name='TestB' locationHint='java:suite://TestB']
      ##teamcity[testStarted name='TestB.test2' locationHint='java:test://TestB/test2']
      ##teamcity[testFinished name='TestB.test2']
      ##teamcity[testSuiteFinished name='TestB']
      """, StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testTearDownClassFailure() throws Exception {
    final Description root = Description.createSuiteDescription("root");
    final Description testA = Description.createSuiteDescription("TestA");
    root.addChild(testA);
    final Description testName = Description.createTestDescription("TestA", "testName");
    testA.addChild(testName);

    final StringBuffer buf = new StringBuffer();
    final JUnit4TestListener sender = createListener(buf);
    sender.sendTree(root);

    Assert.assertEquals("output: " + buf, """
      ##teamcity[enteredTheMatrix]
      ##teamcity[suiteTreeStarted name='TestA' locationHint='java:suite://TestA']
      ##teamcity[suiteTreeNode name='TestA.testName' locationHint='java:test://TestA/testName']
      ##teamcity[suiteTreeEnded name='TestA']
      ##teamcity[treeEnded]
      """, StringUtil.convertLineSeparators(buf.toString()));

    buf.setLength(0);

    sender.testRunStarted(testA);
    final Exception exception = new Exception();
    exception.setStackTrace(new StackTraceElement[0]);
    sender.testStarted(testName);
    sender.testFinished(testName);
    sender.testFailure(new Failure(testA, exception));
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, """
      ##teamcity[rootName name = 'root' location = 'java:suite://root']
      ##teamcity[testSuiteStarted name='TestA' locationHint='java:suite://TestA']
      ##teamcity[testStarted name='TestA.testName' locationHint='java:test://TestA/testName']
      ##teamcity[testFinished name='TestA.testName']
      ##teamcity[testStarted name='Class Configuration' locationHint='java:suite://TestA' ]
      ##teamcity[testFailed name='Class Configuration' error='true' message='' details='java.lang.Exception|n']
      ##teamcity[testFinished name='Class Configuration']
      ##teamcity[testSuiteFinished name='TestA']
      """, StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testTearDownClassFailureSingleClass() throws Exception {
    final Description testA = Description.createSuiteDescription("TestA");
    final Description testName = Description.createTestDescription("TestA", "testName");
    testA.addChild(testName);

    final StringBuffer buf = new StringBuffer();
    final JUnit4TestListener sender = createListener(buf);
    sender.sendTree(testA);

    Assert.assertEquals("output: " + buf, """
      ##teamcity[enteredTheMatrix]
      ##teamcity[suiteTreeNode name='TestA.testName' locationHint='java:test://TestA/testName']
      ##teamcity[treeEnded]
      """, StringUtil.convertLineSeparators(buf.toString()));

    buf.setLength(0);

    sender.testRunStarted(testA);
    final Exception exception = new Exception();
    exception.setStackTrace(new StackTraceElement[0]);
    sender.testStarted(testName);
    sender.testFinished(testName);
    sender.testFailure(new Failure(testA, exception));
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, """
      ##teamcity[rootName name = 'TestA' location = 'java:suite://TestA']
      ##teamcity[testStarted name='TestA.testName' locationHint='java:test://TestA/testName']
      ##teamcity[testFinished name='TestA.testName']
      ##teamcity[testStarted name='Class Configuration' locationHint='java:suite://TestA' ]
      ##teamcity[testFailed name='Class Configuration' error='true' message='' details='java.lang.Exception|n']
      ##teamcity[testFinished name='Class Configuration']
      """, StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testSetupClassFailureForParameterizedClass() throws Exception {
    final Description root = Description.createSuiteDescription("root");
    final Description testA = Description.createSuiteDescription("TestA");
    root.addChild(testA);
    final Description paramDescription = Description.createSuiteDescription("param");
    testA.addChild(paramDescription);
    final Description testName = Description.createTestDescription("TestA", "testName");
    paramDescription.addChild(testName);

    final StringBuffer buf = new StringBuffer();
    final JUnit4TestListener sender = createListener(buf);
    sender.sendTree(root);

    Assert.assertEquals("output: " + buf, """
      ##teamcity[enteredTheMatrix]
      ##teamcity[suiteTreeStarted name='TestA' locationHint='java:suite://TestA']
      ##teamcity[suiteTreeStarted name='param' locationHint='java:suite://param']
      ##teamcity[suiteTreeNode name='TestA.testName' locationHint='java:test://TestA/testName']
      ##teamcity[suiteTreeEnded name='param']
      ##teamcity[suiteTreeEnded name='TestA']
      ##teamcity[treeEnded]
      """, StringUtil.convertLineSeparators(buf.toString()));

    buf.setLength(0);

    sender.testRunStarted(testA);
    final Exception exception = new Exception();
    exception.setStackTrace(new StackTraceElement[0]);
    sender.testAssumptionFailure(new Failure(testA, exception));
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, """
      ##teamcity[rootName name = 'root' location = 'java:suite://root']
      ##teamcity[testSuiteStarted name='TestA' locationHint='java:suite://TestA']
      ##teamcity[testSuiteStarted name='param' locationHint='java:suite://param']
      ##teamcity[testStarted name='TestA.testName' locationHint='java:test://TestA/testName']
      ##teamcity[testIgnored name='TestA.testName' error='true' message='' details='java.lang.Exception|n']
      ##teamcity[testFinished name='TestA.testName']
      ##teamcity[testSuiteFinished name='param']
      ##teamcity[testSuiteFinished name='TestA']
      """, StringUtil.convertLineSeparators(buf.toString()));
    buf.setLength(0);

    //testStarted and testFinished are called by the framework
    sender.testRunStarted(testA);
    sender.testAssumptionFailure(new Failure(testName, exception));
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, """
      ##teamcity[rootName name = 'root' location = 'java:suite://root']
      ##teamcity[testIgnored name='TestA.testName' error='true' message='' details='java.lang.Exception|n']
      """, StringUtil.convertLineSeparators(buf.toString()));

  }

  @Test
  public void testAssumptionFailures() throws Exception {
    Description root = Description.createSuiteDescription("root");
    for (int i = 0; i< 5; i++) {
      Description testClassDescription = Description.createSuiteDescription("Test" + i);
      root.addChild(testClassDescription);
      testClassDescription.addChild(Description.createTestDescription("Test" + i, "testName"));
    }

    final StringBuffer buf = new StringBuffer();
    final JUnit4TestListener sender = createListener(buf);
    sender.sendTree(root);

    Assert.assertEquals("output: " + buf, """
      ##teamcity[enteredTheMatrix]
      ##teamcity[suiteTreeStarted name='Test0' locationHint='java:suite://Test0']
      ##teamcity[suiteTreeNode name='Test0.testName' locationHint='java:test://Test0/testName']
      ##teamcity[suiteTreeEnded name='Test0']
      ##teamcity[suiteTreeStarted name='Test1' locationHint='java:suite://Test1']
      ##teamcity[suiteTreeNode name='Test1.testName' locationHint='java:test://Test1/testName']
      ##teamcity[suiteTreeEnded name='Test1']
      ##teamcity[suiteTreeStarted name='Test2' locationHint='java:suite://Test2']
      ##teamcity[suiteTreeNode name='Test2.testName' locationHint='java:test://Test2/testName']
      ##teamcity[suiteTreeEnded name='Test2']
      ##teamcity[suiteTreeStarted name='Test3' locationHint='java:suite://Test3']
      ##teamcity[suiteTreeNode name='Test3.testName' locationHint='java:test://Test3/testName']
      ##teamcity[suiteTreeEnded name='Test3']
      ##teamcity[suiteTreeStarted name='Test4' locationHint='java:suite://Test4']
      ##teamcity[suiteTreeNode name='Test4.testName' locationHint='java:test://Test4/testName']
      ##teamcity[suiteTreeEnded name='Test4']
      ##teamcity[treeEnded]
      """, StringUtil.convertLineSeparators(buf.toString()));

    buf.setLength(0);

    sender.testRunStarted(root);
    final Exception exception = new Exception();
    exception.setStackTrace(new StackTraceElement[0]);
    int idx = 0;
    for (Description description : root.getChildren()) {
      if (idx++ % 2 != 0) {
        sender.testAssumptionFailure(new Failure(description, exception));
      }
      else {
        for (Description testDescription : description.getChildren()) {
          sender.testStarted(testDescription);
          sender.testFinished(testDescription);
        }
      }
    }

    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, """
      ##teamcity[rootName name = 'root' location = 'java:suite://root']
      ##teamcity[testSuiteStarted name='Test0' locationHint='java:suite://Test0']
      ##teamcity[testStarted name='Test0.testName' locationHint='java:test://Test0/testName']
      ##teamcity[testFinished name='Test0.testName']
      ##teamcity[testSuiteFinished name='Test0']
      ##teamcity[testSuiteStarted name='Test1' locationHint='java:suite://Test1']
      ##teamcity[testStarted name='Test1.testName' locationHint='java:test://Test1/testName']
      ##teamcity[testIgnored name='Test1.testName' error='true' message='' details='java.lang.Exception|n']
      ##teamcity[testFinished name='Test1.testName']
      ##teamcity[testSuiteFinished name='Test1']
      ##teamcity[testSuiteStarted name='Test2' locationHint='java:suite://Test2']
      ##teamcity[testStarted name='Test2.testName' locationHint='java:test://Test2/testName']
      ##teamcity[testFinished name='Test2.testName']
      ##teamcity[testSuiteFinished name='Test2']
      ##teamcity[testSuiteStarted name='Test3' locationHint='java:suite://Test3']
      ##teamcity[testStarted name='Test3.testName' locationHint='java:test://Test3/testName']
      ##teamcity[testIgnored name='Test3.testName' error='true' message='' details='java.lang.Exception|n']
      ##teamcity[testFinished name='Test3.testName']
      ##teamcity[testSuiteFinished name='Test3']
      ##teamcity[testSuiteStarted name='Test4' locationHint='java:suite://Test4']
      ##teamcity[testStarted name='Test4.testName' locationHint='java:test://Test4/testName']
      ##teamcity[testFinished name='Test4.testName']
      ##teamcity[testSuiteFinished name='Test4']
      """, StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testSingleMethod() throws Exception {
    final Description rootDescription = Description.createTestDescription("TestA", "testName");
    doTest(rootDescription, Collections.singletonList(rootDescription),
           """
             ##teamcity[enteredTheMatrix]
             ##teamcity[suiteTreeNode name='TestA.testName' locationHint='java:test://TestA/testName']
             ##teamcity[treeEnded]
             """,
           """
             ##teamcity[rootName name = 'TestA' location = 'java:suite://TestA']
             ##teamcity[testStarted name='TestA.testName' locationHint='java:test://TestA/testName']
             ##teamcity[testFinished name='TestA.testName']
             """);
  }

  @Test
  public void testPackageWithoutDescriptionBefore() throws Exception {
    final Description root = Description.createSuiteDescription("root");
    final ArrayList<Description> tests = new ArrayList<>();
    for (String className : new String[]{"a.TestA", "a.TestB"}) {
      final Description aTestClass = Description.createSuiteDescription(className);
      root.addChild(aTestClass);
      final Description testDescription = Description.createTestDescription(className, "testName");
      aTestClass.addChild(testDescription);
      tests.add(testDescription);
    }

    final StringBuffer buf = new StringBuffer();
    final JUnit4TestListener sender = createListener(buf);

    sender.testRunStarted(root);
    for (Description test : tests) {
      sender.testStarted(test);
      sender.testFinished(test);
    }
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, """
      ##teamcity[enteredTheMatrix]
      ##teamcity[testSuiteStarted name='TestA' locationHint='java:suite://a.TestA']
      ##teamcity[testStarted name='TestA.testName' locationHint='java:test://a.TestA/testName']
      ##teamcity[testFinished name='TestA.testName']
      ##teamcity[testSuiteFinished name='TestA']
      ##teamcity[testSuiteStarted name='TestB' locationHint='java:suite://a.TestB']
      ##teamcity[testStarted name='TestB.testName' locationHint='java:test://a.TestB/testName']
      ##teamcity[testFinished name='TestB.testName']
      ##teamcity[testSuiteFinished name='TestB']
      """, StringUtil.convertLineSeparators(buf.toString()));
  }

  private static JUnit4TestListener createListener(final StringBuffer buf) {
    return new JUnit4TestListener(new PrintStream(new OutputStream() {
      @Override
      public void write(int b) {
        buf.append(new String(new byte[]{(byte)b}, StandardCharsets.UTF_8));
      }
    })) {
      @Override
      protected long currentTime() {
        return 0;
      }

      @Override
      protected String getTrace(Failure failure) {
        return StringUtil.convertLineSeparators(super.getTrace(failure));
      }
    };
  }

  @Test
  public void testParameterizedTestsUpsideDown() throws Exception {
    final Description aTestClass = Description.createSuiteDescription("ATest");
    final ArrayList<Description> tests = new ArrayList<>();
    final Description testMethod = Description.createSuiteDescription("testName");
    aTestClass.addChild(testMethod);
    for (String paramName : new String[]{"[0]", "[1]"}) {
      final Description testDescription = Description.createTestDescription("ATest", "testName" + paramName);
      tests.add(testDescription);
      testMethod.addChild(testDescription);
    }
    doTest(aTestClass, tests,
           """
             ##teamcity[enteredTheMatrix]
             ##teamcity[suiteTreeStarted name='testName' locationHint='java:suite://testName']
             ##teamcity[suiteTreeNode name='ATest.testName|[0|]' locationHint='java:test://ATest/testName|[0|]']
             ##teamcity[suiteTreeNode name='ATest.testName|[1|]' locationHint='java:test://ATest/testName|[1|]']
             ##teamcity[suiteTreeEnded name='testName']
             ##teamcity[treeEnded]
             """,


           """
             ##teamcity[rootName name = 'ATest' location = 'java:suite://ATest']
             ##teamcity[testSuiteStarted name='testName' locationHint='java:suite://testName']
             ##teamcity[testStarted name='ATest.testName|[0|]' locationHint='java:test://ATest/testName|[0|]']
             ##teamcity[testFinished name='ATest.testName|[0|]']
             ##teamcity[testStarted name='ATest.testName|[1|]' locationHint='java:test://ATest/testName|[1|]']
             ##teamcity[testFinished name='ATest.testName|[1|]']
             ##teamcity[testSuiteFinished name='testName']
             """);
  }

  @Test
  public void testSuiteAndParameterizedTestsInOnePackage() throws Exception {
    final Description root = Description.createSuiteDescription("root");
    final Description aTestClass = Description.createSuiteDescription("ATest");
    root.addChild(aTestClass);
    final ArrayList<Description> tests = new ArrayList<>();
    attachParameterizedTests("ATest", aTestClass, tests);
    final Description suiteDescription = Description.createSuiteDescription("suite");
    root.addChild(suiteDescription);
    final Description aTestClassWithJUnit3Test = Description.createSuiteDescription("ATest");
    suiteDescription.addChild(aTestClassWithJUnit3Test);
    final Description testDescription = Description.createTestDescription("ATest", "test");
    aTestClassWithJUnit3Test.addChild(testDescription);
    tests.add(testDescription);
    doTest(root, tests,
           """
             ##teamcity[enteredTheMatrix]
             ##teamcity[suiteTreeStarted name='ATest' locationHint='java:suite://ATest']
             ##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://ATest.|[0|]']
             ##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://ATest/testName|[0|]']
             ##teamcity[suiteTreeEnded name='|[0|]']
             ##teamcity[suiteTreeStarted name='|[1|]' locationHint='java:suite://ATest.|[1|]']
             ##teamcity[suiteTreeNode name='testName|[1|]' locationHint='java:test://ATest/testName|[1|]']
             ##teamcity[suiteTreeEnded name='|[1|]']
             ##teamcity[suiteTreeEnded name='ATest']
             ##teamcity[suiteTreeStarted name='suite' locationHint='java:suite://suite']
             ##teamcity[suiteTreeStarted name='ATest' locationHint='java:suite://ATest']
             ##teamcity[suiteTreeNode name='ATest.test' locationHint='java:test://ATest/test']
             ##teamcity[suiteTreeEnded name='ATest']
             ##teamcity[suiteTreeEnded name='suite']
             ##teamcity[treeEnded]
             """,

           //start
           """
             ##teamcity[rootName name = 'root' location = 'java:suite://root']
             ##teamcity[testSuiteStarted name='ATest' locationHint='java:suite://ATest']
             ##teamcity[testSuiteStarted name='|[0|]' locationHint='java:suite://ATest.|[0|]']
             ##teamcity[testStarted name='testName|[0|]' locationHint='java:test://ATest/testName|[0|]']
             ##teamcity[testFinished name='testName|[0|]']
             ##teamcity[testSuiteFinished name='|[0|]']
             ##teamcity[testSuiteStarted name='|[1|]' locationHint='java:suite://ATest.|[1|]']
             ##teamcity[testStarted name='testName|[1|]' locationHint='java:test://ATest/testName|[1|]']
             ##teamcity[testFinished name='testName|[1|]']
             ##teamcity[testSuiteFinished name='|[1|]']
             ##teamcity[testSuiteFinished name='ATest']
             ##teamcity[testSuiteStarted name='suite' locationHint='java:suite://suite']
             ##teamcity[testSuiteStarted name='ATest' locationHint='java:suite://ATest']
             ##teamcity[testStarted name='ATest.test' locationHint='java:test://ATest/test']
             ##teamcity[testFinished name='ATest.test']
             ##teamcity[testSuiteFinished name='ATest']
             ##teamcity[testSuiteFinished name='suite']
             """);
  }


  private static void attachParameterizedTests(String className, Description aTestClass, List<Description> tests) {
    for (String paramName : new String[]{"[0]", "[1]"}) {
      final Description param1 = Description.createSuiteDescription(paramName);
      aTestClass.addChild(param1);
      final Description testDescription = Description.createTestDescription(className, "testName" + paramName);
      tests.add(testDescription);
      param1.addChild(testDescription);
    }
  }

  private static void doTest(Description description, String expected) {
    final StringBuffer buf = new StringBuffer();
    createListener(buf).sendTree(description);

    Assert.assertEquals("output: " + buf, expected, StringUtil.convertLineSeparators(buf.toString()));
  }

  @Test
  public void testProcessEmptyTestCase() throws Exception {
    final Description description = Description.createSuiteDescription("TestA");
    final Description emptyDescription = Description.createTestDescription(JUnit4TestListener.EMPTY_SUITE_NAME, JUnit4TestListener.EMPTY_SUITE_WARNING);
    description.addChild(emptyDescription);
    doTest(description, Collections.singletonList(emptyDescription),
           """
             ##teamcity[enteredTheMatrix]
             ##teamcity[suiteTreeNode name='TestSuite$1.warning' locationHint='java:test://junit.framework.TestSuite$1/warning']
             ##teamcity[treeEnded]
             """,

           """
             ##teamcity[rootName name = 'TestA' location = 'java:suite://TestA']
             ##teamcity[testSuiteStarted name='TestSuite$1' locationHint='java:suite://junit.framework.TestSuite$1']
             ##teamcity[testStarted name='TestSuite$1.warning' locationHint='java:test://junit.framework.TestSuite$1/warning']
             ##teamcity[testFinished name='TestSuite$1.warning']
             ##teamcity[testSuiteFinished name='TestSuite$1']
             """);
  }

  @Test
  public void test2ClassesWithNoDescription() throws Exception {
    final Description root = Description.createSuiteDescription(new TestClass(null).getName());
    final Description testA = Description.createSuiteDescription("TestA");
    root.addChild(testA);
    final Description testNameA = Description.createTestDescription("TestA", "testName");
    testA.addChild(testNameA);

    final Description testB = Description.createSuiteDescription("TestB");
    root.addChild(testB);
    final Description testNameB = Description.createTestDescription("TestB", "testName");
    testB.addChild(testNameB);

    // We're making sure the `rootName` event isn't reported when no name is provided:
    doTest(root, List.of(testNameA, testNameB),
           """
             ##teamcity[enteredTheMatrix]
             ##teamcity[suiteTreeStarted name='TestA' locationHint='java:suite://TestA']
             ##teamcity[suiteTreeNode name='TestA.testName' locationHint='java:test://TestA/testName']
             ##teamcity[suiteTreeEnded name='TestA']
             ##teamcity[suiteTreeStarted name='TestB' locationHint='java:suite://TestB']
             ##teamcity[suiteTreeNode name='TestB.testName' locationHint='java:test://TestB/testName']
             ##teamcity[suiteTreeEnded name='TestB']
             ##teamcity[treeEnded]
             """,

           """
             ##teamcity[testSuiteStarted name='TestA' locationHint='java:suite://TestA']
             ##teamcity[testStarted name='TestA.testName' locationHint='java:test://TestA/testName']
             ##teamcity[testFinished name='TestA.testName']
             ##teamcity[testSuiteFinished name='TestA']
             ##teamcity[testSuiteStarted name='TestB' locationHint='java:suite://TestB']
             ##teamcity[testStarted name='TestB.testName' locationHint='java:test://TestB/testName']
             ##teamcity[testFinished name='TestB.testName']
             ##teamcity[testSuiteFinished name='TestB']
             """);
  }
}
