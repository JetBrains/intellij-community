/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.junit5;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import jetbrains.buildServer.messages.serviceMessages.MapSerializerUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.engine.descriptor.ClassTestDescriptor;
import org.junit.jupiter.engine.descriptor.TestFactoryTestDescriptor;
import org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;

class JUnit5EventsTest {

  private JUnit5TestExecutionListener myExecutionListener;
  private StringBuffer myBuf;

  @BeforeEach
  void setUp() {
    myBuf = new StringBuffer();
    myExecutionListener = new JUnit5TestExecutionListener(new PrintStream(new OutputStream() {
      @Override
      public void write(int b) {
        myBuf.append(new String(new byte[]{(byte)b}, CharsetToolkit.UTF8_CHARSET));
      }
    })) {
      @Override
      protected long getDuration() {
        return 0;
      }

      @Override
      protected String getTrace(Throwable ex) {
        return "TRACE";
      }
    };
  }

  @AfterEach
  void tearDown() {
    myBuf = null;
  }

  @Test
  void multipleFailures() throws Exception {

    EngineDescriptor engineDescriptor = new EngineDescriptor(UniqueId.forEngine("engine"), "e");
    ClassTestDescriptor c = new ClassTestDescriptor(UniqueId.forEngine("testClass"), TestClass.class);
    engineDescriptor.addChild(c);
    TestDescriptor testDescriptor = new TestMethodTestDescriptor(UniqueId.forEngine("testMethod"), TestClass.class,
                                                                 TestClass.class.getDeclaredMethod("test1"));
    c.addChild(testDescriptor);
    TestIdentifier identifier = TestIdentifier.from(testDescriptor);
    final TestPlan testPlan = TestPlan.from(Collections.singleton(engineDescriptor));
    myExecutionListener.setTestPlan(testPlan);
    myExecutionListener.executionStarted(identifier);
    MultipleFailuresError multipleFailuresError = new MultipleFailuresError("2 errors", Arrays.asList
      (new AssertionFailedError("message1", "expected1", "actual1"),
       new AssertionFailedError("message2", "expected2", "actual2")));
    myExecutionListener.executionFinished(identifier, TestExecutionResult.failed(multipleFailuresError));


    String lineSeparator = MapSerializerUtil.escapeStr(System.getProperty("line.separator"), MapSerializerUtil.STD_ESCAPER);
    Assertions.assertEquals("##teamcity[enteredTheMatrix]\n" +
                            "\n" +
                            "##teamcity[testStarted id='|[engine:testMethod|]' name='test1()' nodeId='|[engine:testMethod|]' parentNodeId='|[engine:testClass|]' locationHint='java:test://com.intellij.junit5.JUnit5EventsTest$TestClass.test1' metainfo='']\n" +
                            "\n" +
                            "##teamcity[testFailed name='test1()' id='|[engine:testMethod|]' nodeId='|[engine:testMethod|]' parentNodeId='|[engine:testClass|]' message='' expected='expected1' actual='actual1' details='']\n" +
                            "\n" +
                            "##teamcity[testFailed name='test1()' id='|[engine:testMethod|]' nodeId='|[engine:testMethod|]' parentNodeId='|[engine:testClass|]' message='' expected='expected2' actual='actual2' details='']\n" +
                            "\n" +
                            "##teamcity[testFailed name='test1()' id='|[engine:testMethod|]' nodeId='|[engine:testMethod|]' parentNodeId='|[engine:testClass|]' message='2 errors (2 failures)|n\tmessage1|n\tmessage2' details='TRACE']\n" +
                            "\n" +
                            "##teamcity[testFinished id='|[engine:testMethod|]' name='test1()' nodeId='|[engine:testMethod|]' parentNodeId='|[engine:testClass|]']\n", StringUtil.convertLineSeparators(myBuf.toString()));
  }

  @Test
  void containerFailure() throws Exception {
    EngineDescriptor engineDescriptor = new EngineDescriptor(UniqueId.forEngine("engine"), "e");
    ClassTestDescriptor classTestDescriptor = new ClassTestDescriptor(UniqueId.forEngine("testClass"), TestClass.class);
    engineDescriptor.addChild(classTestDescriptor);
    TestDescriptor testDescriptor = new TestFactoryTestDescriptor(UniqueId.forEngine("testMethod"), TestClass.class,
                                                                  TestClass.class.getDeclaredMethod("brokenStream"));
    classTestDescriptor.addChild(testDescriptor);
    TestIdentifier identifier = TestIdentifier.from(testDescriptor);
    final TestPlan testPlan = TestPlan.from(Collections.singleton(engineDescriptor));
    myExecutionListener.sendTree(testPlan, "");
    myExecutionListener.executionStarted(identifier);
    myExecutionListener.executionFinished(identifier, TestExecutionResult.failed(new IllegalStateException()));

    Assertions.assertEquals("##teamcity[enteredTheMatrix]\n" +
                            "##teamcity[suiteTreeStarted id='|[engine:testClass|]' name='JUnit5EventsTest$TestClass' nodeId='|[engine:testClass|]' parentNodeId='0' locationHint='java:suite://com.intellij.junit5.JUnit5EventsTest$TestClass']\n" +
                            "##teamcity[suiteTreeStarted id='|[engine:testMethod|]' name='brokenStream()' nodeId='|[engine:testMethod|]' parentNodeId='|[engine:testClass|]' locationHint='java:test://com.intellij.junit5.JUnit5EventsTest$TestClass.brokenStream' metainfo='']\n" +
                            "##teamcity[suiteTreeEnded id='|[engine:testMethod|]' name='brokenStream()' nodeId='|[engine:testMethod|]' parentNodeId='|[engine:testClass|]']\n" +
                            "##teamcity[suiteTreeEnded id='|[engine:testClass|]' name='JUnit5EventsTest$TestClass' nodeId='|[engine:testClass|]' parentNodeId='0']\n" +
                            "##teamcity[treeEnded]\n" +
                            "##teamcity[testSuiteStarted id='|[engine:testMethod|]' name='brokenStream()' nodeId='|[engine:testMethod|]' parentNodeId='|[engine:testClass|]'locationHint='java:test://com.intellij.junit5.JUnit5EventsTest$TestClass.brokenStream' metainfo='']\n" +
                            "\n" +
                            "##teamcity[testFailed name='Class Configuration' id='|[engine:testMethod|]' nodeId='|[engine:testMethod|]' parentNodeId='|[engine:testClass|]' error='true' message='' details='TRACE']\n" +
                            "\n" +
                            "##teamcity[testFinished name='Class Configuration' nodeId='|[engine:testMethod|]' parentNodeId='[engine:testClass]' ]\n" +
                            "##teamcity[testSuiteFinished  id='|[engine:testMethod|]' name='brokenStream()' nodeId='|[engine:testMethod|]' parentNodeId='|[engine:testClass|]']\n", StringUtil.convertLineSeparators(myBuf.toString()));
  }

  private static class TestClass {
    @Test
    void test1() {
    }

    @TestFactory
    Stream<DynamicTest> brokenStream() {
      return Stream.generate(() -> { throw new IllegalStateException(); });
    }
  }
}
