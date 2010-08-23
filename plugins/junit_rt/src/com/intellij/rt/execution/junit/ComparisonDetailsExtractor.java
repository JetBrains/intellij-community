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
package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistry;
import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import junit.framework.ComparisonFailure;

import java.lang.reflect.Field;

/**
 * @noinspection HardCodedStringLiteral
 */
public class ComparisonDetailsExtractor extends ExceptionPacketFactory {
  private static Field EXPECTED_FIELD = null;
  private static Field ACTUAL_FIELD = null;
  protected String myActual = "";
  protected String myExpected = "";

  static {
    try {
      Class exceptionClass = ComparisonFailure.class;
      exceptionClass.getDeclaredField("fExpected");
      EXPECTED_FIELD = exceptionClass.getDeclaredField("fExpected");
      EXPECTED_FIELD.setAccessible(true);
      ACTUAL_FIELD = exceptionClass.getDeclaredField("fActual");
      ACTUAL_FIELD.setAccessible(true);
    } catch (Throwable e) {}
  }

  public ComparisonDetailsExtractor(Throwable assertion, String expected, String actual) {
    super(PoolOfTestStates.COMPARISON_FAILURE, assertion);
    myActual = actual;
    myExpected = expected;
  }

  public static ExceptionPacketFactory create(Throwable assertion) {
    try {
      return create(assertion, getExpected(assertion), getActual(assertion));
    }
    catch (Throwable e) {
      return new ExceptionPacketFactory(PoolOfTestStates.FAILED_INDEX, assertion);
    }
  }

  public static String getActual(Throwable assertion) throws IllegalAccessException, NoSuchFieldException {
    String actual;
    if (assertion instanceof ComparisonFailure) {
      actual = (String)ACTUAL_FIELD.get(assertion);
    }
    else {
      Field field = assertion.getClass().getDeclaredField("fActual");
      field.setAccessible(true);
      actual = (String)field.get(assertion);
    }
    return actual;
  }

  public static String getExpected(Throwable assertion) throws IllegalAccessException, NoSuchFieldException {
    if (assertion instanceof ComparisonFailure) {
      return (String)EXPECTED_FIELD.get(assertion);
    }
    else {
      Field field = assertion.getClass().getDeclaredField("fExpected");
      field.setAccessible(true);
      return (String)field.get(assertion);
    }
  }

  public static ExceptionPacketFactory create(Throwable assertion, final String expected, String actual) {
    return new ComparisonDetailsExtractor(assertion, expected, actual);
  }

  public Packet createPacket(OutputObjectRegistry registry, Object test) {
    Packet packet = super.createPacket(registry, test);
    packet.
        addLimitedString(myExpected).
        addLimitedString(myActual);
    return packet;
  }
}
