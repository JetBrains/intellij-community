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

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.filters.*;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.text.SimpleDateFormat;
import java.util.*;

public class TestResultsXmlFormatter {

  private static final String ELEM_RUN = "testrun";
  private static final String ELEM_TEST = "test";
  private static final String ELEM_SUITE = "suite";
  private static final String ATTR_NAME = "name";
  private static final String ATTR_DURATION = "duration";
  private static final String ELEM_COUNT = "count";
  private static final String ATTR_VALUE = "value";
  private static final String ELEM_OUTPUT = "output";
  private static final String ATTR_OUTPUT_TYPE = "type";
  private static final String ATTR_STATUS = "status";
  private static final String TOTAL_STATUS = "total";
  private static final String ATTR_FOORTER_TEXT = "footerText";

  private final RunConfiguration myRuntimeConfiguration;
  private final ContentHandler myResultHandler;
  private final AbstractTestProxy myTestRoot;

  public static void execute(AbstractTestProxy root, RunConfiguration runtimeConfiguration, ContentHandler resultHandler)
    throws SAXException {
    new TestResultsXmlFormatter(root, runtimeConfiguration, resultHandler).execute();
  }

  private TestResultsXmlFormatter(AbstractTestProxy root, RunConfiguration runtimeConfiguration, ContentHandler resultHandler) {
    myRuntimeConfiguration = runtimeConfiguration;
    myTestRoot = root;
    myResultHandler = resultHandler;
  }

  private void execute() throws SAXException {
    myResultHandler.startDocument();

    TreeMap<String, Integer> counts = new TreeMap<String, Integer>(new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        if (TOTAL_STATUS.equals(o1) && !TOTAL_STATUS.equals(o2)) return -1;
        if (TOTAL_STATUS.equals(o2) && !TOTAL_STATUS.equals(o1)) return 1;
        return o1.compareTo(o2);
      }
    });
    for (AbstractTestProxy node : myTestRoot.getAllTests()) {
      if (!node.isLeaf()) continue;
      String status = getStatusString(node);
      increment(counts, status);
      increment(counts, TOTAL_STATUS);
    }

    Map<String, String> runAttrs = new HashMap<String, String>();
    runAttrs.put(ATTR_NAME, myRuntimeConfiguration.getName());
    String footerText = ExecutionBundle.message("export.test.results.footer", ApplicationNamesInfo.getInstance().getFullProductName(),
                                                new SimpleDateFormat().format(new Date()));
    runAttrs.put(ATTR_FOORTER_TEXT, footerText);
    Long duration = myTestRoot.getDuration();
    if (duration != null) {
      runAttrs.put(ATTR_DURATION, String.valueOf(duration));
    }
    startElement(ELEM_RUN, runAttrs);

    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      Map<String, String> a = new HashMap<String, String>();
      a.put(ATTR_NAME, entry.getKey());
      a.put(ATTR_VALUE, String.valueOf(entry.getValue()));
      startElement(ELEM_COUNT, a);
      endElement(ELEM_COUNT);
    }

    CompositeFilter f = new CompositeFilter(myRuntimeConfiguration.getProject());
    for (ConsoleFilterProvider eachProvider : Extensions.getExtensions(ConsoleFilterProvider.FILTER_PROVIDERS)) {
      Filter[] filters = eachProvider.getDefaultFilters(myRuntimeConfiguration.getProject());
      for (Filter filter : filters) {
        f.addFilter(filter);
      }
    }


    if (myTestRoot.shouldSkipRootNodeForExport()) {
      for (AbstractTestProxy node : myTestRoot.getChildren()) {
        processNode(node, f);
      }
    }
    else {
      processNode(myTestRoot, f);
    }
    endElement(ELEM_RUN);
    myResultHandler.endDocument();
  }

  private static void increment(Map<String, Integer> counts, String status) {
    Integer count = counts.get(status);
    counts.put(status, count != null ? count + 1 : 1);
  }

  private void processNode(AbstractTestProxy node, final Filter filter) throws SAXException {
    Map<String, String> attrs = new HashMap<String, String>();
    attrs.put(ATTR_NAME, node.getName());
    attrs.put(ATTR_STATUS, getStatusString(node));
    Long duration = node.getDuration();
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
                writeOutput(lastType.get(), buffer, filter);
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
        writeOutput(lastType.get(), buffer, filter);
      }
    }
    else {
      for (AbstractTestProxy child : node.getChildren()) {
        processNode(child, filter);
      }
    }
    endElement(elemName);
  }

  private void writeOutput(ConsoleViewContentType type, StringBuilder text, Filter filter) throws SAXException {
    StringBuilder output = new StringBuilder();
    StringTokenizer t = new StringTokenizer(text.toString(), "\n");
    while (t.hasMoreTokens()) {
      String line = StringUtil.escapeXml(t.nextToken()) + "\n";
      Filter.Result result = null;//filter.applyFilter(line, line.length());
      if (result != null && result.hyperlinkInfo instanceof OpenFileHyperlinkInfo) {
        output.append(line.substring(0, result.highlightStartOffset));
        OpenFileDescriptor descriptor = ((OpenFileHyperlinkInfo)result.hyperlinkInfo).getDescriptor();
        output.append("<a href=\"javascript://\" onclick=\"Activator.doOpen('file?file=");
        output.append(descriptor.getFile().getPresentableUrl());
        output.append("&line=");
        output.append(descriptor.getLine());
        output.append("')\">");
        output.append(line.substring(result.highlightStartOffset, result.highlightEndOffset));
        output.append("</a>");
        output.append(line.substring(result.highlightEndOffset));
      }
      else {
        output.append(line);
      }
    }

    Map<String, String> a = new HashMap<String, String>();
    a.put(ATTR_OUTPUT_TYPE, getTypeString(type));
    startElement(ELEM_OUTPUT, a);
    writeText(output.toString());
    text.delete(0, text.length());
    endElement(ELEM_OUTPUT);
  }

  private static String getTypeString(ConsoleViewContentType type) {
    return type == ConsoleViewContentType.ERROR_OUTPUT ? "stderr" : "stdout";
  }

  private static String getStatusString(AbstractTestProxy node) {
    int magnitude = node.getMagnitude();
    // TODO enumeration!
    switch (magnitude) {
      case 0:
        return "skipped";
      case 5:
        return "ignored";
      case 1:
        return "passed";
      case 6:
        return "failed";
      case 8:
        return "error";
      default:
        return node.isPassed() ? "passed" : "failed";
    }
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
