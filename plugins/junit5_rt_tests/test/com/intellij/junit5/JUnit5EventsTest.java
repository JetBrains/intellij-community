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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.engine.descriptor.MethodTestDescriptor;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.launcher.TestIdentifier;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

class JUnit5EventsTest {

  @Test
  void multipleFailures() throws Exception {
    StringBuffer buf = new StringBuffer();
    JUnit5TestExecutionListener executionListener = new JUnit5TestExecutionListener(new PrintStream(new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        buf.append(new String(new byte[]{(byte)b}));
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
    TestDescriptor testDescriptor = new MethodTestDescriptor(UniqueId.forEngine("engine"), TestClass.class,
                                                             TestClass.class.getDeclaredMethod("test1"));
    TestIdentifier identifier = TestIdentifier.from(testDescriptor);
    executionListener.executionStarted(identifier);
    MultipleFailuresError multipleFailuresError = new MultipleFailuresError("2 errors");
    multipleFailuresError.addFailure(new AssertionFailedError("message1", "expected1", "actual1"));
    multipleFailuresError.addFailure(new AssertionFailedError("message2", "expected2", "actual2"));
    executionListener.executionFinished(identifier, TestExecutionResult.failed(multipleFailuresError));


    Assertions.assertEquals("##teamcity[enteredTheMatrix]\n" +
                            "\n" +
                            "##teamcity[testStarted id='[engine:engine]' name='test1()']\n" +
                            "\n" +
                            "##teamcity[testFailed actual='actual1' expected='expected1' name='test1()' details='' id='|[engine:engine|]' message='']\n" +
                            "\n" +
                            "##teamcity[testFailed actual='actual2' expected='expected2' name='test1()' details='' id='|[engine:engine|]' message='']\n" +
                            "\n" +
                            "##teamcity[testFailed name='test1()' details='TRACE' id='|[engine:engine|]' message='2 errors (2 failures)|n\tmessage1|n\tmessage2']\n" +
                            "\n" +
                            "##teamcity[testFinished id='[engine:engine]' name='test1()']\n", buf.toString());
  }

  private static class TestClass {
    @Test
    void test1() {
    }
  }
}
