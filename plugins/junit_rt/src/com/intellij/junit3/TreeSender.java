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
package com.intellij.junit3;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistry;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;
import com.intellij.rt.execution.junit.segments.Packet;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.*;

public class TreeSender {
  private static void sendNode(Test test, Packet packet, Collection objects) {
    Vector testCases = getTestCasesOf(test);
    packet.addObject(test, objects).addLong(testCases.size());
    for (int i = 0; i < testCases.size(); i++) {
      Test nextTest = (Test)testCases.get(i);
      sendNode(nextTest, packet, objects);
    }
  }

  private static Vector getTestCasesOf(Test test) {
    Vector testCases = new Vector();
    if (test instanceof TestRunnerUtil.SuiteMethodWrapper) {
      test = ((TestRunnerUtil.SuiteMethodWrapper)test).getSuite();
    }
    if (test instanceof TestSuite) {
      TestSuite testSuite = (TestSuite)test;

      for (Enumeration each = testSuite.tests(); each.hasMoreElements();) {
        Object childTest = each.nextElement();
        if (childTest instanceof TestSuite && !((TestSuite)childTest).tests().hasMoreElements()) continue;
        testCases.addElement(childTest);
      }
    }
    return testCases;
  }

  public static void sendSuite(OutputObjectRegistry registry, Test suite) {
    Packet packet = registry.createPacket();
    packet.addString(PoolOfDelimiters.TREE_PREFIX);
    Collection objects = new ArrayList();
    sendNode(suite, packet, objects);
    for (Iterator iterator = objects.iterator(); iterator.hasNext();) {
      ((Packet)iterator.next()).send();
    }
    packet.addString("\n");
    packet.send();
  }
}
