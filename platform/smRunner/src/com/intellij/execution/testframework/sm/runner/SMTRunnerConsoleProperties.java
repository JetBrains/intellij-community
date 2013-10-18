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

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.filters.CompositeFilter;
import com.intellij.execution.filters.FileHyperlinkInfo;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.SMStacktraceParser;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
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
 * @author: Roman Chernyatchik
 */
public class SMTRunnerConsoleProperties extends TestConsoleProperties implements SMStacktraceParser {
  private final RunConfiguration myConfiguration;
  protected final CompositeFilter myCustomFilter;

  /**
   * @param config
   * @param testFrameworkName Prefix for storage which keeps runner settings. E.g. "RubyTestUnit"
   * @param executor
   */
  public SMTRunnerConsoleProperties(@NotNull final RunConfiguration config,
                                    @NotNull final String testFrameworkName,
                                    @NotNull final Executor executor)
  {
    super(new Storage.PropertiesComponentStorage(testFrameworkName + "Support.", PropertiesComponent.getInstance()),
          config.getProject(),
          executor);
    myConfiguration = config;
    myCustomFilter = new CompositeFilter(config.getProject());
  }

  public RunConfiguration getConfiguration() {
    return myConfiguration;
  }

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
      final Filter.Result result;
      try {
        result = myCustomFilter.applyFilter(line, stacktraceLength);
      }
      catch (Throwable t) {
        throw new RuntimeException("Error while applying " + myCustomFilter + " to '"+line+"'", t);
      }
      if (result != null) {
        final HyperlinkInfo info = result.hyperlinkInfo;

        // covers 99% use existing cases
        if (info instanceof FileHyperlinkInfo) {
          return ((FileHyperlinkInfo)info).getDescriptor();
        }

        // otherwise
        return new Navigatable() {
          @Override
          public void navigate(boolean requestFocus) {
            result.hyperlinkInfo.navigate(project);
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
  protected Navigatable findSuitableNavigatableForLine(@NotNull final Project project,
                                                       @NotNull final VirtualFile file,
                                                       final int line) {
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

    return new OpenFileDescriptor(project, file, offset);
  }
}
