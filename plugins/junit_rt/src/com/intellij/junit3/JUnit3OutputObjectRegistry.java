/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 05-Jun-2009
 */
package com.intellij.junit3;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistry;
import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class JUnit3OutputObjectRegistry extends OutputObjectRegistry {

  public JUnit3OutputObjectRegistry(PacketProcessor mainTransport, int lastIndex) {
    super(mainTransport, lastIndex);
  }

  protected int getTestCont(Object test) {
    return ((Test)test).countTestCases();
  }

  protected void addStringRepresentation(Object test, Packet packet) {
    if (test instanceof TestRunnerUtil.FailedTestCase) {
      addTestMethod(packet, ((TestRunnerUtil.FailedTestCase)test).getMethodName(), ((TestCase)test).getName());
    }
    else if (test instanceof TestCase) {
      addTestMethod(packet, ((TestCase)test).getName(), test.getClass().getName());
    }
    else if (test instanceof TestAllInPackage2) {
      TestAllInPackage2 allInPackage = (TestAllInPackage2)test;
      addAllInPackage(packet, allInPackage.getName());
    }
    else if (test instanceof TestSuite) {
      TestSuite testSuite = (TestSuite)test;
      String fullName = testSuite.getName();
      if (fullName == null) {
        addUnknownTest(packet, test);
        return;
      }
      addTestClass(packet, fullName);
    }
    else if (test instanceof TestRunnerUtil.SuiteMethodWrapper) {
      addTestClass(packet, ((TestRunnerUtil.SuiteMethodWrapper)test).getClassName());
    }
    else {
      addUnknownTest(packet, test);
    }
  }
}
