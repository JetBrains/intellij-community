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

import com.intellij.junit4.SMTestSender;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.Description;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class JUnitTreeByDescriptionHierarchyTest {
  @Test
  public void testEmptySuite() throws Exception {
    doTest(Description.createSuiteDescription("empty suite"), "");
  }

  @Test
  public void test2Parameterized() throws Exception {
    final Description root = Description.createSuiteDescription("root");
    for (String className : new String[]{"a.TestA", "a.TestB"}) {
      final Description aTestClass = Description.createSuiteDescription(className);
      root.addChild(aTestClass);
      for (String paramName : new String[]{"[0]", "[1]"}) {
        final Description param1 = Description.createSuiteDescription(paramName);
        aTestClass.addChild(param1);
        param1.addChild(Description.createTestDescription(className, "testName" + paramName));
      }
      
    }
    doTest(root, "##teamcity[suiteTreeStarted name='TestA' locationHint='java:suite://a.TestA']\n" +
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
                 "##teamcity[suiteTreeEnded name='TestB']\n");
  }

  @Test
  public void testSingleMethod() throws Exception {
    final Description rootDescription = Description.createTestDescription("TestA", "testName");
    doTest(rootDescription, "##teamcity[suiteTreeNode name='testName' locationHint='java:test://TestA.testName']\n");
  }

  @Test
  public void testSuiteAndParameterizedTestsInOnePackage() throws Exception {
    final Description root = Description.createSuiteDescription("root");
    final Description aTestClass = Description.createSuiteDescription("ATest");
    root.addChild(aTestClass);
    for (String paramName : new String[]{"[0]", "[1]"}) {
      final Description param1 = Description.createSuiteDescription(paramName);
      aTestClass.addChild(param1);
      param1.addChild(Description.createTestDescription("ATest", "testName" + paramName));
    }
    final Description suiteDescription = Description.createSuiteDescription("suite");
    root.addChild(suiteDescription);
    final Description aTestClassWithJUnit3Test = Description.createSuiteDescription("ATest");
    suiteDescription.addChild(aTestClassWithJUnit3Test);
    aTestClassWithJUnit3Test.addChild(Description.createTestDescription("ATest", "test"));
    doTest(root, "##teamcity[suiteTreeStarted name='ATest' locationHint='java:suite://ATest']\n" +
                 "##teamcity[suiteTreeStarted name='|[0|]' locationHint='java:suite://ATest.|[0|]']\n" +
                 "##teamcity[suiteTreeNode name='testName|[0|]' locationHint='java:test://ATest.testName|[0|]']\n" +
                 "##teamcity[suiteTreeEnded name='|[0|]']\n" +
                 "##teamcity[suiteTreeStarted name='|[1|]' locationHint='java:suite://ATest.|[1|]']\n" +
                 "##teamcity[suiteTreeNode name='testName|[1|]' locationHint='java:test://ATest.testName|[1|]']\n" +
                 "##teamcity[suiteTreeEnded name='|[1|]']\n" +
                 "##teamcity[suiteTreeNode name='test' locationHint='java:test://ATest.test']\n" +
                 "##teamcity[suiteTreeEnded name='ATest']\n");
  }

  private static void doTest(Description description, String expected) {
    final StringBuffer buf = new StringBuffer();
    new SMTestSender().sendTree(description, new PrintStream(new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        buf.append(new String(new byte[]{(byte)b}));
      }
    }));

    Assert.assertEquals("output: " + buf, expected, StringUtil.convertLineSeparators(buf.toString()));
  }
}
