package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistryEx;
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
      String expected;
      if (assertion instanceof ComparisonFailure) {
        expected = (String)EXPECTED_FIELD.get(assertion);
      }
      else {
        Field field = assertion.getClass().getDeclaredField("fExpected");
        field.setAccessible(true);
        expected = (String)field.get(assertion);
      }
      String actual;
      if (assertion instanceof ComparisonFailure) {
        actual = (String)ACTUAL_FIELD.get(assertion);
      }
      else {
        Field field = assertion.getClass().getDeclaredField("fActual");
        field.setAccessible(true);
        actual = (String)field.get(assertion);
      }
      return new ComparisonDetailsExtractor(assertion, expected, actual);
    }
    catch (Throwable e) {
      return new ExceptionPacketFactory(PoolOfTestStates.FAILED_INDEX, assertion);
    }
  }

  public Packet createPacket(OutputObjectRegistryEx registry, Object test) {
    Packet packet = super.createPacket(registry, test);
    packet.
        addLimitedString(myExpected).
        addLimitedString(myActual);
    return packet;
  }
}
