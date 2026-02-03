// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.TestsOutputConsolePrinter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;

public final class TestProxyPrinterProvider {

  private final TestProxyFilterProvider myFilterProvider;
  private final BaseTestsOutputConsoleView myTestOutputConsoleView;

  public TestProxyPrinterProvider(@NotNull BaseTestsOutputConsoleView testsOutputConsoleView,
                                  @NotNull TestProxyFilterProvider filterProvider) {
    myTestOutputConsoleView = testsOutputConsoleView;
    myFilterProvider = filterProvider;
  }

  public @Nullable Printer getPrinterByType(@NotNull String nodeType, @NotNull String nodeName, @Nullable String nodeArguments) {
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

    private final Condition<? super ConsoleViewContentType> myContentTypeCondition;
    private final Filter myFilter;

    HyperlinkPrinter(@NotNull BaseTestsOutputConsoleView testsOutputConsoleView,
                     @NotNull Condition<? super ConsoleViewContentType> contentTypeCondition,
                     @NotNull Filter filter) {
      super(testsOutputConsoleView, testsOutputConsoleView.getProperties(), null);
      myContentTypeCondition = contentTypeCondition;
      myFilter = filter;
    }

    @Override
    public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
      if (!myContentTypeCondition.value(contentType)) {
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
      Filter.Result result = ReadAction.compute(() -> {
        try {
          return myFilter.applyFilter(line, line.length());
        }
        catch (Throwable t) {
          throw new RuntimeException("Error while applying " + myFilter + " to '"+line+"'", t);
        }
      });
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

    private static @NotNull List<Filter.ResultItem> sort(@NotNull List<Filter.ResultItem> items) {
      if (items.size() <= 1) {
        return items;
      }
      List<Filter.ResultItem> copy = new ArrayList<>(items);
      copy.sort(Comparator.comparingInt(Filter.ResultItem::getHighlightStartOffset));
      return copy;
    }
  }

}
