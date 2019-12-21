// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;

import java.util.List;
import java.util.function.Function;

/**
 * @author Bas Leijdekkers
 */
public abstract class PredefinedConfigurationsTestCase extends StructuralSearchTestCase {

  protected void doTest(Configuration template, String source, LanguageFileType fileType, String... results) {
    doTest(template, source, fileType, e -> StructuralSearchUtil.getPresentableElement(e).getText(), results);
  }

  protected void doTest(Configuration template,
                        String source,
                        LanguageFileType fileType,
                        Function<? super PsiElement, String> resultConverter,
                        String... expectedResults) {
    if (!(template instanceof SearchConfiguration)) fail();
    final SearchConfiguration searchConfiguration = (SearchConfiguration)template;
    options = searchConfiguration.getMatchOptions();
    final List<MatchResult> matches = testMatcher.testFindMatches(source, options, true, fileType, false);
    assertEquals(template.getName(), expectedResults.length, matches.size());
    final String[] actualResults = matches.stream().map(MatchResult::getMatch).map(resultConverter).toArray(String[]::new);
    for (int i = 0; i < actualResults.length; i++) {
      assertEquals(template.getName(), expectedResults[i], actualResults[i]);
    }
  }
}
