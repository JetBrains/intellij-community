// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.filters.CompositeFilter;
import com.intellij.execution.filters.FileHyperlinkInfo;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMStacktraceParserEx;
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction;
import com.intellij.execution.testframework.sm.runner.history.actions.ImportTestsFromFileAction;
import com.intellij.execution.testframework.sm.runner.history.actions.ImportTestsGroup;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.config.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 * Use {@link SMRunnerConsolePropertiesProvider} so importer {@link AbstractImportTestsAction.ImportRunProfile#ImportRunProfile(VirtualFile, Project)}
 * would be able to create properties by read configuration and test navigation, rerun failed tests etc. would work on imported results
 */
public class SMTRunnerConsoleProperties extends TestConsoleProperties implements SMStacktraceParserEx {
  private final RunProfile myConfiguration;
  @NotNull private final String myTestFrameworkName;
  private final CompositeFilter myCustomFilter;
  private boolean myIdBasedTestTree = false;
  private boolean myPrintTestingStartedTime = true;

  /**
   * @param config
   * @param testFrameworkName Prefix for storage which keeps runner settings. E.g. "RubyTestUnit"
   * @param executor
   */
  public SMTRunnerConsoleProperties(@NotNull RunConfiguration config, @NotNull String testFrameworkName, @NotNull Executor executor) {
    this(config.getProject(), config, testFrameworkName, executor);
  }

  public SMTRunnerConsoleProperties(@NotNull Project project,
                                    @NotNull RunProfile config,
                                    @NotNull String testFrameworkName,
                                    @NotNull Executor executor) {
    super(new Storage.PropertiesComponentStorage(testFrameworkName + "Support.", PropertiesComponent.getInstance()), project, executor);
    myConfiguration = config;
    myTestFrameworkName = testFrameworkName;
    myCustomFilter = new CompositeFilter(project);
  }

  /**
   * If enabled, runner must add new line char (\n) before each TC message. This char is not reported to user.
   * @deprecated Fix your runner and stop adding "\n" before TC message.
   */
  @Deprecated
  public boolean serviceMessageHasNewLinePrefix() {
    return false;
  }

  @Override
  public RunProfile getConfiguration() {
    return myConfiguration;
  }

  @Override
  protected AnAction @Nullable [] createImportActions() {
    return new AnAction[] {new ImportTestsGroup(this), new ImportTestsFromFileAction()};
  }

  public boolean isIdBasedTestTree() {
    return myIdBasedTestTree;
  }

  public void setIdBasedTestTree(boolean idBasedTestTree) {
    myIdBasedTestTree = idBasedTestTree;
  }

  public boolean isPrintTestingStartedTime() {
    return myPrintTestingStartedTime;
  }

  public void setPrintTestingStartedTime(boolean printTestingStartedTime) {
    myPrintTestingStartedTime = printTestingStartedTime;
  }

  @Nullable
  @Override
  public Navigatable getErrorNavigatable(@NotNull Location<?> location, @NotNull String stacktrace) {
    return getErrorNavigatable(location.getProject(), stacktrace);
  }

  @Nullable
  @Override
  public Navigatable getErrorNavigatable(@NotNull final Project project, final @NotNull String stacktrace) {
    if (myCustomFilter.isEmpty()) {
      return null;
    }

    // iterate stacktrace lines find first navigatable line using
    // stacktrace filters
    final int stacktraceLength = stacktrace.length();
    final String[] lines = StringUtil.splitByLines(stacktrace);
    for (String line : lines) {
      Filter.Result result;
      try {
        result = myCustomFilter.applyFilter(line, stacktraceLength);
      }
      catch (Throwable t) {
        throw new RuntimeException("Error while applying " + myCustomFilter + " to '" + line + "'", t);
      }
      final HyperlinkInfo info = result != null ? result.getFirstHyperlinkInfo() : null;
      if (info != null) {

        // covers 99% use existing cases
        if (info instanceof FileHyperlinkInfo) {
          return ((FileHyperlinkInfo)info).getDescriptor();
        }

        // otherwise
        return new Navigatable() {
          @Override
          public void navigate(boolean requestFocus) {
            info.navigate(project);
          }

          @Override
          public boolean canNavigate() {
            return true;
          }

          @Override
          public boolean canNavigateToSource() {
            return true;
          }
        };
      }
    }
    return null;
  }

  public void addStackTraceFilter(final Filter filter) {
    myCustomFilter.addFilter(filter);
  }

  @Nullable
  @Deprecated
  protected Navigatable findSuitableNavigatableForLine(@NotNull Project project, @NotNull VirtualFile file, int line) {
    // lets find first non-ws psi element

    final Document doc = FileDocumentManager.getInstance().getDocument(file);
    final PsiFile psi = doc == null ? null : PsiDocumentManager.getInstance(project).getPsiFile(doc);
    if (psi == null) {
      return null;
    }

    int offset = doc.getLineStartOffset(line);
    int endOffset = doc.getLineEndOffset(line);
    for (int i = offset + 1; i < endOffset; i++) {
      PsiElement el = psi.findElementAt(i);
      if (el != null && !(el instanceof PsiWhiteSpace)) {
        offset = el.getTextOffset();
        break;
      }
    }

    return PsiNavigationSupport.getInstance().createNavigatable(project, file, offset);
  }

  public boolean fixEmptySuite() {
    return false;
  }

  @Nullable
  public SMTestLocator getTestLocator() {
    return null;
  }

  @Nullable
  public TestProxyFilterProvider getFilterProvider() {
    return null;
  }

  @Nullable
  public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return null;
  }

  @NotNull
  public String getTestFrameworkName() {
    return myTestFrameworkName;
  }

  public boolean isUndefined() {
    return false;
  }
}
