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

import com.intellij.execution.DefaultExecutionTarget;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.filters.*;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.execution.testframework.*;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.text.SimpleDateFormat;
import java.util.*;

public class TestResultsXmlFormatter {

  private static final String ELEM_RUN = "testrun";
  public static final String ELEM_TEST = "test";
  public static final String ELEM_SUITE = "suite";
  public static final String ATTR_NAME = "name";
  public static final String ATTR_DURATION = "duration";
  public static final String ATTR_LOCATION = "locationUrl";
  public static final String ELEM_COUNT = "count";
  public static final String ATTR_VALUE = "value";
  public static final String ELEM_OUTPUT = "output";
  public static final String ATTR_OUTPUT_TYPE = "type";
  public static final String ATTR_STATUS = "status";
  public static final String TOTAL_STATUS = "total";
  private static final String ATTR_FOORTER_TEXT = "footerText";
  public static final String ATTR_CONFIG = "isConfig";
  public static final String STATUS_PASSED = "passed";
  public static final String STATUS_FAILED = "failed";
  public static final String STATUS_ERROR = "error";
  public static final String STATUS_IGNORED = "ignored";
  public static final String STATUS_SKIPPED = "skipped";

  public static final String ROOT_ELEM = "root";
  

  private final RunConfiguration myRuntimeConfiguration;
  private final ContentHandler myResultHandler;
  private final AbstractTestProxy myTestRoot;
  private final boolean myHidePassedConfig;
  private final ExecutionTarget myExecutionTarget;

  public static void execute(AbstractTestProxy root, RunConfiguration runtimeConfiguration, TestConsoleProperties properties, ContentHandler resultHandler)
    throws SAXException {
    new TestResultsXmlFormatter(root, runtimeConfiguration, properties, resultHandler).execute();
  }

  private TestResultsXmlFormatter(AbstractTestProxy root,
                                  RunConfiguration runtimeConfiguration,
                                  TestConsoleProperties properties,
                                  ContentHandler resultHandler) {
    myRuntimeConfiguration = runtimeConfiguration;
    myTestRoot = root;
    myResultHandler = resultHandler;
    myHidePassedConfig = TestConsoleProperties.HIDE_SUCCESSFUL_CONFIG.value(properties);
    myExecutionTarget = properties.getExecutionTarget();
  }

  private void execute() throws SAXException {
    myResultHandler.startDocument();

    TreeMap<String, Integer> counts = new TreeMap<>((o1, o2) -> {
      if (TOTAL_STATUS.equals(o1) && !TOTAL_STATUS.equals(o2)) return -1;
      if (TOTAL_STATUS.equals(o2) && !TOTAL_STATUS.equals(o1)) return 1;
      return o1.compareTo(o2);
    });
    for (AbstractTestProxy node : myTestRoot.getAllTests()) {
      if (!node.isLeaf()) continue;
      String status = getStatusString(node);
      increment(counts, status);
      increment(counts, TOTAL_STATUS);
    }

    Map<String, String> runAttrs = new HashMap<>();
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
      Map<String, String> a = new HashMap<>();
      a.put(ATTR_NAME, entry.getKey());
      a.put(ATTR_VALUE, String.valueOf(entry.getValue()));
      startElement(ELEM_COUNT, a);
      endElement(ELEM_COUNT);
    }

    final Element config = new Element("config");
    try {
      myRuntimeConfiguration.writeExternal(config);
      config.setAttribute("configId", myRuntimeConfiguration.getType().getId());
      config.setAttribute("name", myRuntimeConfiguration.getName());
      if (!DefaultExecutionTarget.INSTANCE.equals(myExecutionTarget)) {
        config.setAttribute("target", myExecutionTarget.getId());
      }
    }
    catch (WriteExternalException ignore) {}
    processJDomElement(config);

    CompositeFilter f = new CompositeFilter(myRuntimeConfiguration.getProject());
    for (ConsoleFilterProvider eachProvider : Extensions.getExtensions(ConsoleFilterProvider.FILTER_PROVIDERS)) {
      Filter[] filters = eachProvider.getDefaultFilters(myRuntimeConfiguration.getProject());
      for (Filter filter : filters) {
        f.addFilter(filter);
      }
    }


