// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.history;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.export.TestResultsXmlFormatter;
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor;
import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestIgnoredEvent;
import com.intellij.execution.testframework.sm.runner.events.TestOutputEvent;
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.Stack;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ImportedTestContentHandler extends DefaultHandler {
  private final GeneralTestEventsProcessor myProcessor;
  private final Stack<String> mySuites = new Stack<>();
  private String myCurrentTest;
  private String myDuration;
  private String myStatus;
  private final StringBuilder currentValue = new StringBuilder();
  private boolean myErrorOutput = false;
  private String myExpected;
  private String myActual;

  public ImportedTestContentHandler(GeneralTestEventsProcessor processor) {
    myProcessor = processor;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    if (TestResultsXmlFormatter.ELEM_SUITE.equals(qName)) {
      final String suiteName = StringUtil.unescapeXmlEntities(attributes.getValue(TestResultsXmlFormatter.ATTR_NAME));
      String locationValue = attributes.getValue(TestResultsXmlFormatter.ATTR_LOCATION);
      String metaValue = attributes.getValue(TestResultsXmlFormatter.ATTR_METAINFO);
      TestSuiteStartedEvent startedEvent = new TestSuiteStartedEvent(suiteName,
                                                                     locationValue == null ? null : StringUtil.unescapeXmlEntities(locationValue),
                                                                     metaValue == null ? null : StringUtil.unescapeXmlEntities(metaValue));
      myProcessor.onSuiteStarted(startedEvent);
      mySuites.push(suiteName);
    }
    else if (TestResultsXmlFormatter.ELEM_TEST.equals(qName)) {
      final String name = StringUtil.unescapeXmlEntities(attributes.getValue(TestResultsXmlFormatter.ATTR_NAME));
      myCurrentTest = name;
      myDuration = attributes.getValue(TestResultsXmlFormatter.ATTR_DURATION);
      myStatus = attributes.getValue(TestResultsXmlFormatter.ATTR_STATUS);
      final String isConfig = attributes.getValue(TestResultsXmlFormatter.ATTR_CONFIG);
      String locationValue = attributes.getValue(TestResultsXmlFormatter.ATTR_LOCATION);
      String metaValue = attributes.getValue(TestResultsXmlFormatter.ATTR_METAINFO);
      final TestStartedEvent startedEvent = new TestStartedEvent(name,
                                                                 locationValue == null ? null : StringUtil.unescapeXmlEntities(locationValue),
                                                                 metaValue == null ? null : StringUtil.unescapeXmlEntities(metaValue));
      if (isConfig != null && Boolean.parseBoolean(isConfig)) {
        startedEvent.setConfig(true);
      }
      myProcessor.onTestStarted(startedEvent);
      currentValue.setLength(0);
    }
    else if (TestResultsXmlFormatter.ELEM_OUTPUT.equals(qName)) {
      boolean isError = Objects.equals(attributes.getValue(TestResultsXmlFormatter.ATTR_OUTPUT_TYPE), "stderr");
      if (isError || !myErrorOutput) {
        currentValue.setLength(0);
      }
      myErrorOutput = isError;
    }
    else if (TestResultsXmlFormatter.ROOT_ELEM.equals(qName)) {
      myProcessor.onRootPresentationAdded(attributes.getValue("name"), attributes.getValue("comment"), attributes.getValue("location"));
    }
    else if (TestResultsXmlFormatter.DIFF.equals(qName)) {
      myExpected = attributes.getValue(TestResultsXmlFormatter.EXPECTED);
      myActual = attributes.getValue(TestResultsXmlFormatter.ACTUAL);
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    currentValue.append(ch, start, length);
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    final String currentText = StringUtil.unescapeXmlEntities(currentValue.toString());
    final boolean isTestOutput = myCurrentTest == null || TestResultsXmlFormatter.STATUS_PASSED.equals(myStatus) || !myErrorOutput;
    if (isTestOutput) {
      currentValue.setLength(0);
    }
    if (TestResultsXmlFormatter.ELEM_SUITE.equals(qName)) {
      myProcessor.onSuiteFinished(new TestSuiteFinishedEvent(mySuites.pop()));
    }
    else if (TestResultsXmlFormatter.ELEM_TEST.equals(qName)) {
      final boolean isError = TestResultsXmlFormatter.STATUS_ERROR.equals(myStatus);
      if (TestResultsXmlFormatter.STATUS_FAILED.equals(myStatus) || isError) {
        myProcessor.onTestFailure(new TestFailedEvent(myCurrentTest, "", currentText, isError, myActual, myExpected));
      }
      else if (TestResultsXmlFormatter.STATUS_IGNORED.equals(myStatus) || TestResultsXmlFormatter.STATUS_SKIPPED.equals(myStatus)) {
        myProcessor.onTestIgnored(new TestIgnoredEvent(myCurrentTest, "", currentText) {
          @NotNull
          @Override
          public String getIgnoreComment() {
            return "";
          }
        });
      }
      myProcessor.onTestFinished(new TestFinishedEvent(myCurrentTest, myDuration != null ? Long.parseLong(myDuration) : -1));
      if (!TestResultsXmlFormatter.STATUS_PASSED.equals(myStatus)) {
        currentValue.setLength(0);
      }
      myCurrentTest = null;
      myActual = null;
      myExpected = null;
    }
    else if (TestResultsXmlFormatter.ELEM_OUTPUT.equals(qName) && !StringUtil.isEmpty(currentText) && isTestOutput) {
      if (myCurrentTest != null) {
        myProcessor.onTestOutput(new TestOutputEvent(myCurrentTest, currentText, !myErrorOutput));
      }
      else {
        myProcessor.onUncapturedOutput(currentText, myErrorOutput ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT);
      }
    }
  }
}
