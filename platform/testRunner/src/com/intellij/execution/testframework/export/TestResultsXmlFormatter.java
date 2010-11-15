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
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.HashMap;
import java.util.Map;

public class TestResultsXmlFormatter {

  private static final String ELEM_RUN = "testrun";
  private static final String ELEM_TEST = "test";
  private static final String ELEM_SUITE = "suite";
  private static final String ATTR_NAME = "name";
  private static final String ATTR_DURATION = "duration";
  private static final String ATTR_TOTAL = "total";
  private static final String ATTR_PASSED = "passed";
  private static final String ATTR_FAILED = "failed";
  private static final String ATTR_STATUS = "status";

  private final RuntimeConfiguration myRuntimeConfiguration;
  private final ContentHandler myResultHandler;
  private final AbstractTestProxy myTestRoot;
  private static final String ELEM_OUTPUT = "output";
  private static final String ATTR_OUTPUT_TYPE = "type";

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
    myResultHandler.startDocument();

    int total = 0;
    int passed = 0;
    for (AbstractTestProxy node : myTestRoot.getAllTests()) {
      if (!node.isLeaf()) continue;
      total++;
      if (node.isPassed()) passed++;
    }

    Map<String, String> attrs = new HashMap<String, String>();
    attrs.put(ATTR_NAME, myRuntimeConfiguration.getName());
    Integer duration = myTestRoot.getDuration();
    if (duration != null) {
      attrs.put(ATTR_DURATION, String.valueOf(duration));
    }
    attrs.put(ATTR_TOTAL, String.valueOf(total));
    attrs.put(ATTR_PASSED, String.valueOf(passed));
    attrs.put(ATTR_FAILED, String.valueOf(total - passed));
    startElement(ELEM_RUN, attrs);
    if (myTestRoot.shouldSkipRootNodeForExport()) {
      for (AbstractTestProxy node : myTestRoot.getChildren()) {
        processNode(node);
      }
    }
    else {
      processNode(myTestRoot);
    }
    endElement(ELEM_RUN);
    myResultHandler.endDocument();
  }

  private void processNode(AbstractTestProxy node) throws SAXException {
    Map<String, String> attrs = new HashMap<String, String>();
    attrs.put(ATTR_NAME, node.getName());
    attrs.put(ATTR_STATUS, getStatusString(node));
    Integer duration = node.getDuration();
    if (duration != null) {
      attrs.put(ATTR_DURATION, String.valueOf(duration));
    }
    String elemName = node.isLeaf() ? ELEM_TEST : ELEM_SUITE;
    startElement(elemName, attrs);
    if (node.isLeaf()) {
      final StringBuilder buffer = new StringBuilder();
      final Ref<ConsoleViewContentType> lastType = new Ref<ConsoleViewContentType>();
      final Ref<SAXException> error = new Ref<SAXException>();
      node.printOn(new Printer() {
        @Override
        public void print(String text, ConsoleViewContentType contentType) {
          if (contentType != lastType.get()) {
            if (buffer.length() > 0) {
              try {
                Map<String, String> a = new HashMap<String, String>();
                a.put(ATTR_OUTPUT_TYPE, getTypeString(lastType.get()));
                startElement(ELEM_OUTPUT, a);
                writeText(buffer.toString());
                buffer.delete(0, buffer.length());
                endElement(ELEM_OUTPUT);
              }
              catch (SAXException e) {
                error.set(e);
              }
            }
            lastType.set(contentType);
          }
          buffer.append(text);
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
      if (!error.isNull()) {
        throw error.get();
      }
      if (buffer.length() > 0) {
        Map<String, String> a = new HashMap<String, String>();
        a.put(ATTR_OUTPUT_TYPE, lastType.toString());
        startElement(ELEM_OUTPUT, a);
        writeText(buffer.toString());
        endElement(ELEM_OUTPUT);
      }
    }
    else {
      for (AbstractTestProxy child : node.getChildren()) {
        processNode(child);
      }
    }
    endElement(elemName);
  }

  private static String getTypeString(ConsoleViewContentType type) {
    return type == ConsoleViewContentType.ERROR_OUTPUT ? "error" : "normal";
  }

  private static String getStatusString(AbstractTestProxy node) {
    if (node.isPassed()) return "passed";
    return "failed"; // TODO
  }

  private void startElement(String name, Map<String, String> attributes) throws SAXException {
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
