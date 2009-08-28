package com.intellij.junit3;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistryEx;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;
import com.intellij.rt.execution.junit.segments.Packet;
import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.Enumeration;
import java.util.Vector;

public class TreeSender {
  private static void sendNode(Test test, Packet packet) {
    Vector testCases = getTestCasesOf(test);
    packet.addObject(test).addLong(testCases.size());
    for (int i = 0; i < testCases.size(); i++) {
      Test nextTest = (Test)testCases.get(i);
      sendNode(nextTest, packet);
    }
  }

  private static Vector getTestCasesOf(Test test) {
    Vector testCases = new Vector();
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

  public static void sendSuite(OutputObjectRegistryEx registry, Test suite) {
    Packet packet = registry.createPacket();
    packet.addString(PoolOfDelimiters.TREE_PREFIX);
    sendNode(suite, packet);
    packet.addString("\n");
    packet.send();
  }
}
