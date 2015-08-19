/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 15-Aug-2007
 */
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class DiffHyperlink implements Printable {
  private static final String NEW_LINE = "\n";
  private static final Logger LOG = Logger.getInstance("#" + DiffHyperlink.class.getName());

  protected final String myExpected;
  protected final String myActual;
  protected final String myFilePath;
  protected final String myActualFilePath;
  private boolean myPrintOneLine;
  private final HyperlinkInfo myDiffHyperlink = new DiffHyperlinkInfo();


  public DiffHyperlink(final String expected, final String actual, final String filePath) {
    this(expected, actual, filePath, true);
  }

  public DiffHyperlink(final String expected,
                       final String actual,
                       final String filePath,
                       boolean printOneLine) {
    this(expected, actual, filePath, null, printOneLine);
  }
  
  public DiffHyperlink(final String expected,
                       final String actual,
                       final String expectedFilePath,
                       final String actualFilePath,
                       boolean printOneLine) {
    myExpected = expected;
    myActual = actual;
    myFilePath = normalizeSeparators(expectedFilePath);
    myActualFilePath = normalizeSeparators(actualFilePath);
    myPrintOneLine = printOneLine;
  }

  private static String normalizeSeparators(String filePath) {
    return filePath == null ? null : filePath.replace(File.separatorChar, '/');
  }

  /**
   * Use {@link ViewAssertEqualsDiffAction#openDiff(DataContext, DiffHyperlink)} 
   */
  @Deprecated
  public void openDiff(Project project) {
    ViewAssertEqualsDiffAction.openDiff(DataManager.getInstance().getDataContext(), this);
  }

  /**
   * Use {@link ViewAssertEqualsDiffAction#openDiff(DataContext, DiffHyperlink)}
   */
  @Deprecated
  public void openMultiDiff(final Project project,
                            final AbstractTestProxy.AssertEqualsDiffChain chain) {
    ViewAssertEqualsDiffAction.openDiff(DataManager.getInstance().getDataContext(), this);
  }

  protected String getTitle() {
    return ExecutionBundle.message("strings.equal.failed.dialog.title");
  }

  public String getDiffTitle() {
    return getTitle();
  }

  public String getLeft() {
    return myExpected;
  }

  public String getRight() {
    return myActual;
  }

  public String getFilePath() {
    return myFilePath;
  }
  
  public String getActualFilePath() {
    return myActualFilePath;
  }

  public void printOn(final Printer printer) {
    if (!hasMoreThanOneLine(myActual.trim()) && !hasMoreThanOneLine(myExpected.trim()) && myPrintOneLine) {
      printer.print(NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
      printer.print(ExecutionBundle.message("diff.content.expected.for.file.title"), ConsoleViewContentType.SYSTEM_OUTPUT);
      printer.print(myExpected + NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
      printer.print(ExecutionBundle.message("junit.actual.text.label"), ConsoleViewContentType.SYSTEM_OUTPUT);
      printer.print(myActual + NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    }
    printer.print(" ", ConsoleViewContentType.ERROR_OUTPUT);
    printer.printHyperlink(ExecutionBundle.message("junit.click.to.see.diff.link"), myDiffHyperlink);
    printer.print(NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
  }

  private static boolean hasMoreThanOneLine(final String string) {
    return string.indexOf('\n') != -1 || string.indexOf('\r') != -1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DiffHyperlink)) return false;

    DiffHyperlink hyperlink = (DiffHyperlink)o;

    if (myActual != null ? !myActual.equals(hyperlink.myActual) : hyperlink.myActual != null) return false;
    if (myExpected != null ? !myExpected.equals(hyperlink.myExpected) : hyperlink.myExpected != null) return false;
    if (myFilePath != null ? !myFilePath.equals(hyperlink.myFilePath) : hyperlink.myFilePath != null) return false;
    if (myActualFilePath != null ? !myActualFilePath.equals(hyperlink.myActualFilePath) : hyperlink.myActualFilePath != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myExpected != null ? myExpected.hashCode() : 0;
    result = 31 * result + (myActual != null ? myActual.hashCode() : 0);
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