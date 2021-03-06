// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    final Matcher matcher = new Matcher(getProject(), options);
    final List<MatchResult> matches = matcher.testFindMatches(source, true, fileType, false);
    final String[] actualResults = matches.stream().map(MatchResult::getMatch).map(resultConverter).toArray(String[]::new);
    assertEquals(template.getName() + "\n" + String.join("\n", actualResults), expectedResults.length, matches.size());
    for (int i = 0; i < actualResults.length; i++) {
      assertEquals(template.getName(), expectedResults[i], actualResults[i]);
    }
  }
}
