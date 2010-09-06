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
import com.intellij.rt.execution.junit.segments.PacketProcessor;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JUnit4TestResultsSender extends RunListener {
  private final OutputObjectRegistry myRegistry;
  private final PacketProcessor myErr;
  private Map myCurrentTestMeters = new HashMap();
  private Set myCurrentTests = new HashSet();

  public JUnit4TestResultsSender(OutputObjectRegistry packetFactory, PacketProcessor segmentedErr) {
    myRegistry = packetFactory;
    myErr = segmentedErr;
  }

  public synchronized void testFailure(Failure failure) throws Exception {
    final Description description = failure.getDescription();
    final Throwable throwable = failure.getException();

    if (throwable instanceof AssertionError || throwable.getCause() instanceof AssertionError) {
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
  }

  private void doAddFailure(final Description test, final Throwable assertion) {
    createExceptionNotification(assertion).createPacket(myRegistry, test).send();
  }

  private static PacketFactory createExceptionNotification(Throwable assertion) {
    if (assertion instanceof KnownException) return ((KnownException)assertion).getPacketFactory();
    if (assertion instanceof ComparisonFailure || assertion instanceof org.junit.ComparisonFailure) {
      return ComparisonDetailsExtractor.create(assertion);
    }
    final Throwable cause = assertion.getCause();
    if (cause instanceof ComparisonFailure || cause instanceof org.junit.ComparisonFailure) {
      try {
        return ComparisonDetailsExtractor.create(assertion, ComparisonDetailsExtractor.getExpected(cause), ComparisonDetailsExtractor.getActual(cause));
      }
      catch (Throwable ignore) {}
    }

    final Matcher matcher =
      Pattern.compile("\nExpected: \"(.*)\"\n     got: \"(.*)\"\n", Pattern.DOTALL).matcher(assertion.getMessage());
    if (matcher.matches()){
      return ComparisonDetailsExtractor.create(assertion, matcher.group(1).replaceAll("\\\\n", "\n"), matcher.group(2).replaceAll("\\\\n", "\n"));
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
    switchPacket.sendThrough(myErr);
  }


  public synchronized void testStarted(Description description) throws Exception {
    myCurrentTests.add(description);
    myRegistry.createPacket().setTestState(description, PoolOfTestStates.RUNNING_INDEX).send();
    switchOutput(myRegistry.createPacket().switchInputTo(description));
    myCurrentTestMeters.put(description, new TestMeter());
  }

}
