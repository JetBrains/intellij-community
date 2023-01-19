// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.testframework.stacktrace;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfoBase;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.actions.ViewAssertEqualsDiffAction;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

public class DiffHyperlink implements Printable {
  private static final String NEW_LINE = "\n";

  @NotNull
  private final String myExpected;
  @NotNull
  private final String myActual;
  protected final String myFilePath;
  protected final String myActualFilePath;
  private final boolean myPrintOneLine;
  private final HyperlinkInfo myDiffHyperlink = new DiffHyperlinkInfo();
  private final static int ourMaxExpectedLength = Registry.intValue("test.console.expected.actual.max.length", 255);
  private AbstractTestProxy myTestProxy;


  public DiffHyperlink(@NotNull String expected, @NotNull String actual, String filePath) {
    this(expected, actual, filePath, null, true);
  }

  public DiffHyperlink(@NotNull String expected,
                       @NotNull String actual,
                       final String expectedFilePath,
                       final String actualFilePath,
                       boolean printOneLine) {
    myExpected = expected;
    myActual = actual;
    myFilePath = normalizeSeparators(expectedFilePath);
    myActualFilePath = normalizeSeparators(actualFilePath);
    myPrintOneLine = printOneLine;
  }

  public void setTestProxy(AbstractTestProxy testProxy) {
    myTestProxy = testProxy;
  }

  public HyperlinkInfo getInfo() {
    return myDiffHyperlink;
  }

  public AbstractTestProxy getTestProxy() {
    return myTestProxy;
  }

  private static String normalizeSeparators(String filePath) {
    return filePath == null ? null : filePath.replace(File.separatorChar, '/');
  }

  protected @NlsContexts.DialogTitle String getTitle() {
    return myTestProxy != null
           ? ExecutionBundle.message("strings.equal.failed.with.test.name.dialog.title", myTestProxy)
           : ExecutionBundle.message("strings.equal.failed.dialog.title");
  }

  public @NlsContexts.DialogTitle String getDiffTitle() {
    return getTitle();
  }

  @Nullable
  public @NlsSafe String getTestName() {
    return myTestProxy.getName();
  }

  @NotNull
  public String getLeft() {
    return myExpected;
  }

  @NotNull
  public String getRight() {
    return myActual;
  }

  public String getFilePath() {
    return myFilePath;
  }

  public String getActualFilePath() {
    return myActualFilePath;
  }

  @Override
  public void printOn(final Printer printer) {
    if (!hasMoreThanOneLine(myActual) && !hasMoreThanOneLine(myExpected) && myPrintOneLine) {
      printer.printExpectedActualHeader(StringUtil.trimLog(myExpected, ourMaxExpectedLength), 
                                        StringUtil.trimLog(myActual, ourMaxExpectedLength));
    }
    printer.print(NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    printer.printHyperlink(ExecutionBundle.message("junit.click.to.see.diff.link"), myDiffHyperlink);
    printer.print(NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
  }

  private static boolean hasMoreThanOneLine(final @NotNull String string) {
    return string.indexOf('\n') != -1 || string.indexOf('\r') != -1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DiffHyperlink)) return false;

    DiffHyperlink hyperlink = (DiffHyperlink)o;

    if (!myActual.equals(hyperlink.myActual)) return false;
    if (!myExpected.equals(hyperlink.myExpected)) return false;
    if (!Objects.equals(myFilePath, hyperlink.myFilePath)) return false;
    if (!Objects.equals(myActualFilePath, hyperlink.myActualFilePath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myExpected.hashCode();
    result = 31 * result + myActual.hashCode();
    result = 31 * result + (myFilePath != null ? myFilePath.hashCode() : 0);
    result = 31 * result + (myActualFilePath != null ? myActualFilePath.hashCode() : 0);
    return result;
  }

  public class DiffHyperlinkInfo extends HyperlinkInfoBase {
    @Override
    public void navigate(@NotNull Project project, @Nullable RelativePoint hyperlinkLocationPoint) {
      final DataManager dataManager = DataManager.getInstance();
      final DataContext dataContext = hyperlinkLocationPoint != null ?
                                      dataManager.getDataContext(hyperlinkLocationPoint.getOriginalComponent()) : dataManager.getDataContext();
      ViewAssertEqualsDiffAction.openDiff(dataContext, DiffHyperlink.this);
    }

    public DiffHyperlink getPrintable() {
      return DiffHyperlink.this;
    }
  }
}