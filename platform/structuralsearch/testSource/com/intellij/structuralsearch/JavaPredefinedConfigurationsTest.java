// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.plugin.ui.Configuration;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Bas Leijdekkers
 */
public class JavaPredefinedConfigurationsTest extends PredefinedConfigurationsTestCase {
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
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.all.methods.of.the.class.within.hierarchy")),
           "class X {}", JavaFileType.INSTANCE,
           PsiElement::getText,
           "registerNatives", "getClass", "hashCode", "equals", "clone", "toString", "notify", "notifyAll", "wait", "wait", "wait", "finalize");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.methods.with.final.parameters")),
           "class X {" +
           "  int myI;" +
           "  X(final int i) {" +
           "    myI = i;" +
           "  }" +
           "  public void m(final int i, int j, int k) {" +
           "    System.out.println(i);" +
           "  }" +
           "  void n() {}" +
           "  void o(String s) {}" +
           "}",
           "X(final int i) {    myI = i;  }", "public void m(final int i, int j, int k) {    System.out.println(i);  }");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.methods.of.the.class")),
           "abstract class X {" +
           "  X() {}" +
           "  X(String s) {}" +
           "  abstract void x();" +
           "  int x(int i) {}" +
           "  boolean x(double d, Object o) {}" +
           "}",
           "X() {}", "X(String s) {}", "abstract void x();", "int x(int i) {}", "boolean x(double d, Object o) {}");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.class.static.blocks")),
           "class X {" +
           "  static {}" +
           "  static {" +
           "    System.out.println();" +
           "  }" +
           "  {" +
           "    {}" +
           "  }" +
           "}",
           "static {}", "static {    System.out.println();  }");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.class.any.initialization.blocks")),
           "class X {" +
           "  static {}" +
           "  static {" +
           "    System.out.println();" +
           "  }" +
           "  {" +
           "    {}" +
           "  }" +
           "}",
           "static {}", "static {    System.out.println();  }", "{    {}  }");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.static.fields.without.final")),
           "class X {" +
           "  int i1 = 1;" +
           "  static int i2 = 2;" +
           "  static final int i3 = 3;" +
           "}",
           "static int i2 = 2;");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.annotated.fields")),
           "class X {" +
           "  @SuppressWarnings(\"All\") @Deprecated" +
           "  private static final int YES = 0;" +
           "  @Deprecated" +
           "  String text = null;" +
           "  public static final int NO = 1;" +
           "}",
           "@SuppressWarnings(\"All\") @Deprecated  private static final int YES = 0;",
           "@Deprecated  String text = null;");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.javadoc.annotated.methods")),
           "class X {" +
           "  /** constructor */" +
           "  X() {}" +
           "" +
           "  /** */" +
           "  void x() {}" +
           "" +
           "  /** @deprecated */" +
           "  void y() {}" +
           "" +
           "  /**" +
           "   * important" +
           "   * method" +
           "   * @param i  the value that will be returned" +
           "   */" +
           "   int z(int i) {" +
           "     return i;" +
           "   }" +
           "" +
           "  void a() {}" +
           "}",
           "/** constructor */" +
           "  X() {}",
           "/** */" +
           "  void x() {}",
           "/** @deprecated */" +
           "  void y() {}",
           "/**" +
           "   * important" +
           "   * method" +
           "   * @param i  the value that will be returned" +
           "   */" +
           "   int z(int i) {" +
           "     return i;" +
           "   }");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.switches")),
           "class X {{" +
           "  int i = switch (1) {" +
           "            default -> {}" +
           "          }" +
           "  switch (2) {" +
           "    case 1,2:" +
           "      break;" +
           "    default:" +
           "  }" +
           "}}",
           "switch (1) {" +
           "            default -> {}" +
           "          }",
           "switch (2) {" +
           "    case 1,2:" +
           "      break;" +
           "    default:" +
           "  }");
    doTest(configurationMap.remove(SSRBundle.message("predefined.configuration.comments.containing.word")),
           "// bug\n" +
           "/* bugs are here */\n" +
           "/**\n" +
           "* may\n" +
           "* contain\n" +
           "* one bug\n" +
           "*/\n" +
           "/* buggy */\n" +
           "// bug?",
           "// bug",
           "/**\n"+
           "* may\n" +
           "* contain\n" +
           "* one bug\n" +
           "*/",
           "// bug?");
    //assertTrue((templates.length - configurationMap.size()) + " of " + templates.length +
    //           " existing templates tested. Untested templates: " + configurationMap.keySet(), configurationMap.isEmpty());
  }

  protected void doTest(Configuration template, String source, String... results) {
    doTest(template, source, JavaFileType.INSTANCE, results);
  }
}