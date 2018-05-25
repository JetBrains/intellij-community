// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    testCases.put(SSRBundle.message("predefined.configuration.class.with.parameterless.constructors"),
                  pair("class X {" +
                       "  X() {}" +
                       "}" +
                       "class Y {" +
                       "  Y() {}" +
                       "  Y(String name) {}" +
                       "}" +
                       "class Z {" +
                       "  Z(String name) {}" +
                       "}" +
                       "class A {" +
                       "  void x(String names) {}" +
                       "}",
                       asList("class X {" +
                              "  X() {}" +
                              "}",
                              "class A {" +
                              "  void x(String names) {}" +
                              "}")));
    testCases.put(SSRBundle.message("predefined.configuration.try.without.resources"),
                  pair("class X {{" +
                       "  try {} finally {}" +
                       "  try {" +
                       "    ;" +
                       "  } catch (RuntimeException e) {}" +
                       "  try (resourceRef) {}" +
                       "  try (AutoCloseable ac = null) {}" +
                       "}}",
                       asList("try {} finally {}")));
    testCases.put(SSRBundle.message("predefined.configuration.switch.with.branches"),
                  pair("class X {{" +
                       "  switch (1) {" +
                       "    case 1:" +
                       "      break;" +
                       "    case 2:" +
                       "      System.out.println();" +
                       "    case 3:" +
                       "    default:" +
                       "  }" +
                       "  switch (1) {" +
                       "    case 1:" +
                       "     break;" +
                       "    case 2:" +
                       "      System.out.println();" +
                       "    case 3:" +
                       "    case 4:" +
                       "    default:" +
                       "  }" +
                       "}}",
                       asList("switch (1) {" +
                              "    case 1:" +
                              "      break;" +
                              "    case 2:" +
                              "      System.out.println();" +
                              "    case 3:" +
                              "    default:" +
                              "  }")));
    testCases.put(SSRBundle.message("predefined.configuration.string.concatenations"),
                  pair("class X {{" +
                       "  String s = \"1\" + \"2\" + \"3\" + \"4\" + \"5\" + \"6\" + \"7\" + \"8\" + \"9\" + \"10\" + \"11\";" +
                       "  String t = \"1\" + \"2\" + \"3\" + \"4\" + \"5\" + \"6\" + \"7\" + \"8\" + \"9\"+ \"10\";",
                       asList("\"1\" + \"2\" + \"3\" + \"4\" + \"5\" + \"6\" + \"7\" + \"8\" + \"9\" + \"10\" + \"11\"")));
    testCases.put(SSRBundle.message("predefined.configuration.assert.without.description"),
                  pair("class X {{" +
                       "  assert true;" +
                       "  assert false : false;" +
                       "  assert false : \"reason\";" +
                       "}}",
                       asList("assert true;")));
    testCases.put(SSRBundle.message("predefined.configuration.labeled.break"),
                  pair("class X {{" +
                       "  break one;" +
                       "  break;" +
                       "  continue;" +
                       "  continue here;" +
                       "}}",
                       asList("break one;")));
    testCases.put(SSRBundle.message("predefined.configuration.method.returns.bounded.wildcard"),
                  pair("abstract class X {" +
                       "  List<? extends Number> one() {" +
                       "    return null;" +
                       "  }" +
                       "  abstract List<? extends Number> ignore();" +
                       "  List<?> two() {" +
                       "    return null;" +
                       "  }" +
                       "  <T extends Number> T three() {" +
                       "    return null;" +
                       "  }" +
                       "}",
                       asList("List<? extends Number> one() {" +
                              "    return null;" +
                              "  }")));
    testCases.put(SSRBundle.message("predefined.configuration.generic.constructors"),
                  pair("class X<U> {" +
                       "  X() {}" +
                       "  <T> X(String s) {}" +
                       "  <T extends U, V> X(int i) {}" +
                       "}",
                       asList("<T> X(String s) {}",
                              "<T extends U, V> X(int i) {}")));
  }

  public void testPredefinedConfigurations() {
    for (Configuration template : JavaPredefinedConfigurations.createPredefinedTemplates()) {
      final Pair<String, List<String>> testCase = testCases.get(template.getName());
      if (testCase == null) continue; // todo fail when not all predefined templates are covered
      if (!(template instanceof SearchConfiguration)) fail();
      final SearchConfiguration searchConfiguration = (SearchConfiguration)template;
      options = searchConfiguration.getMatchOptions();
      final List<MatchResult> matches = testMatcher.testFindMatches(testCase.first, options, true, StdFileTypes.JAVA, null, false);
      assertEquals(template.getName(), testCase.second.size(), matches.size());
      for (int i = 0; i < matches.size(); i++) {
        final String matchText = StructuralSearchUtil.getPresentableElement(matches.get(i).getMatch()).getText();
        assertEquals(template.getName(), testCase.second.get(i), matchText);
      }
    }
  }
}