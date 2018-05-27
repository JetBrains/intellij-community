// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Bas Leijdekkers
 */
public class JavaPredefinedConfigurationsTest extends StructuralSearchTestCase {

  public void testAll() {
    final Configuration[] templates = JavaPredefinedConfigurations.createPredefinedTemplates();
    final Map<String, Configuration> configurationMap = Stream.of(templates).collect(Collectors.toMap(Configuration::getName, x -> x));
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.logging.without.if")),
           "class X {" +
           "  void x(String s) {" +
           "    LOG.debug(\"s1: \" + s);" +
           "    if (LOG.isDebug()) {" +
           "      LOG.debug(\"s2: \" + s);" +
           "    }" +
           "  }" +
           "}",
           "LOG.debug(\"s1: \" + s);");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.method.calls")),
           "class X {" +
           "  void x() {" +
           "    System.out.println();" +
           "    System.out.println(1);" +
           "    x();" +
           "  }" +
           "}",
           "System.out.println()", "System.out.println(1)", "x()");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.constructors.of.the.class")),
           "class X {" +
           "  X() {}" +
           "  X(int i) {" +
           "    System.out.println(i);" +
           "  }" +
           "  X(String... ss) {}" +
           "  void m() {}" +
           "}",
           "X() {}", "X(int i) {    System.out.println(i);  }", "X(String... ss) {}");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.class.with.parameterless.constructors")),
           "class X {" +
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
           "class X {  X() {}}", "class A {  void x(String names) {}}");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.try.without.resources")),
           "class X {{" +
           "  try {} finally {}" +
           "  try {" +
           "    ;" +
           "  } catch (RuntimeException e) {}" +
           "  try (resourceRef) {}" +
           "  try (AutoCloseable ac = null) {}" +
           "}}",
           "try {} finally {}");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.switch.with.branches")),
           "class X {{" +
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
           "switch (1) {" +
           "    case 1:" +
           "      break;" +
           "    case 2:" +
           "      System.out.println();" +
           "    case 3:" +
           "    default:" +
           "  }");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.string.concatenations")),
           "class X {" +
           "  String s = \"1\" + \"2\" + \"3\" + \"4\" + \"5\" + \"6\" + \"7\" + \"8\" + \"9\" + \"10\" + \"11\";" +
           "  String t = \"1\" + \"2\" + \"3\" + \"4\" + \"5\" + \"6\" + \"7\" + \"8\" + \"9\"+ \"10\";" +
           "}",
           "\"1\" + \"2\" + \"3\" + \"4\" + \"5\" + \"6\" + \"7\" + \"8\" + \"9\" + \"10\" + \"11\"");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.assert.without.description")),
           "class X {{" +
           "  assert true;" +
           "  assert false : false;" +
           "  assert false : \"reason\";" +
           "}}",
           "assert true;");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.labeled.break")),
           "class X {{" +
           "  break one;" +
           "  break;" +
           "  continue;" +
           "  continue here;" +
           "}}",
           "break one;");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.method.returns.bounded.wildcard")),
           "abstract class X {" +
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
           "List<? extends Number> one() {    return null;  }");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.generic.constructors")),
           "class X<U> {" +
           "  X() {}" +
           "  <T> X(String s) {}" +
           "  <T extends U, V> X(int i) {}" +
           "}",
           "<T> X(String s) {}", "<T extends U, V> X(int i) {}");
    //assertTrue("untested configurations: " + configurationMap.keySet(), configurationMap.isEmpty());
  }

  private void doTest(Configuration template, String source, String... results) {
    if (!(template instanceof SearchConfiguration)) fail();
    final SearchConfiguration searchConfiguration = (SearchConfiguration)template;
    options = searchConfiguration.getMatchOptions();
    final List<MatchResult> matches = testMatcher.testFindMatches(source, options, true, StdFileTypes.JAVA, null, false);
    assertEquals(template.getName(), results.length, matches.size());
    for (int i = 0; i < matches.size(); i++) {
      final String matchText = StructuralSearchUtil.getPresentableElement(matches.get(i).getMatch()).getText();
      assertEquals(template.getName(), results[i], matchText);
    }
  }
}