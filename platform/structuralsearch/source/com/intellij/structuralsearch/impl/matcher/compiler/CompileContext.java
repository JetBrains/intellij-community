package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 17.11.2004
 * Time: 19:26:37
 * To change this template use File | Settings | File Templates.
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

  MatchOptions getOptions() {
    return options;
  }

  void setOptions(MatchOptions options) {
    this.options = options;
  }

  Project getProject() {
    return project;
  }

  void setProject(Project project) {
    this.project = project;
  }
}
