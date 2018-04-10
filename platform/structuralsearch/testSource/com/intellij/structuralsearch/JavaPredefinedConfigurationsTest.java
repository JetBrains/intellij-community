// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Pair;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.Pair.pair;
import static java.util.Arrays.asList;

/**
 * @author Bas Leijdekkers
 */
public class JavaPredefinedConfigurationsTest extends StructuralSearchTestCase {

  /**
   * name of predefined template -> pair of source and list of expected search results in that source
   */
  private static final Map<String, Pair<String, List<String>>> testCases = new HashMap<>();

  static {
    testCases.put(SSRBundle.message("predefined.configuration.logging.without.if"),
                  pair("class X {" +
                       "  void x(String s) {" +
                       "    LOG.debug(\"s1: \" + s);" +
                       "    if (LOG.isDebug()) {" +
                       "      LOG.debug(\"s2: \" + s);" +
                       "    }" +
                       "  }" +
                       "}",
                       asList("LOG.debug(\"s1: \" + s);")));
    testCases.put(SSRBundle.message("predefined.configuration.method.calls"),
                  pair("class X {" +
                       "  void x() {" +
                       "    System.out.println();" +
                       "    System.out.println(1);" +
                       "    x();" +
                       "  }" +
                       "}",
                       asList("System.out.println()",
                              "System.out.println(1)",
                              "x()")));
    testCases.put(SSRBundle.message("predefined.configuration.constructors.of.the.class"),
                  pair("class X {" +
                       "  X() {}" +
                       "  X(int i) {" +
                       "    System.out.println(i);" +
                       "  }" +
                       "  X(String... ss) {}" +
                       "  void m() {}" +
                       "}",
                       asList("X() {}",
                              "X(int i) {    System.out.println(i);  }",
                              "X(String... ss) {}")));
  }

  public void testPredefinedConfigurations() {
    final Configuration[] templates = JavaPredefinedConfigurations.createPredefinedTemplates();
    for (Configuration template : templates) {
      final Pair<String, List<String>> testCase = testCases.get(template.getName());
      if (testCase == null) continue; // todo fail when not all predefined templates are covered
      if (!(template instanceof SearchConfiguration)) fail();
      final SearchConfiguration searchConfiguration = (SearchConfiguration)template;
      options = searchConfiguration.getMatchOptions();
      final List<MatchResult> matches = testMatcher.testFindMatches(testCase.first, options, true, StdFileTypes.JAVA, null, false);
      assertEquals(matches.size(), testCase.second.size());
      for (int i = 0; i < matches.size(); i++) {
        final String matchText = StructuralSearchUtil.getPresentableElement(matches.get(i).getMatch()).getText();
        assertEquals(template.getName(), testCase.second.get(i), matchText);
      }
    }
  }
}