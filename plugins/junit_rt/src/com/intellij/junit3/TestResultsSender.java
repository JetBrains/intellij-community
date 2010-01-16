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

import com.intellij.rt.execution.junit.*;
import com.intellij.rt.execution.junit.segments.OutputObjectRegistryEx;
import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import junit.framework.AssertionFailedError;
import junit.framework.ComparisonFailure;
import junit.framework.Test;
import junit.framework.TestListener;

public class TestResultsSender implements TestListener {
  private final OutputObjectRegistryEx myRegistry;
  private final PacketProcessor myErr;
  private TestMeter myCurrentTestMeter;
  private Test myCurrentTest;

  public TestResultsSender(OutputObjectRegistryEx packetFactory, PacketProcessor segmentedErr) {
    myRegistry = packetFactory;
    myErr = segmentedErr;
  }

  public synchronized void addError(Test test, Throwable throwable) {
    try {
      final Class aClass = Class.forName("java.lang.AssertionError");
      if (aClass.isInstance(throwable)) {
        doAddFailure(test, (Error)throwable);
        return;
      }
    }
    catch (ClassNotFoundException ignored) {}

    stopMeter(test);
    prepareDefectPacket(test, throwable).send();
  }

  public synchronized void addFailure(Test test, AssertionFailedError assertion) {
    doAddFailure(test, assertion);
  }

  private void doAddFailure(final Test test, final Error assertion) {
    stopMeter(test);
    createExceptionNotification(assertion).createPacket(myRegistry, test).send();
  }

  private static PacketFactory createExceptionNotification(Error assertion) {
    if (assertion instanceof KnownException) return ((KnownException)assertion).getPacketFactory();
    if (assertion instanceof ComparisonFailure || assertion.getClass().getName().equals("org.junit.ComparisonFailure")) {
      return ComparisonDetailsExtractor.create(assertion);
    }
    return new ExceptionPacketFactory(PoolOfTestStates.FAILED_INDEX, assertion);
  }

  private Packet prepareDefectPacket(Test test, Throwable assertion) {
    return myRegistry.createPacket().
            setTestState(test, PoolOfTestStates.ERROR_INDEX).
            addThrowable(assertion);
  }


  public synchronized void endTest(Test test) {
    stopMeter(test);
    Packet packet = myRegistry.createPacket().setTestState(test, PoolOfTestStates.COMPLETE_INDEX);
    myCurrentTestMeter.writeTo(packet);
    packet.send();
    myRegistry.forget(test);
  }

  private void stopMeter(Test test) {
    if (!test.equals(myCurrentTest)) {
      myCurrentTestMeter = new TestMeter();
      //noinspection HardCodedStringLiteral
      System.err.println("Wrong test finished. Last started: " + myCurrentTest+" stopped: " + test+"; "+test.getClass());
    }
    myCurrentTestMeter.stop();
  }

  private void switchOutput(Packet switchPacket) {
    switchPacket.send();
    switchPacket.sendThrough(myErr);
  }

  public synchronized void startTest(Test test) {
    myCurrentTest = test;
    myRegistry.createPacket().setTestState(test, PoolOfTestStates.RUNNING_INDEX).send();
    switchOutput(myRegistry.createPacket().switchInputTo(test));
    myCurrentTestMeter = new TestMeter();
  }
}
