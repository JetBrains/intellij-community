/**
 * This file was originally part of [rules_jvm] (https://github.com/bazel-contrib/rules_jvm)
 * Original source:
 * https://github.com/bazel-contrib/rules_jvm/blob/201fa7198cfd50ae4d686715651500da656b368a/java/src/com/github/bazel_contrib/contrib_rules_jvm/junit5/TestCaseXmlRenderer.java
 * Licensed under the Apache License, Version 2.0
 */
package com.intellij.tests.bazel;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.listeners.LegacyReportingUtils;

import static com.intellij.tests.bazel.SafeXml.*;

class TestCaseXmlRenderer {

  private static final DecimalFormatSymbols DECIMAL_FORMAT_SYMBOLS =
    new DecimalFormatSymbols(Locale.ROOT);
  private final TestPlan testPlan;

  public TestCaseXmlRenderer(TestPlan testPlan) {
    this.testPlan = testPlan;
  }

  public void toXml(XMLStreamWriter xml, TestData test) throws XMLStreamException {
    DecimalFormat decimalFormat = new DecimalFormat("#.##", DECIMAL_FORMAT_SYMBOLS);
    decimalFormat.setRoundingMode(RoundingMode.HALF_UP);

    TestIdentifier id = test.getId();

    String name;
    if (test.isDynamic()) {
      name = id.getDisplayName(); // [ordinal] name=value...
    } else {
      // Massage the name
      name = id.getLegacyReportingName();
      int index = name.indexOf('(');
      if (index != -1) {
        name = name.substring(0, index);
      }
    }

    xml.writeStartElement("testcase");
    xml.writeAttribute("name", escapeIllegalCharacters(name));
    xml.writeAttribute("classname", LegacyReportingUtils.getClassName(testPlan, id));

    /* @Nullable */ Duration maybeDuration = test.getDuration();
    boolean wasInterrupted = maybeDuration == null;
    Duration duration =
      maybeDuration == null ? Duration.between(test.getStarted(), Instant.now()) : maybeDuration;
    xml.writeAttribute("time", decimalFormat.format(duration.toMillis() / 1000f));

    if (wasInterrupted) {
      xml.writeStartElement("failure");
      xml.writeCData("Test timed out and was interrupted");
      xml.writeEndElement();
    } else {
      if (test.isDisabled() || test.isSkipped()) {
        xml.writeStartElement("skipped");
        if (test.getSkipReason() != null) {
          xml.writeCData(test.getSkipReason());
        } else {
          writeThrowableMessage(xml, test.getResult());
        }
        xml.writeEndElement();
      }
      if (test.isFailure() || test.isError()) {
        xml.writeStartElement(test.isFailure() ? "failure" : "error");
        writeThrowableMessage(xml, test.getResult());
        xml.writeEndElement();
      }
    }

    writeTextElement(xml, "system-out", test.getStdOut());
    writeTextElement(xml, "system-err", test.getStdErr());

    xml.writeEndElement();
  }

  private void writeThrowableMessage(XMLStreamWriter xml, TestExecutionResult result)
    throws XMLStreamException {
    Throwable throwable = null;
    if (result != null) {
      throwable = result.getThrowable().orElse(null);
    }
    if (throwable == null) {
      // Stub out the values
      xml.writeAttribute("message", "unknown cause");
      xml.writeAttribute("type", RuntimeException.class.getName());
      return;
    }

    xml.writeAttribute("message", escapeIllegalCharacters(String.valueOf(throwable.getMessage())));
    xml.writeAttribute("type", throwable.getClass().getName());

    StringWriter stringWriter = new StringWriter();
    throwable.printStackTrace(new PrintWriter(stringWriter));

    writeCData(xml, stringWriter.toString());
  }
}