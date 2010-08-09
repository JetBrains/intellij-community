/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class DiffHyperlink implements Printable {
  private static final String NEW_LINE = "\n";

  protected final String myExpected;
  protected final String myActual;
  protected final String myFilePath;
  private final HyperlinkInfo myDiffHyperlink = new DiffHyperlinkInfo();


  public DiffHyperlink(final String expected, final String actual, final String filePath) {
    myExpected = expected;
    myActual = actual;
    myFilePath = filePath == null ? null : filePath.replace(File.separatorChar, '/');
  }

  public void openDiff(final Project project) {
    String expectedTitle = ExecutionBundle.message("diff.content.expected.title");
    final DiffContent expectedContent;
    final VirtualFile vFile;
    if (myFilePath != null && (vFile = LocalFileSystem.getInstance().findFileByPath(myFilePath)) != null) {
      expectedContent = DiffContent.fromFile(project, vFile);
      expectedTitle += " (" + vFile.getPresentableUrl() + ")";
    } else expectedContent = new SimpleContent(myExpected);
    final SimpleDiffRequest diffData = new SimpleDiffRequest(project, getTitle());
    diffData.setContents(expectedContent, new SimpleContent(myActual));
    diffData.setContentTitles(expectedTitle, ExecutionBundle.message("diff.content.actual.title"));
    diffData.addHint(DiffTool.HINT_SHOW_NOT_MODAL_DIALOG);
    diffData.addHint(DiffTool.HINT_DO_NOT_IGNORE_WHITESPACES);
    diffData.setGroupKey("#com.intellij.execution.junit2.states.ComparisonFailureState$DiffDialog");
    DiffManager.getInstance().getIdeaDiffTool().show(diffData);
  }

  protected String getTitle() {
    return ExecutionBundle.message("strings.equal.failed.dialog.title");
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

  public void printOn(final Printer printer) {
    if (hasMoreThanOneLine(myActual) || hasMoreThanOneLine(myExpected)) {
      printer.print(" ", ConsoleViewContentType.ERROR_OUTPUT);
      printer.printHyperlink(ExecutionBundle.message("junit.click.to.see.diff.link"), myDiffHyperlink);
      printer.print(NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    }
    else {
      printer.print(NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
      printer.print(ExecutionBundle.message("diff.content.expected.for.file.title"), ConsoleViewContentType.SYSTEM_OUTPUT);
      printer.print(myExpected + NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
      printer.print(ExecutionBundle.message("junit.actual.text.label"), ConsoleViewContentType.SYSTEM_OUTPUT);
      printer.print(myActual + NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  private static boolean hasMoreThanOneLine(final String string) {
    return string.indexOf('\n') != -1 || string.indexOf('\r') != -1;
  }

  public class DiffHyperlinkInfo implements HyperlinkInfo {
    public void navigate(final Project project) {
      openDiff(project);
    }

    public DiffHyperlink getPrintable() {
      return DiffHyperlink.this;
    }
  }
}