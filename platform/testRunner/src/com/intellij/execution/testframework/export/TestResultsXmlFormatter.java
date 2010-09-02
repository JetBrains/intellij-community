/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.execution.testframework.export;

import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.LinkedHashMap;
import java.util.Map;

// this class generates resulting XML compatible to that of XMLJUnitResultFormatter

public class TestResultsXmlFormatter {

  // see org.apache.tools.ant.taskdefs.optional.junit.XmlConstants
  private static final String TESTSUITES = "testsuites";
  private static final String TESTSUITE = "testsuite";
  private static final String TESTCASE = "testcase";
  private static final String FAILURE = "failure";
  private static final String ATTR_NAME = "name";
  private static final String ATTR_FAILURES = "failures";
  private static final String ATTR_TESTS = "tests";

  
  private static final Logger LOG = Logger.getInstance(TestResultsXmlFormatter.class.getName());

  private final RuntimeConfiguration myRuntimeConfiguration;
  private final ContentHandler myResultHandler;
  private final AbstractTestProxy myTestRoot;

  public static void execute(AbstractTestProxy root, RuntimeConfiguration runtimeConfiguration, ContentHandler resultHandler)
    throws SAXException {
    new TestResultsXmlFormatter(root, runtimeConfiguration, resultHandler).execute();
  }

  private TestResultsXmlFormatter(AbstractTestProxy root, RuntimeConfiguration runtimeConfiguration, ContentHandler resultHandler) {
    myRuntimeConfiguration = runtimeConfiguration;
    myTestRoot = root;
    myResultHandler = resultHandler;
  }

  private void execute() throws SAXException {
    // we'll try to represent tree of any depth as the classic "Suite-Test" tree of a depth one

    // first, figure out the maximum depth
    final Ref<Integer> maxDepth = new Ref<Integer>(0);
    AbstractTestProxy testRoot = myTestRoot;
    visit(testRoot, 0, false, new PairProcessor<AbstractTestProxy, Integer>() {
      @Override
      public boolean process(AbstractTestProxy node, Integer depth) {
        maxDepth.set(Math.max(maxDepth.get(), depth));
        return true;
      }
    });

    if (maxDepth.get() > 1 && testRoot.getChildren().size() == 1) {
      testRoot = testRoot.getChildren().get(0);
      maxDepth.set(maxDepth.get() - 1);
    }

    myResultHandler.startDocument();
    startElement(TESTSUITES, new LinkedHashMap<String, String>());

    final Ref<SAXException> error = Ref.create(null);
    visit(testRoot, 0, true, new PairProcessor<AbstractTestProxy, Integer>() {
      @Override
      public boolean process(AbstractTestProxy node, Integer depth) {
        if (depth == maxDepth.get() - 1) {
          try {
            processSuite(node);
          }
          catch (SAXException e) {
            error.set(e);
            return false;
          }
        }
        return true;
      }
    });

    if (!error.isNull()) {
      throw error.get();
    }

    endElement(TESTSUITES);
    myResultHandler.endDocument();
  }

  private void processSuite(AbstractTestProxy suite) throws SAXException {
    int succeeded = 0;
    int failed = 0;
    int interrupted = 0;

    for (AbstractTestProxy test : suite.getChildren()) {
      if (test.isPassed()) {
        succeeded++;
      }
      else if (test.isDefect()) {
        failed++;
      }
      else if (test.isInterrupted()) {
        interrupted++;
      }
      else {
        LOG.error("Unexpected test status: " + test);
      }
    }

    StringBuilder suiteName = new StringBuilder();
    for (AbstractTestProxy node = suite; node != null; node = node.getParent()) {
      if (suiteName.length() > 0) {
        suiteName.insert(0, " - ");
      }
      suiteName.insert(0, node.getName());
    }

    LinkedHashMap<String, String> suiteAttrs = new LinkedHashMap<String, String>();
    suiteAttrs.put(ATTR_NAME, suiteName.toString());
    suiteAttrs.put(ATTR_TESTS, String.valueOf(succeeded + failed + interrupted));
    suiteAttrs.put(ATTR_FAILURES, String.valueOf(failed));
    startElement(TESTSUITE, suiteAttrs);

    for (AbstractTestProxy test : suite.getChildren()) {
      LinkedHashMap<String, String> testAttrs = new LinkedHashMap<String, String>();
      testAttrs.put(ATTR_NAME, test.getName());
      startElement(TESTCASE, testAttrs);
      if (test.isDefect()) {
        startElement(FAILURE, new LinkedHashMap<String, String>());
        final StringBuilder output = new StringBuilder();
        test.printOn(new Printer() {
          @Override
          public void print(String text, ConsoleViewContentType contentType) {
            output.append(text);
          }

          @Override
          public void onNewAvailable(@NotNull Printable printable) {
          }

          @Override
          public void printHyperlink(String text, HyperlinkInfo info) {
          }

          @Override
          public void mark() {
          }
        });
        writeText(output.toString());
        endElement(FAILURE);
      }
      endElement(TESTCASE);
    }
    endElement(TESTSUITE);
  }

  private static boolean visit(AbstractTestProxy node, int depth, boolean visitSelf, PairProcessor<AbstractTestProxy, Integer> processor) {
    if (visitSelf) {
      if (!processor.process(node, depth)) {
        return false;
      }
    }
    for (AbstractTestProxy child : node.getChildren()) {
      if (!visit(child, depth + 1, true, processor)) {
        return false;
      }
    }
    return true;
  }

  private void startElement(String name, LinkedHashMap<String, String> attributes) throws SAXException {
    AttributesImpl attrs = new AttributesImpl();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      attrs.addAttribute("", entry.getKey(), entry.getKey(), "CDATA", entry.getValue());
    }
    myResultHandler.startElement("", name, name, attrs);
  }

  private void endElement(String name) throws SAXException {
    myResultHandler.endElement("", name, name);
  }

  private void writeText(String text) throws SAXException {
    final char[] chars = text.toCharArray();
    myResultHandler.characters(chars, 0, chars.length);
  }

}
