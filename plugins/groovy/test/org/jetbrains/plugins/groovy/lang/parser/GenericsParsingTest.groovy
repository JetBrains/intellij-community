// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser

/**
 * @author peter
 */
class GenericsParsingTest extends GroovyParsingTestCase {

  final String basePath = super.basePath + "generics"

  void testErr1() throws Throwable { doTest() }

  void testErr2() throws Throwable { doTest() }

  void testErr3() throws Throwable { doTest() }

  void testErr4() { doTest() }

  void testGenmethod1() throws Throwable { doTest() }

  void testGenmethod2() throws Throwable { doTest() }

  void testGenmethod3() throws Throwable { doTest() }

  void testGenmethod4() throws Throwable { doTest() }

  void testGenmethod5() { doTest() }

  void testTypeargs1() throws Throwable { doTest() }

  void testTypeparam1() throws Throwable { doTest() }

  void testTypeparam2() throws Throwable { doTest() }

  void testTypeparam3() throws Throwable { doTest() }

  void testNewLineAfterAngle() { doTest() }

  void testNewLineAfterComma() { doTest() }

  void testNewLineBeforeAngle() { doTest() }

  void testNewLineBeforeComma() { doTest() }

  void testNewLineBeforeExtendsInTypeParameterBounds() { doTest() }

  void testNewLineAfterExtendsInTypeParameterBounds() { doTest() }

  void testNewLineBeforeAmpInTypeParameterBounds() { doTest() }

  void testNewLineAfterAmpInTypeParameterBounds() { doTest() }
}
