// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.history;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.export.TestResultsXmlFormatter;
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.Stack;
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

  public ImportedTestContentHandler(GeneralTestEventsProcessor processor) {
    myProcessor = processor;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    if (TestResultsXmlFormatter.ELEM_SUITE.equals(qName)) {
      final String suiteName = StringUtil.unescapeXml(attributes.getValue(TestResultsXmlFormatter.ATTR_NAME));
      myProcessor.onSuiteStarted(new TestSuiteStartedEvent(suiteName, 
                                                           StringUtil.unescapeXml(attributes.getValue(TestResultsXmlFormatter.ATTR_LOCATION)),
                                                           StringUtil.unescapeXml(attributes.getValue(TestResultsXmlFormatter.ATTR_METAINFO))));
      mySuites.push(suiteName);
    }
    else if (TestResultsXmlFormatter.ELEM_TEST.equals(qName)) {
      final String name = StringUtil.unescapeXml(attributes.getValue(TestResultsXmlFormatter.ATTR_NAME));
      myCurrentTest = name;
      myDuration = attributes.getValue(TestResultsXmlFormatter.ATTR_DURATION);
      myStatus = attributes.getValue(TestResultsXmlFormatter.ATTR_STATUS);
      final String isConfig = attributes.getValue(TestResultsXmlFormatter.ATTR_CONFIG);
      final TestStartedEvent startedEvent = new TestStartedEvent(name, 
                                                                 StringUtil.unescapeXml(attributes.getValue(TestResultsXmlFormatter.ATTR_LOCATION)),
                                                                 StringUtil.unescapeXml(attributes.getValue(TestResultsXmlFormatter.ATTR_METAINFO)));
      if (isConfig != null && Boolean.valueOf(isConfig)) {
        startedEvent.setConfig(true);
      }
      myProcessor.onTestStarted(startedEvent);
      currentValue.setLength(0);
    }
    else if (TestResultsXmlFormatter.ELEM_OUTPUT.equals(qName)) {
      myErrorOutput = Comparing.equal(attributes.getValue(TestResultsXmlFormatter.ATTR_OUTPUT_TYPE), "stderr");
      currentValue.setLength(0);
    }
    else if (TestResultsXmlFormatter.ROOT_ELEM.equals(qName)) {
      myProcessor.onRootPresentationAdded(attributes.getValue("name"), attributes.getValue("comment"), attributes.getValue("location"));
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    currentValue.append(ch, start, length);
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    final String currentText = StringUtil.unescapeXml(currentValue.toString());
    final boolean isTestOutput = myCurrentTest == null || TestResultsXmlFormatter.STATUS_PASSED.equals(myStatus) || myErrorOutput;
    if (isTestOutput) {
      currentValue.setLength(0);
    }
    if (TestResultsXmlFormatter.ELEM_SUITE.equals(qName)) {
      myProcessor.onSuiteFinished(new TestSuiteFinishedEvent(mySuites.pop()));
    }
    else if (TestResultsXmlFormatter.ELEM_TEST.equals(qName)) {
      final boolean isError = TestResultsXmlFormatter.STATUS_ERROR.equals(myStatus);
      if (TestResultsXmlFormatter.STATUS_FAILED.equals(myStatus) || isError) {
        myProcessor.onTestFailure(new TestFailedEvent(myCurrentTest, "", currentText, isError, null, null));
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
