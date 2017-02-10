/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
 * Date: 17.11.2004
 * Time: 19:26:37
 */
public class CompileContext {
  private OptimizingSearchHelper searchHelper;
  
  private CompiledPattern pattern;
  private MatchOptions options;
  private Project project;

  public void clear() {
    if (searchHelper!=null) searchHelper.clear();

    project = null;
    pattern = null;
    options = null;
  }

  public void init(final CompiledPattern _result, final MatchOptions _options, final Project _project, final boolean _findMatchingFiles) {
    options = _options;
    project = _project;
    pattern = _result;

    searchHelper = ApplicationManager.getApplication().isUnitTestMode() ?
                   new TestModeOptimizingSearchHelper(this) :
                   new FindInFilesOptimizingSearchHelper(this, _findMatchingFiles, _project);
  }

  public OptimizingSearchHelper getSearchHelper() {
    return searchHelper;
  }

  public CompiledPattern getPattern() {
    return pattern;
  }

  void setPattern(CompiledPattern pattern) {
    this.pattern = pattern;
  }

  public MatchOptions getOptions() {
    return options;
  }

  void setOptions(MatchOptions options) {
    this.options = options;
  }

  public Project getProject() {
    return project;
  }

  void setProject(Project project) {
    this.project = project;
  }
}
