/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;

/**
 * @author maxim
 */
public class CompileContext {
  private final OptimizingSearchHelper searchHelper;
  
  private final CompiledPattern pattern;
  private final MatchOptions options;
  private final Project project;

  public CompileContext(final CompiledPattern _result, final MatchOptions _options, final Project _project) {
    options = _options;
    project = _project;
    pattern = _result;

    searchHelper = ApplicationManager.getApplication().isUnitTestMode() ?
                   new TestModeOptimizingSearchHelper() :
                   new FindInFilesOptimizingSearchHelper(options.getScope(), options.isCaseSensitiveMatch(), _project);
  }

  public void clear() {
    searchHelper.clear();
  }

  public OptimizingSearchHelper getSearchHelper() {
    return searchHelper;
  }

  public CompiledPattern getPattern() {
    return pattern;
  }

  public MatchOptions getOptions() {
    return options;
  }

  public Project getProject() {
    return project;
  }
}
