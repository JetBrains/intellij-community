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
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Printable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.actions.ViewAssertEqualsDiffAction;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public class DiffHyperlink implements Printable {
  private static final String NEW_LINE = "\n";
  private static final Logger LOG = Logger.getInstance("#" + DiffHyperlink.class.getName());

  protected final String myExpected;
  protected final String myActual;
  protected final String myFilePath;
  private boolean myPrintOneLine;
  private final HyperlinkInfo myDiffHyperlink = new DiffHyperlinkInfo();


  public DiffHyperlink(final String expected, final String actual, final String filePath) {
    this(expected, actual, filePath, true);
  }

  public DiffHyperlink(final String expected,
                       final String actual,
                       final String filePath,
                       boolean printOneLine) {
    myExpected = expected;
    myActual = actual;
    myFilePath = filePath == null ? null : filePath.replace(File.separatorChar, '/');
    myPrintOneLine = printOneLine;
  }

  public void openDiff(Project project) {
    openMultiDiff(project, null);
  }

  public void openMultiDiff(final Project project,
                            final AbstractTestProxy.AssertEqualsDiffChain chain) {
    final SimpleDiffRequest diffData = createRequest(project, chain, myFilePath, myExpected, myActual);
    DiffManager.getInstance().getIdeaDiffTool().show(diffData);
  }

  private SimpleDiffRequest createRequest(final Project project,
                                          final AbstractTestProxy.AssertEqualsDiffChain chain,
                                          String filePath, String expected, String actual) {
    String expectedTitle = ExecutionBundle.message("diff.content.expected.title");
    final DiffContent expectedContent;
    final VirtualFile vFile;
    if (filePath != null && (vFile = LocalFileSystem.getInstance().findFileByPath(filePath)) != null) {
      expectedContent = DiffContent.fromFile(project, vFile);
      expectedTitle += " (" + vFile.getPresentableUrl() + ")";
    } else {
      expectedContent = new SimpleContent(expected);
    }
    final SimpleDiffRequest diffData = new SimpleDiffRequest(project, getTitle());
    if (chain != null) {
      diffData.setToolbarAddons(new DiffRequest.ToolbarAddons() {
        @Override
        public void customize(DiffToolbar toolbar) {
          toolbar.addAction(new NextPrevAction("Compare Previous Failure", AllIcons.Actions.Prevfile, chain) {
            {
              registerCustomShortcutSet(ActionManager.getInstance().getAction("PreviousTab").getShortcutSet(), null);
            }

            @Override
            protected DiffHyperlink getNextId() {
              return chain.getPrevious();
            }
          });
          toolbar.addAction(new NextPrevAction("Compare Next Failure", AllIcons.Actions.Nextfile, chain) {
            {
              registerCustomShortcutSet(ActionManager.getInstance().getAction("NextTab").getShortcutSet(), null);
            }

            @Override
            protected DiffHyperlink getNextId() {
              return chain.getNext();
            }
          });
        }
      });
    }
    diffData.setContents(expectedContent, new SimpleContent(actual));
    diffData.setContentTitles(expectedTitle, ExecutionBundle.message("diff.content.actual.title"));
    diffData.addHint(DiffTool.HINT_SHOW_FRAME);
    diffData.addHint(DiffTool.HINT_DO_NOT_IGNORE_WHITESPACES);
    diffData.setGroupKey("#com.intellij.execution.junit2.states.ComparisonFailureState$DiffDialog");
    return diffData;
  }

  abstract class NextPrevAction extends AnAction {

    private final AbstractTestProxy.AssertEqualsDiffChain myChain;

    public NextPrevAction(@Nullable String text, @Nullable Icon icon,
                          final AbstractTestProxy.AssertEqualsDiffChain chain) {
      super(text, text, icon);
      myChain = chain;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final DiffViewer viewer = e.getData(PlatformDataKeys.DIFF_VIEWER);
      LOG.assertTrue(viewer != null);
      final Project project = e.getData(CommonDataKeys.PROJECT);
      final DiffHyperlink nextProvider = getNextId();
      myChain.setCurrent(nextProvider);
      final SimpleDiffRequest nextRequest = createRequest(project, myChain,
                                                          nextProvider.getFilePath(), nextProvider.getLeft(), nextProvider.getRight());
      viewer.setDiffRequest(nextRequest);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final DiffViewer viewer = e.getData(PlatformDataKeys.DIFF_VIEWER);
      final Project project = e.getData(CommonDataKeys.PROJECT);
      e.getPresentation().setEnabled(project != null && viewer != null);
    }

    protected abstract DiffHyperlink getNextId();
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

  public void printOn(final Printer printer) {
    if (!hasMoreThanOneLine(myActual) && !hasMoreThanOneLine(myExpected) && myPrintOneLine) {
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

    return true;
  }

  @Override
  public int hashCode() {
    int result = myExpected != null ? myExpected.hashCode() : 0;
    result = 31 * result + (myActual != null ? myActual.hashCode() : 0);
    result = 31 * result + (myFilePath != null ? myFilePath.hashCode() : 0);
    return result;
  }

  public class DiffHyperlinkInfo implements HyperlinkInfo {
    public void navigate(final Project project) {
      ViewAssertEqualsDiffAction.openDiff(DataManager.getInstance().getDataContext(), DiffHyperlink.this);
    }

    public DiffHyperlink getPrintable() {
      return DiffHyperlink.this;
    }
  }
}