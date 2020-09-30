// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import org.jetbrains.annotations.NotNull;

/**
 * @author maxim
 */
public class CompileContext {
  private final OptimizingSearchHelper mySearchHelper;
  
  private final CompiledPattern myPattern;
  private final MatchOptions myOptions;
  private final Project myProject;

  public CompileContext(@NotNull CompiledPattern pattern, @NotNull MatchOptions options, @NotNull Project project) {
    myPattern = pattern;
    myOptions = options;
    myProject = project;

    mySearchHelper = ApplicationManager.getApplication().isUnitTestMode() ?
                     new TestModeOptimizingSearchHelper() :
                     new FindInFilesOptimizingSearchHelper(myOptions.getScope(), options.isCaseSensitiveMatch(), project);
  }

  public void clear() {
    mySearchHelper.clear();
  }

  @NotNull
  public OptimizingSearchHelper getSearchHelper() {
    return mySearchHelper;
  }

  @NotNull
  public CompiledPattern getPattern() {
    return myPattern;
  }

  @NotNull
  public MatchOptions getOptions() {
    return myOptions;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }
}
