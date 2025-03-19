// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

public class GenericsParsingTest extends GroovyParsingTestCase {
  public void testErr1() { doTest(); }

  public void testErr2() { doTest(); }

  public void testErr3() { doTest(); }

  public void testErr4() { doTest(); }

  public void testGenmethod1() { doTest(); }

  public void testGenmethod2() { doTest(); }

  public void testGenmethod3() { doTest(); }

  public void testGenmethod4() { doTest(); }

  public void testGenmethod5() { doTest(); }

  public void testTypeargs1() { doTest(); }

  public void testTypeparam1() { doTest(); }

  public void testTypeparam2() { doTest(); }

  public void testTypeparam3() { doTest(); }

  public void testNewLineAfterAngle() { doTest(); }

  public void testNewLineAfterComma() { doTest(); }

  public void testNewLineBeforeAngle() { doTest(); }

  public void testNewLineBeforeComma() { doTest(); }

  public void testNewLineBeforeExtendsInTypeParameterBounds() { doTest(); }

  public void testNewLineAfterExtendsInTypeParameterBounds() { doTest(); }

  public void testNewLineBeforeAmpInTypeParameterBounds() { doTest(); }

  public void testNewLineAfterAmpInTypeParameterBounds() { doTest(); }

  public void testTopLevelMethodWithoutModifiers() { doTest(); }

  public void testClassLevelMethodWithoutModifiers() { doTest(); }

  public void testClassLevelMethodWithoutModifiers2() { doTest(); }

  @Override
  public final String getBasePath() {
    return basePath;
  }

  private final String basePath = super.getBasePath() + "generics";
}
