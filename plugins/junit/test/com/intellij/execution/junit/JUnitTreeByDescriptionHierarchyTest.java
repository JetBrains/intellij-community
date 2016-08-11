/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.junit;

import com.intellij.junit4.JUnit4TestListener;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JUnitTreeByDescriptionHierarchyTest {
  @Test
  public void testEmptySuite() throws Exception {
    doTest(Description.createSuiteDescription("empty suite"), "##teamcity[enteredTheMatrix]\n" +
                                                              "##teamcity[treeEnded]\n");
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
           "##teamcity[enteredTheMatrix]\n" +
           "##teamcity[suiteTreeStarted name='TestA' locationHint='java:suite://a.TestA']\n" +
           "##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://a.TestA.|[0|]']\n" +
           "##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://a.TestA.testName|[0|]']\n" +
           "##teamcity[suiteTreeEnded name='|[0|]']\n" +
           "##teamcity[suiteTreeStarted name='|[1|]' locationHint='java:suite://a.TestA.|[1|]']\n" +
           "##teamcity[suiteTreeNode name='testName|[1|]' locationHint='java:test://a.TestA.testName|[1|]']\n" +
           "##teamcity[suiteTreeEnded name='|[1|]']\n" +
           "##teamcity[suiteTreeEnded name='TestA']\n" +
           "##teamcity[suiteTreeStarted name='TestB' locationHint='java:suite://a.TestB']\n" +
           "##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://a.TestB.|[0|]']\n" +
           "##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://a.TestB.testName|[0|]']\n" +
           "##teamcity[suiteTreeEnded name='|[0|]']\n" +
           "##teamcity[suiteTreeStarted name='|[1|]' locationHint='java:suite://a.TestB.|[1|]']\n" +
           "##teamcity[suiteTreeNode name='testName|[1|]' locationHint='java:test://a.TestB.testName|[1|]']\n" +
           "##teamcity[suiteTreeEnded name='|[1|]']\n" +
           "##teamcity[suiteTreeEnded name='TestB']\n" +
           "##teamcity[treeEnded]\n",


           "##teamcity[rootName name = 'root' location = 'java:suite://root']\n" +
           "##teamcity[testSuiteStarted name='TestA']\n" +
           "##teamcity[testSuiteStarted name='|[0|]']\n" +
           "\n" +
           "##teamcity[testStarted name='testName|[0|]' locationHint='java:test://a.TestA.testName|[0|]']\n" +
           "\n" +
           "##teamcity[testFinished name='testName|[0|]']\n" +
           "##teamcity[testSuiteFinished name='|[0|]']\n" +
           "##teamcity[testSuiteStarted name='|[1|]']\n" +
           "\n" +
           "##teamcity[testStarted name='testName|[1|]' locationHint='java:test://a.TestA.testName|[1|]']\n" +
           "\n" +
           "##teamcity[testFinished name='testName|[1|]']\n" +
           "##teamcity[testSuiteFinished name='|[1|]']\n" +
           "##teamcity[testSuiteFinished name='TestA']\n" +
           "##teamcity[testSuiteStarted name='TestB']\n" +
           "##teamcity[testSuiteStarted name='|[0|]']\n" +
           "\n" +
           "##teamcity[testStarted name='testName|[0|]' locationHint='java:test://a.TestB.testName|[0|]']\n" +
           "\n" +
           "##teamcity[testFinished name='testName|[0|]']\n" +
           "##teamcity[testSuiteFinished name='|[0|]']\n" +
           "##teamcity[testSuiteStarted name='|[1|]']\n" +
           "\n" +
           "##teamcity[testStarted name='testName|[1|]' locationHint='java:test://a.TestB.testName|[1|]']\n" +
           "\n" +
           "##teamcity[testFinished name='testName|[1|]']\n" +
           "##teamcity[testSuiteFinished name='|[1|]']\n" +
           "##teamcity[testSuiteFinished name='TestB']\n");
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
    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testSuiteStarted name='ATest' locationHint='java:suite://ATest']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.test1' locationHint='java:test://ATest.test1']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.test1']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='ATest.test2' locationHint='java:test://ATest.test2']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='ATest.test2']\n" +
                                          "##teamcity[testSuiteFinished name='ATest']\n", StringUtil.convertLineSeparators(buf.toString()));
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
    doTest(rootDescription, tests, "##teamcity[enteredTheMatrix]\n" +
                                   "##teamcity[suiteTreeStarted name='MyTest' locationHint='java:suite://a.MyTest']\n" +
                                   "##teamcity[suiteTreeNode name='MyTest.testMe' locationHint='java:test://a.MyTest.testMe']\n" +
                                   "##teamcity[suiteTreeEnded name='MyTest']\n" +
                                   "##teamcity[suiteTreeStarted name='MyTest' locationHint='java:suite://b.MyTest']\n" +
                                   "##teamcity[suiteTreeNode name='MyTest.testMe' locationHint='java:test://b.MyTest.testMe']\n" +
                                   "##teamcity[suiteTreeEnded name='MyTest']\n" +
                                   "##teamcity[treeEnded]\n",
           "##teamcity[rootName name = 'root' location = 'java:suite://root']\n" +
           "##teamcity[testSuiteStarted name='MyTest']\n" +
           "\n" +
           "##teamcity[testStarted name='MyTest.testMe' locationHint='java:test://a.MyTest.testMe']\n" +
           "\n" +
           "##teamcity[testFinished name='MyTest.testMe']\n" +
           "##teamcity[testSuiteFinished name='MyTest']\n" +
           "##teamcity[testSuiteStarted name='MyTest']\n" +
           "\n" +
           "##teamcity[testStarted name='MyTest.testMe' locationHint='java:test://b.MyTest.testMe']\n" +
           "\n" +
           "##teamcity[testFinished name='MyTest.testMe']\n" +
           "##teamcity[testSuiteFinished nam" +
           "e='MyTest']\n");
  }

  @Test
  public void testSingleParameterizedClass() throws Exception {
    final String className = "a.TestA";
    final Description aTestClassDescription = Description.createSuiteDescription(className);
    final ArrayList<Description> tests = new ArrayList<>();
    attachParameterizedTests(className, aTestClassDescription, tests);
    doTest(aTestClassDescription, tests,
           //tree
           "##teamcity[enteredTheMatrix]\n" +
           "##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://a.TestA.|[0|]']\n" +
           "##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://a.TestA.testName|[0|]']\n" +
           "##teamcity[suiteTreeEnded name='|[0|]']\n" +
           "##teamcity[suiteTreeStarted name='|[1|]' locationHint='java:suite://a.TestA.|[1|]']\n" +
           "##teamcity[suiteTreeNode name='testName|[1|]' locationHint='java:test://a.TestA.testName|[1|]']\n" +
           "##teamcity[suiteTreeEnded name='|[1|]']\n" +
           "##teamcity[treeEnded]\n",
           //start
           "##teamcity[rootName name = 'TestA' comment = 'a' location = 'java:suite://a.TestA']\n" +
           "##teamcity[testSuiteStarted name='|[0|]']\n" +
           "\n" +
           "##teamcity[testStarted name='testName|[0|]' locationHint='java:test://a.TestA.testName|[0|]']\n" +
           "\n" +
           "##teamcity[testFinished name='testName|[0|]']\n" +
           "##teamcity[testSuiteFinished name='|[0|]']\n" +
           "##teamcity[testSuiteStarted name='|[1|]']\n" +
           "\n" +
           "##teamcity[testStarted name='testName|[1|]' locationHint='java:test://a.TestA.testName|[1|]']\n" +
           "\n" +
           "##teamcity[testFinished name='testName|[1|]']\n" +
           "##teamcity[testSuiteFinished name='|[1|]']\n");
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
           "##teamcity[enteredTheMatrix]\n" +
           "##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://a.TestA.|[0|]']\n" +
           "##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://a.TestA.testName|[0|]']\n" +
           "##teamcity[suiteTreeEnded name='|[0|]']\n" +
           "##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://a.TestA.|[0|]']\n" +
           "##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://a.TestA.testName|[0|]']\n" +
           "##teamcity[suiteTreeEnded name='|[0|]']\n" +
           "##teamcity[treeEnded]\n",
           //start
           "##teamcity[rootName name = 'TestA' comment = 'a' location = 'java:suite://a.TestA']\n" +
           "##teamcity[testSuiteStarted name='|[0|]']\n" +
           "\n" +
           "##teamcity[testStarted name='testName|[0|]' locationHint='java:test://a.TestA.testName|[0|]']\n" +
           "\n" +
           "##teamcity[testFinished name='testName|[0|]']\n" +
           "##teamcity[testSuiteFinished name='|[0|]']\n" +
           "##teamcity[testSuiteStarted name='|[0|]']\n" +
           "\n" +
           "##teamcity[testStarted name='testName|[0|]' locationHint='java:test://a.TestA.testName|[0|]']\n" +
           "\n" +
           "##teamcity[testFinished name='testName|[0|]']\n" +
           "##teamcity[testSuiteFinished name='|[0|]']\n");
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
           "##teamcity[enteredTheMatrix]\n" +
           "##teamcity[suiteTreeStarted name='|[0: with - 1.1|]' locationHint='java:suite://a.TestA.|[0: with - 1.1|]']\n" +
           "##teamcity[suiteTreeNode name='testName|[0: with - 1.1|]' locationHint='java:test://a.TestA.testName|[0: with - 1.1|]']\n" +
           "##teamcity[suiteTreeEnded name='|[0: with - 1.1|]']\n" +
           "##teamcity[suiteTreeStarted name='|[1: with - 2.1|]' locationHint='java:suite://a.TestA.|[1: with - 2.1|]']\n" +
           "##teamcity[suiteTreeNode name='testName|[1: with - 2.1|]' locationHint='java:test://a.TestA.testName|[1: with - 2.1|]']\n" +
           "##teamcity[suiteTreeEnded name='|[1: with - 2.1|]']\n" +
           "##teamcity[treeEnded]\n",
           //start
           "##teamcity[rootName name = 'TestA' comment = 'a' location = 'java:suite://a.TestA']\n" +
           "##teamcity[testSuiteStarted name='|[0: with - 1.1|]']\n" +
           "\n" +
           "##teamcity[testStarted name='testName|[0: with - 1.1|]' locationHint='java:test://a.TestA.testName|[0: with - 1.1|]']\n" +
           "\n" +
           "##teamcity[testFinished name='testName|[0: with - 1.1|]']\n" +
           "##teamcity[testSuiteFinished name='|[0: with - 1.1|]']\n" +
           "##teamcity[testSuiteStarted name='|[1: with - 2.1|]']\n" +
           "\n" +
           "##teamcity[testStarted name='testName|[1: with - 2.1|]' locationHint='java:test://a.TestA.testName|[1: with - 2.1|]']\n" +
           "\n" +
           "##teamcity[testFinished name='testName|[1: with - 2.1|]']\n" +
           "##teamcity[testSuiteFinished name='|[1: with - 2.1|]']\n");
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
           "##teamcity[enteredTheMatrix]\n" +
           "##teamcity[suiteTreeStarted name='ASuite1' locationHint='java:suite://ASuite1']\n" +
           "##teamcity[suiteTreeStarted name='ATest' locationHint='java:suite://ATest']\n" +
           "##teamcity[suiteTreeNode name='ATest.test1' locationHint='java:test://ATest.test1']\n" +
           "##teamcity[suiteTreeEnded name='ATest']\n" +
           "##teamcity[suiteTreeEnded name='ASuite1']\n" +
           "##teamcity[suiteTreeStarted name='ASuite2' locationHint='java:suite://ASuite2']\n" +
           "##teamcity[suiteTreeStarted name='ATest' locationHint='java:suite://ATest']\n" +
           "##teamcity[suiteTreeNode name='ATest.test1' locationHint='java:test://ATest.test1']\n" +
           "##teamcity[suiteTreeEnded name='ATest']\n" +
           "##teamcity[suiteTreeEnded name='ASuite2']\n" +
           "##teamcity[treeEnded]\n",

           //started
           "##teamcity[rootName name = 'root' location = 'java:suite://root']\n" +
           "##teamcity[testSuiteStarted name='ASuite1']\n" +
           "##teamcity[testSuiteStarted name='ATest']\n" +
           "\n" +
           "##teamcity[testStarted name='ATest.test1' locationHint='java:test://ATest.test1']\n" +
           "\n" +
           "##teamcity[testFinished name='ATest.test1']\n" +
           "##teamcity[testSuiteFinished name='ATest']\n" +
           "##teamcity[testSuiteFinished name='ASuite1']\n" +
           "##teamcity[testSuiteStarted name='ASuite2']\n" +
           "##teamcity[testSuiteStarted name='ATest']\n" +
           "\n" +
           "##teamcity[testStarted name='ATest.test1' locationHint='java:test://ATest.test1']\n" +
           "\n" +
           "##teamcity[testFinished name='ATest.test1']\n" +
           "##teamcity[testSuiteFinished name='ATest']\n" +
           "##teamcity[testSuiteFinished name='ASuite2']\n");
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

    final StringBuffer buf = new StringBuffer();
    final JUnit4TestListener sender = createListener(buf);
    sender.sendTree(root);

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[suiteTreeStarted name='TestA' locationHint='java:suite://TestA']\n" +
                                          "##teamcity[suiteTreeNode name='TestA.testName' locationHint='java:test://TestA.testName']\n" +
                                          "##teamcity[suiteTreeEnded name='TestA']\n" +
                                          "##teamcity[treeEnded]\n", StringUtil.convertLineSeparators(buf.toString()));

    buf.setLength(0);

    sender.testRunStarted(testA);
    final Exception exception = new Exception();
    exception.setStackTrace(new StackTraceElement[0]);
    sender.testAssumptionFailure(new Failure(testA, exception));
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, "##teamcity[rootName name = 'root' location = 'java:suite://root']\n" +
                                          "##teamcity[testSuiteStarted name='TestA']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='TestA.testName' locationHint='java:test://TestA.testName']\n" +
                                          "\n" +
                                          "##teamcity[testIgnored name='TestA.testName' details='java.lang.Exception|n' error='true' message='']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='TestA.testName']\n" +
                                          "##teamcity[testSuiteFinished name='TestA']\n", StringUtil.convertLineSeparators(buf.toString()));
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

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[suiteTreeStarted name='TestA' locationHint='java:suite://TestA']\n" +
                                          "##teamcity[suiteTreeNode name='TestA.testName' locationHint='java:test://TestA.testName']\n" +
                                          "##teamcity[suiteTreeEnded name='TestA']\n" +
                                          "##teamcity[treeEnded]\n", StringUtil.convertLineSeparators(buf.toString()));

    buf.setLength(0);

    sender.testRunStarted(testA);
    final Exception exception = new Exception();
    exception.setStackTrace(new StackTraceElement[0]);
    sender.testFailure(new Failure(testA, exception));
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, "##teamcity[rootName name = 'root' location = 'java:suite://root']\n" +
                                          "##teamcity[testStarted name='Class Configuration'  locationHint='java:suite://TestA' ]\n" +
                                          "\n" +
                                          "##teamcity[testFailed name='Class Configuration' details='java.lang.Exception|n' error='true' message='']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='Class Configuration']\n" +
                                          "##teamcity[testSuiteStarted name='TestA']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='TestA.testName' locationHint='java:test://TestA.testName']\n" +
                                          "\n" +
                                          "##teamcity[testIgnored name='TestA.testName']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='TestA.testName']\n" +
                                          "##teamcity[testSuiteFinished name='TestA']\n", StringUtil.convertLineSeparators(buf.toString()));
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

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[suiteTreeNode name='TestA.test1' locationHint='java:test://TestA.test1']\n" +
                                          "##teamcity[suiteTreeNode name='TestB.test2' locationHint='java:test://TestB.test2']\n" +
                                          "##teamcity[treeEnded]\n", StringUtil.convertLineSeparators(buf.toString()));

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

    Assert.assertEquals("output: " + buf, "##teamcity[rootName name = 'root' location = 'java:suite://root']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='TestA.test1' locationHint='java:test://TestA.test1']\n" +
                                          "\n" +
                                          "##teamcity[testFailed name='TestA.test1' details='java.lang.Exception|n' error='true' message='']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='TestA.test1']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='TestB.test2' locationHint='java:test://TestB.test2']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='TestB.test2']\n", StringUtil.convertLineSeparators(buf.toString()));
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

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[suiteTreeStarted name='TestA' locationHint='java:suite://TestA']\n" +
                                          "##teamcity[suiteTreeNode name='TestA.testName' locationHint='java:test://TestA.testName']\n" +
                                          "##teamcity[suiteTreeEnded name='TestA']\n" +
                                          "##teamcity[treeEnded]\n", StringUtil.convertLineSeparators(buf.toString()));

    buf.setLength(0);

    sender.testRunStarted(testA);
    final Exception exception = new Exception();
    exception.setStackTrace(new StackTraceElement[0]);
    sender.testStarted(testName);
    sender.testFinished(testName);
    sender.testFailure(new Failure(testA, exception));
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, "##teamcity[rootName name = 'root' location = 'java:suite://root']\n" +
                                          "##teamcity[testSuiteStarted name='TestA']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='TestA.testName' locationHint='java:test://TestA.testName']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='TestA.testName']\n" +
                                          "##teamcity[testStarted name='Class Configuration'  locationHint='java:suite://TestA' ]\n" +
                                          "\n" +
                                          "##teamcity[testFailed name='Class Configuration' details='java.lang.Exception|n' error='true' message='']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='Class Configuration']\n" +
                                          "##teamcity[testSuiteFinished name='TestA']\n", StringUtil.convertLineSeparators(buf.toString()));
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

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[suiteTreeStarted name='TestA' locationHint='java:suite://TestA']\n" +
                                          "##teamcity[suiteTreeStarted name='param' locationHint='java:suite://param']\n" +
                                          "##teamcity[suiteTreeNode name='TestA.testName' locationHint='java:test://TestA.testName']\n" +
                                          "##teamcity[suiteTreeEnded name='param']\n" +
                                          "##teamcity[suiteTreeEnded name='TestA']\n" +
                                          "##teamcity[treeEnded]\n", StringUtil.convertLineSeparators(buf.toString()));

    buf.setLength(0);

    sender.testRunStarted(testA);
    final Exception exception = new Exception();
    exception.setStackTrace(new StackTraceElement[0]);
    sender.testAssumptionFailure(new Failure(testA, exception));
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, "##teamcity[rootName name = 'root' location = 'java:suite://root']\n" +
                                          "##teamcity[testSuiteStarted name='TestA']\n" +
                                          "##teamcity[testSuiteStarted name='param']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='TestA.testName' locationHint='java:test://TestA.testName']\n" +
                                          "\n" +
                                          "##teamcity[testIgnored name='TestA.testName' details='java.lang.Exception|n' error='true' message='']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='TestA.testName']\n" +
                                          "##teamcity[testSuiteFinished name='param']\n" +
                                          "##teamcity[testSuiteFinished name='TestA']\n", StringUtil.convertLineSeparators(buf.toString()));
    buf.setLength(0);

    //testStarted and testFinished are called by the framework
    sender.testRunStarted(testA);
    sender.testAssumptionFailure(new Failure(testName, exception));
    sender.testRunFinished(new Result());

    Assert.assertEquals("output: " + buf, "##teamcity[rootName name = 'root' location = 'java:suite://root']\n" +
                                          "\n" +
                                          "##teamcity[testIgnored name='TestA.testName' details='java.lang.Exception|n' error='true' message='']\n", StringUtil.convertLineSeparators(buf.toString()));
    
  }

  @Test
  public void testSingleMethod() throws Exception {
    final Description rootDescription = Description.createTestDescription("TestA", "testName");
    doTest(rootDescription, Collections.singletonList(rootDescription),
           "##teamcity[enteredTheMatrix]\n" +
           "##teamcity[suiteTreeNode name='TestA.testName' locationHint='java:test://TestA.testName']\n" +
           "##teamcity[treeEnded]\n",
           "##teamcity[rootName name = 'TestA' location = 'java:suite://TestA']\n" +
           "\n" +
           "##teamcity[testStarted name='TestA.testName' locationHint='java:test://TestA.testName']\n" +
           "\n" +
           "##teamcity[testFinished name='TestA.testName']\n");
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

    Assert.assertEquals("output: " + buf, "##teamcity[enteredTheMatrix]\n" +
                                          "##teamcity[testSuiteStarted name='TestA' locationHint='java:suite://a.TestA']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='TestA.testName' locationHint='java:test://a.TestA.testName']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='TestA.testName']\n" +
                                          "##teamcity[testSuiteFinished name='TestA']\n" +
                                          "##teamcity[testSuiteStarted name='TestB' locationHint='java:suite://a.TestB']\n" +
                                          "\n" +
                                          "##teamcity[testStarted name='TestB.testName' locationHint='java:test://a.TestB.testName']\n" +
                                          "\n" +
                                          "##teamcity[testFinished name='TestB.testName']\n" +
                                          "##teamcity[testSuiteFinished name='TestB']\n", StringUtil.convertLineSeparators(buf.toString()));
  }

  private static JUnit4TestListener createListener(final StringBuffer buf) {
    return new JUnit4TestListener(new PrintStream(new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        buf.append(new String(new byte[]{(byte)b}));
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
           "##teamcity[enteredTheMatrix]\n" +
           "##teamcity[suiteTreeStarted name='testName' locationHint='java:suite://testName']\n" +
           "##teamcity[suiteTreeNode name='ATest.testName|[0|]' locationHint='java:test://ATest.testName|[0|]']\n" +
           "##teamcity[suiteTreeNode name='ATest.testName|[1|]' locationHint='java:test://ATest.testName|[1|]']\n" +
           "##teamcity[suiteTreeEnded name='testName']\n" +
           "##teamcity[treeEnded]\n",


           "##teamcity[rootName name = 'ATest' location = 'java:suite://ATest']\n" +
           "##teamcity[testSuiteStarted name='testName']\n" +
           "\n" +
           "##teamcity[testStarted name='ATest.testName|[0|]' locationHint='java:test://ATest.testName|[0|]']\n" +
           "\n" +
           "##teamcity[testFinished name='ATest.testName|[0|]']\n" +
           "\n" +
           "##teamcity[testStarted name='ATest.testName|[1|]' locationHint='java:test://ATest.testName|[1|]']\n" +
           "\n" +
           "##teamcity[testFinished name='ATest.testName|[1|]']\n" +
           "##teamcity[testSuiteFinished name='testName']\n");
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
           "##teamcity[enteredTheMatrix]\n" +
           "##teamcity[suiteTreeStarted name='ATest' locationHint='java:suite://ATest']\n" +
           "##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://ATest.|[0|]']\n" +
           "##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://ATest.testName|[0|]']\n" +
           "##teamcity[suiteTreeEnded name='|[0|]']\n" +
           "##teamcity[suiteTreeStarted name='|[1|]' locationHint='java:suite://ATest.|[1|]']\n" +
           "##teamcity[suiteTreeNode name='testName|[1|]' locationHint='java:test://ATest.testName|[1|]']\n" +
           "##teamcity[suiteTreeEnded name='|[1|]']\n" +
           "##teamcity[suiteTreeEnded name='ATest']\n" +
           "##teamcity[suiteTreeStarted name='suite' locationHint='java:suite://suite']\n" +
           "##teamcity[suiteTreeStarted name='ATest' locationHint='java:suite://ATest']\n" +
           "##teamcity[suiteTreeNode name='ATest.test' locationHint='java:test://ATest.test']\n" +
           "##teamcity[suiteTreeEnded name='ATest']\n" +
           "##teamcity[suiteTreeEnded name='suite']\n" +
           "##teamcity[treeEnded]\n",

           //start
           "##teamcity[rootName name = 'root' location = 'java:suite://root']\n" +
           "##teamcity[testSuiteStarted name='ATest']\n" +
           "##teamcity[testSuiteStarted name='|[0|]']\n" +
           "\n" +
           "##teamcity[testStarted name='testName|[0|]' locationHint='java:test://ATest.testName|[0|]']\n" +
           "\n" +
           "##teamcity[testFinished name='testName|[0|]']\n" +
           "##teamcity[testSuiteFinished name='|[0|]']\n" +
           "##teamcity[testSuiteStarted name='|[1|]']\n" +
           "\n" +
           "##teamcity[testStarted name='testName|[1|]' locationHint='java:test://ATest.testName|[1|]']\n" +
           "\n" +
           "##teamcity[testFinished name='testName|[1|]']\n" +
           "##teamcity[testSuiteFinished name='|[1|]']\n" +
           "##teamcity[testSuiteFinished name='ATest']\n" +
           "##teamcity[testSuiteStarted name='suite']\n" +
           "##teamcity[testSuiteStarted name='ATest']\n" +
           "\n" +
           "##teamcity[testStarted name='ATest.test' locationHint='java:test://ATest.test']\n" +
           "\n" +
           "##teamcity[testFinished name='ATest.test']\n" +
           "##teamcity[testSuiteFinished name='ATest']\n" +
           "##teamcity[testSuiteFinished name='suite']\n");
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
           "##teamcity[enteredTheMatrix]\n" +
           "##teamcity[suiteTreeNode name='TestSuite$1.warning' locationHint='java:test://junit.framework.TestSuite$1.warning']\n" +
           "##teamcity[treeEnded]\n",

           "##teamcity[rootName name = 'TestA' location = 'java:suite://TestA']\n" +
           "\n" +
           "##teamcity[testStarted name='TestSuite$1.warning' locationHint='java:test://junit.framework.TestSuite$1.warning']\n" +
           "\n" +
           "##teamcity[testFinished name='TestSuite$1.warning']\n");
  }
}
