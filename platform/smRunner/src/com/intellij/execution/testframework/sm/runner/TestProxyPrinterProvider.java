/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.TestsOutputConsolePrinter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class TestProxyPrinterProvider {

  private final TestProxyFilterProvider myFilterProvider;
  private final BaseTestsOutputConsoleView myTestOutputConsoleView;

  public TestProxyPrinterProvider(@NotNull BaseTestsOutputConsoleView testsOutputConsoleView,
                                  @NotNull TestProxyFilterProvider filterProvider) {
    myTestOutputConsoleView = testsOutputConsoleView;
    myFilterProvider = filterProvider;
  }

  @Nullable
  public Printer getPrinterByType(@NotNull String nodeType, @NotNull String nodeName, @Nullable String nodeArguments) {
    Filter filter = myFilterProvider.getFilter(nodeType, nodeName, nodeArguments);
    if (filter != null && !Disposer.isDisposed(myTestOutputConsoleView)) {
      return new HyperlinkPrinter(myTestOutputConsoleView, HyperlinkPrinter.ERROR_CONTENT_TYPE, filter);
    }
    return null;
  }

  private static class HyperlinkPrinter extends TestsOutputConsolePrinter {

    public static final Condition<ConsoleViewContentType> ERROR_CONTENT_TYPE =
      contentType -> ConsoleViewContentType.ERROR_OUTPUT == contentType;
    private static final String NL = "\n";

    private final Condition<ConsoleViewContentType> myContentTypeCondition;
    private final Filter myFilter;

    public HyperlinkPrinter(@NotNull BaseTestsOutputConsoleView testsOutputConsoleView,
                            @NotNull Condition<ConsoleViewContentType> contentTypeCondition,
                            @NotNull Filter filter) {
      super(testsOutputConsoleView, testsOutputConsoleView.getProperties(), null);
      myContentTypeCondition = contentTypeCondition;
      myFilter = filter;
    }

    @Override
    public void print(String text, ConsoleViewContentType contentType) {
      if (contentType == null || !myContentTypeCondition.value(contentType)) {
        defaultPrint(text, contentType);
        return;
      }
      text = StringUtil.replace(text, "\r\n", NL, false);
      StringTokenizer tokenizer = new StringTokenizer(text, NL, true);
      while (tokenizer.hasMoreTokens()) {
        String line = tokenizer.nextToken();
        if (NL.equals(line)) {
          defaultPrint(line, contentType);
        }
        else {
          printLine(line, contentType);
        }
      }
    }

    private void defaultPrint(String text, ConsoleViewContentType contentType) {
      super.print(text, contentType);
    }

    private void printLine(@NotNull String line, @NotNull ConsoleViewContentType contentType) {
      Filter.Result result;
      try {
        result = myFilter.applyFilter(line, line.length());
      }
      catch (Throwable t) {
        throw new RuntimeException("Error while applying " + myFilter + " to '"+line+"'", t);
      }
      if (result != null) {
        List<Filter.ResultItem> items = sort(result.getResultItems());
        int lastOffset = 0;
        for (Filter.ResultItem item : items) {
          defaultPrint(line.substring(lastOffset, item.getHighlightStartOffset()), contentType);
          String linkText = line.substring(item.getHighlightStartOffset(), item.getHighlightEndOffset());
          printHyperlink(linkText, item.getHyperlinkInfo());
          lastOffset = item.getHighlightEndOffset();
        }
        defaultPrint(line.substring(lastOffset), contentType);
      }
      else {
        defaultPrint(line, contentType);
      }
    }

    @NotNull
    private static List<Filter.ResultItem> sort(@NotNull List<Filter.ResultItem> items) {
      if (items.size() <= 1) {
        return items;
      }
      List<Filter.ResultItem> copy = new ArrayList<>(items);
      Collections.sort(copy, Comparator.comparingInt(Filter.ResultItem::getHighlightStartOffset));
      return copy;
    }
  }

}
