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
package com.intellij.structuralsearch;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.structuralsearch.impl.matcher.MatcherImpl;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

abstract class StructuralSearchTestCase extends LightQuickFixTestCase {
  protected MatchOptions options;
  protected Matcher testMatcher;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    StructuralSearchUtil.ourUseUniversalMatchingAlgorithm = false;
    testMatcher = new Matcher(getProject());
    options = new MatchOptions();
    options.setLooseMatching(true);
    options.setRecursiveSearch(true);
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  @Override
  protected void tearDown() throws Exception {
    testMatcher = null;
    options = null;
    super.tearDown();
  }

  protected int findMatchesCount(String in, String pattern, boolean filePattern, FileType fileType) {
    return findMatches(in,pattern,filePattern, fileType).size();
  }

  protected List<MatchResult> findMatches(String in,
                                          String pattern,
                                          boolean filePattern,
                                          FileType patternFileType,
                                          Language patternLanguage,
                                          FileType sourceFileType,
                                          String sourceExtension,
                                          boolean physicalSourceFile) {
    return findMatches(in, pattern, filePattern, patternFileType, patternLanguage, sourceFileType, sourceExtension, physicalSourceFile, true);
  }

  protected List<MatchResult> findMatches(String in,
                                          String pattern,
                                          boolean filePattern,
                                          FileType patternFileType,
                                          Language patternLanguage,
                                          FileType sourceFileType,
                                          String sourceExtension,
                                          boolean physicalSourceFile,
                                          boolean transform) {
    options.clearVariableConstraints();
    options.setSearchPattern(pattern);
    if (transform) {
      MatcherImplUtil.transform(options);
    }
    pattern = options.getSearchPattern();
    options.setFileType(patternFileType);
    options.setDialect(patternLanguage);

    MatcherImpl.validate(getProject(), options);
    return testMatcher.testFindMatches(in, pattern, options, filePattern, sourceFileType, sourceExtension, physicalSourceFile);
  }

  protected List<MatchResult> findMatches(String in, String pattern, boolean filePattern, FileType patternFileType) {
    return findMatches(in, pattern, filePattern, patternFileType, null, patternFileType, null, false);
  }

  protected int findMatchesCount(String in, String pattern, boolean filePattern) {
    return findMatchesCount(in, pattern,filePattern, StdFileTypes.JAVA);
  }

  protected int findMatchesCount(String in, String pattern) {
    return findMatchesCount(in,pattern,false);
  }

  protected String loadFile(String fileName) throws IOException {
    return FileUtilRt.loadFile(new File(getTestDataPath() + fileName), CharsetToolkit.UTF8, true);
  }
}
