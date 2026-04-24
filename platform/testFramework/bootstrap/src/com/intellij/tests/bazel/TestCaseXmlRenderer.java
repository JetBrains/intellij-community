/**
 * This file was originally part of [rules_jvm] (https://github.com/bazel-contrib/rules_jvm)
 * Original source:
 * https://github.com/bazel-contrib/rules_jvm/blob/201fa7198cfd50ae4d686715651500da656b368a/java/src/com/github/bazel_contrib/contrib_rules_jvm/junit5/TestCaseXmlRenderer.java
 * Licensed under the Apache License, Version 2.0
 */
package com.intellij.tests.bazel;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.listeners.LegacyReportingUtils;
import org.opentest4j.AssertionFailedError;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.intellij.tests.bazel.SafeXml.escapeIllegalCharacters;
import static com.intellij.tests.bazel.SafeXml.writeCData;
import static com.intellij.tests.bazel.SafeXml.writeTextElement;

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

    StringWriter stringWriter =  new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    if (throwable instanceof AssertionFailedError error) {
      String expected = error.getExpected().getStringRepresentation();
      String actual = error.getActual().getStringRepresentation();
      printWriter.println("---- expected -------------------------------");
      printWriter.print(expected);
      printWriter.println("---- actual ---------------------------------");
      printWriter.print(actual);
      printWriter.println("---- diff -----------------------------------");
      printWriter.println(computeLineDiff(expected, actual));
      printWriter.println("---------------------------------------------");
    }
    throwable.printStackTrace(printWriter);

    writeCData(xml, stringWriter.toString());
  }

  /**
   * Compares two strings line by line and returns a diff string similar to the Linux `diff` command.
   * Uses the Longest Common Subsequence (LCS) algorithm to determine additions and deletions.
   * <p>
   * '<' indicates a line removed from the original text. '>' indicates a line added in the modified text.
   *
   * @param expected The original string.
   * @param actual The modified string to compare against.
   * @return A formatted diff string.
   */
  private static String computeLineDiff(String expected, String actual) {
    if (expected.equals(actual)) {
      return "";
    }

    // Using split with -1 to ensure trailing empty lines are preserved, mirroring Kotlin's lines()
    String[] lines1 = expected.split("\\R", -1);
    String[] lines2 = actual.split("\\R", -1);

    int m = lines1.length;
    int n = lines2.length;

    // Step 1: Build the LCS matrix
    int[][] dp = new int[m + 1][n + 1];

    for (int i = 1; i <= m; i++) {
      for (int j = 1; j <= n; j++) {
        if (lines1[i - 1].equals(lines2[j - 1])) {
          dp[i][j] = dp[i - 1][j - 1] + 1;
        } else {
          dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
        }
      }
    }

    // Step 2: Backtrack through the matrix to construct the diff output
    List<String> diffOutput = new ArrayList<>();
    int i = m;
    int j = n;

    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && lines1[i - 1].equals(lines2[j - 1])) {
        // Lines are identical; skip and move diagonally
        i--;
        j--;
      } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
        // Addition
        diffOutput.add("> " + lines2[j - 1]);
        j--;
      } else {
        // Deletion
        diffOutput.add("< " + lines1[i - 1]);
        i--;
      }
    }

    // Reverse the list since backtracking starts from the end
    Collections.reverse(diffOutput);
    return String.join("\n", diffOutput);
  }
}