    if (myTestRoot instanceof TestProxyRoot) {
      final String presentation = ((TestProxyRoot)myTestRoot).getPresentation();
      if (presentation != null) {
        final LinkedHashMap<String, String> rootAttrs = new LinkedHashMap<>();
        rootAttrs.put("name", presentation);
        final String comment = ((TestProxyRoot)myTestRoot).getComment();
        if (comment != null) {
          rootAttrs.put("comment", comment);
        }
        final String rootLocation = ((TestProxyRoot)myTestRoot).getRootLocation();
        if (rootLocation != null) {
          rootAttrs.put("location", rootLocation);
        }
        startElement(ROOT_ELEM, rootAttrs);
        writeOutput(myTestRoot, f);
        endElement(ROOT_ELEM);
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

  private void processJDomElement(Element config) throws SAXException {
    final String name = config.getName();
    final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
    for (Attribute attribute : config.getAttributes()) {
      attributes.put(attribute.getName(), attribute.getValue());
    }
    startElement(name, attributes);
    for (Element child : config.getChildren()) {
      processJDomElement(child);
    }
    endElement(name);
  }

  private static void increment(Map<String, Integer> counts, String status) {
    Integer count = counts.get(status);
    counts.put(status, count != null ? count + 1 : 1);
  }

  private void processNode(AbstractTestProxy node, final Filter filter) throws SAXException {
    ProgressManager.checkCanceled();
    Map<String, String> attrs = new HashMap<>();
    attrs.put(ATTR_NAME, node.getName());
    attrs.put(ATTR_STATUS, getStatusString(node));
    Long duration = node.getDuration();
    if (duration != null) {
      attrs.put(ATTR_DURATION, String.valueOf(duration));
    }
    String locationUrl = node.getLocationUrl();
    if (locationUrl != null) {
      attrs.put(ATTR_LOCATION, locationUrl);
    }
    if (node.isConfig()) {
      attrs.put(ATTR_CONFIG, "true");
    }
    boolean started = false;
    String elemName = node.isLeaf() ? ELEM_TEST : ELEM_SUITE;
    if (node.isLeaf()) {
      started = true;
      startElement(elemName, attrs);
      writeOutput(node, filter);
    }
    else {
      for (AbstractTestProxy child : node.getChildren()) {
        if (myHidePassedConfig && child.isConfig() && child.isPassed()) {
          //ignore configurations during export
          continue;
        }
        if (!started) {
          started = true;
          startElement(elemName, attrs);
        }
        processNode(child, filter);
      }
    }
    if (started) {
      endElement(elemName);
    }
  }

  private void writeOutput(AbstractTestProxy node, final Filter filter) throws SAXException {
    final StringBuilder buffer = new StringBuilder();
    final Ref<ConsoleViewContentType> lastType = new Ref<>();
    final Ref<SAXException> error = new Ref<>();

    final int bufferSize = ConsoleBuffer.useCycleBuffer() ? ConsoleBuffer.getCycleBufferSize() : -1;
    final Printer printer = new Printer() {
      @Override
      public void print(String text, ConsoleViewContentType contentType) {
        ProgressManager.checkCanceled();
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
        if (bufferSize < 0 || buffer.length() < bufferSize) {
          buffer.append(text);
        }
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
    };
    node.printOwnPrintablesOn(printer);
    if (!error.isNull()) {
      throw error.get();
    }
    if (buffer.length() > 0) {
      writeOutput(lastType.get(), buffer, filter);
    }
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

    Map<String, String> a = new HashMap<>();
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
        return STATUS_SKIPPED;
      case 2:
      case 4:
        return STATUS_SKIPPED;
      case 5:
        return STATUS_IGNORED;
      case 1:
        return STATUS_PASSED;
      case 6:
        return STATUS_FAILED;
      case 8:
        return STATUS_ERROR;
      default:
        return node.isPassed() ? STATUS_PASSED : STATUS_FAILED;
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
