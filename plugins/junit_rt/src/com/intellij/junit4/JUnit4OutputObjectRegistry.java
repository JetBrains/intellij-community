/*
 * User: anna
 * Date: 05-Jun-2009
 */
package com.intellij.junit4;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistryEx;
import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import org.junit.runner.Description;


public class JUnit4OutputObjectRegistry extends OutputObjectRegistryEx {
  public JUnit4OutputObjectRegistry(PacketProcessor mainTransport, PacketProcessor auxilaryTransport) {
    super(mainTransport, auxilaryTransport);
  }

  protected int getTestCont(Object test) {
    return ((Description)test).testCount();
  }

  protected void addStringRepresentation(Object obj, Packet packet) {
    Description test = (Description)obj;
    if (test.isTest()) {
      addTestMethod(packet, JUnit4ReflectionUtil.getMethodName(test), JUnit4ReflectionUtil.getClassName(test));
    }
    else if (test.isSuite()) {
      String fullName = JUnit4ReflectionUtil.getClassName(test);
      if (fullName == null) {
        addUnknownTest(packet, test);
        return;
      }
      addTestClass(packet, fullName);
    }
    else {
      addUnknownTest(packet, test);
    }
  }

}