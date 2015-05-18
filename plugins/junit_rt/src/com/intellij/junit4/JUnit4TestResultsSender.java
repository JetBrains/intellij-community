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
package com.intellij.junit4;

import com.intellij.rt.execution.junit.*;
import com.intellij.rt.execution.junit.segments.OutputObjectRegistry;
import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import junit.framework.ComparisonFailure;
import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JUnit4TestResultsSender extends RunListener {
  private static final String JUNIT_FRAMEWORK_COMPARISON_NAME = ComparisonFailure.class.getName();
  private static final String ORG_JUNIT_COMPARISON_NAME = "org.junit.ComparisonFailure";

  private final OutputObjectRegistry myRegistry;
  private Map myCurrentTestMeters = new HashMap();
  private Set myCurrentTests = new HashSet();

  public JUnit4TestResultsSender(OutputObjectRegistry packetFactory) {
    myRegistry = packetFactory;
  }

  public synchronized void testFailure(Failure failure) throws Exception {
    final Description description = failure.getDescription();
    final Throwable throwable = failure.getException();

    final Throwable cause = throwable.getCause();
    if (ComparisonFailureData.isAssertionError(throwable.getClass()) || 
        ComparisonFailureData.isAssertionError(cause != null ? cause.getClass() : null)) {
      // junit4 makes no distinction between errors and failures
      doAddFailure(description, throwable);
    }

    else {
      prepareDefectPacket(description, throwable).send();
    }
  }

  public void testAssumptionFailure(Failure failure) {
    final Description description = failure.getDescription();
    prepareIgnoredPacket(description, null).addThrowable(failure.getException()).send();
  }

  public synchronized void testIgnored(Description description) throws Exception {
    String val = null;
    try {
      final Ignore ignoredAnnotation = (Ignore)description.getAnnotation(Ignore.class);
      if (ignoredAnnotation != null) {
        val = ignoredAnnotation.value();
      }
    }
    catch (NoSuchMethodError ignored) {
      //junit < 4.4
    }
    testStarted(description);
    stopMeter(description);
    prepareIgnoredPacket(description, val).addLimitedString("").addLimitedString("").send();
    myRegistry.forget(description);
  }

  private void doAddFailure(final Description test, final Throwable assertion) {
    createExceptionNotification(assertion).createPacket(myRegistry, test).send();
  }

  private static boolean isComparisonFailure(Throwable throwable) {
    if (throwable == null) return false;
    return isComparisonFailure(throwable.getClass());
  }

  private static boolean isComparisonFailure(Class aClass) {
    if (aClass == null) return false;
    final String throwableClassName = aClass.getName();
    if (throwableClassName.equals(JUNIT_FRAMEWORK_COMPARISON_NAME) || throwableClassName.equals(ORG_JUNIT_COMPARISON_NAME)) return true;
    return isComparisonFailure(aClass.getSuperclass());
  }

  private static PacketFactory createExceptionNotification(Throwable assertion) {
    if (assertion instanceof KnownException) return ((KnownException)assertion).getPacketFactory();
    final ComparisonFailureData notification = JUnit4TestListener.createExceptionNotification(assertion);
    if (notification != null) {
      return ComparisonDetailsExtractor.create(assertion, notification.getExpected(), notification.getActual());
    }
    return new ExceptionPacketFactory(PoolOfTestStates.FAILED_INDEX, assertion);
  }

  private Packet prepareDefectPacket(Description test, Throwable assertion) {
    return myRegistry.createPacket().
            setTestState(test, PoolOfTestStates.ERROR_INDEX).
            addThrowable(assertion);
  }
  private Packet prepareIgnoredPacket(Description test, String val) {
    return myRegistry.createPacket().setTestState(test, PoolOfTestStates.IGNORED_INDEX).addObject(test).addLimitedString(val != null ? val : "");
  }

  public void testFinished(Description description) throws Exception {
    final Object testMeter = myCurrentTestMeters.get(description);
    stopMeter(description);
    Packet packet = myRegistry.createPacket().setTestState(description, PoolOfTestStates.COMPLETE_INDEX);
    ((TestMeter)testMeter).writeTo(packet);
    packet.send();
    myRegistry.forget(description);
  }

  private void stopMeter(Description test) {
    if (!myCurrentTests.remove(test)) {
      myCurrentTestMeters.put(test, new TestMeter());
      //noinspection HardCodedStringLiteral
      System.err.println("Wrong test finished. Last started: " + myCurrentTests +" stopped: " + test+"; "+test.getClass());
    }
    final Object stopMeter = myCurrentTestMeters.remove(test);
    if (stopMeter instanceof TestMeter) {
      ((TestMeter)stopMeter).stop();
    }
  }

  private void switchOutput(Packet switchPacket) {
    switchPacket.send();
  }


  public synchronized void testStarted(Description description) throws Exception {
    myCurrentTests.add(description);
    myRegistry.createPacket().setTestState(description, PoolOfTestStates.RUNNING_INDEX).send();
    switchOutput(myRegistry.createPacket().switchInputTo(description));
    myCurrentTestMeters.put(description, new TestMeter());
  }

}
