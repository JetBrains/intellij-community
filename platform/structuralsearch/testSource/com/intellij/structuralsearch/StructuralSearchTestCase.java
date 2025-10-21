// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class StructuralSearchTestCase extends LightPlatformCodeInsightTestCase {
  protected MatchOptions options;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    options = new MatchOptions();
    options.setRecursiveSearch(true);
  }

  @Override
  protected void tearDown() throws Exception {
    options = null;
    super.tearDown();
  }

  protected String getSearchPlan(String query, LanguageFileType fileType) {
    final MatchOptions matchOptions = new MatchOptions();
    matchOptions.fillSearchCriteria(query);
    matchOptions.setFileType(fileType);
    PatternCompiler.compilePattern(getProject(), matchOptions, true, true);
    return PatternCompiler.getLastSearchPlan();
  }

  protected int findMatchesCount(String in, String pattern, LanguageFileType fileType) {
    return findMatches(in, pattern, fileType).size();
  }

  protected List<MatchResult> findMatches(String in,
                                          String pattern,
                                          LanguageFileType patternFileType,
                                          Language patternLanguage,
                                          LanguageFileType sourceFileType,
                                          boolean physicalSourceFile) {
    options.fillSearchCriteria(pattern);
    options.setFileType(patternFileType);
    options.setDialect(patternLanguage);

    final CompiledPattern compiledPattern = PatternCompiler.compilePattern(getProject(), options, true, false);
    final String message = checkApplicableConstraints(options, compiledPattern);
    assertNull(message, message);
    final Matcher matcher = new Matcher(getProject(), options, compiledPattern);
    return matcher.testFindMatches(in, true, sourceFileType, physicalSourceFile);
  }

  public static String checkApplicableConstraints(MatchOptions options, CompiledPattern compiledPattern) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(options.getFileType());
    assert profile != null : "no profile found for file type: " + options.getFileType();
    for (String varName : options.getVariableConstraintNames()) {
      final List<PsiElement> nodes = compiledPattern.getVariableNodes(varName);
      final MatchVariableConstraint constraint = options.getVariableConstraint(varName);
      final List<String> usedConstraints = new SmartList<>();
      if (!StringUtil.isEmpty(constraint.getRegExp())) {
        usedConstraints.add(UIUtil.TEXT);
      }
      if (constraint.isWithinHierarchy()) {
        usedConstraints.add(UIUtil.TEXT_HIERARCHY);
      }
      if (constraint.getMinCount() == 0) {
        usedConstraints.add(UIUtil.MINIMUM_ZERO);
      }
      if (constraint.getMaxCount() > 1) {
        usedConstraints.add(UIUtil.MAXIMUM_UNLIMITED);
      }
      if (!StringUtil.isEmpty(constraint.getNameOfExprType())) {
        usedConstraints.add(UIUtil.TYPE);
      }
      if (!StringUtil.isEmpty(constraint.getNameOfFormalArgType())) {
        usedConstraints.add(UIUtil.EXPECTED_TYPE);
      }
      if (!StringUtil.isEmpty(constraint.getReferenceConstraint())) {
        usedConstraints.add(UIUtil.REFERENCE);
      }
      for (String usedConstraint : usedConstraints) {
        if (!profile.isApplicableConstraint(usedConstraint, nodes, false, constraint.isPartOfSearchResults())) {
          return usedConstraint + " not applicable for " + varName;
        }
      }
    }
    return null;
  }

  protected List<MatchResult> findMatches(String in, String pattern, LanguageFileType fileType) {
    return findMatches(in, pattern, fileType, null, fileType, false);
  }

  protected void findMatchesText(String in, String pattern, LanguageFileType fileType, String... expectedResults) {
    final List<MatchResult> matches = findMatches(in, pattern, fileType);
    final List<String> actualResults = ContainerUtil.map(matches, r -> StructuralSearchUtil.getPresentableElement(r.getMatch()).getText());
    assertEquals(String.join("\n", actualResults), expectedResults.length, actualResults.size());
    for (int i = 0, length = expectedResults.length; i < length; i++) {
      assertEquals(expectedResults[i], actualResults.get(i));
    }
  }

  protected String loadFile(String fileName) throws IOException {
    return FileUtilRt.loadFile(new File(getTestDataPath() + fileName), CharsetToolkit.UTF8, true);
  }
}
