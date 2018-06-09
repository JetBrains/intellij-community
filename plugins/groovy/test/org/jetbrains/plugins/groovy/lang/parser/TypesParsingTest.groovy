// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser

/**
 * @author peter
 */
class TypesParsingTest extends GroovyParsingTestCase {
  @Override
  String getBasePath() {
    return super.basePath + "types"
  }

  void testAnn_def1() throws Throwable { doTest() }

  void testAnn_def2() throws Throwable { doTest() }

  void testAnn_def3() throws Throwable { doTest() }

  void testIdentifierAfterAnnotationMethod() { doTest() }

  void testDefault1() throws Throwable { doTest() }

  void testDefault2() throws Throwable { doTest() }

  void testType1() throws Throwable { doTest() }

  void testType10() throws Throwable { doTest() }

  void testType11() throws Throwable { doTest() }

  void testType12() throws Throwable { doTest() }

  void testType13() throws Throwable { doTest() }

  void testType14() throws Throwable { doTest() }

  void testType15() throws Throwable { doTest() }

  void testType2() throws Throwable { doTest() }

  void testType3() throws Throwable { doTest() }

  void testType4() throws Throwable { doTest() }

  void testType5() throws Throwable { doTest() }

  void testType6() throws Throwable { doTest() }

  void testType7() throws Throwable { doTest() }

  void testType8() throws Throwable { doTest() }

  void testType9() throws Throwable { doTest() }

  void testIdentifierInsteadOfImplements() { doTest() }

  void testInnerEnum() throws Throwable { doTest() }

  void testNewlineBeforeClassBrace() throws Throwable { doTest() }

  void testNewLineBeforeClassBraceAfterExtends() { doTest() }

  void testNewLineBeforeClassBraceAfterImplements() { doTest() }

  void testNewlineBeforeExtends() throws Throwable { doTest() }

  void testNewLineAfterMethodModifiers() { doTest() }

  void testNewLineAfterLAngleInTypeArgumentList() { doTest() }

  void testNewLineBeforeRAngleInTypeArgumentList() { doTest() }

  void testNewLineBetweenTypeArguments() { doTest() }

  void testNewLineBetweenTypeArgumentsError() { doTest() }

  void testNewLineBetweenExtendsImplements() { doTest() }

  void testStaticInitializer() throws Throwable { doTest() }

  void testInterfaceWithGroovyDoc() throws Throwable { doTest() }

  void testIncorrectParam1() { doTest() }

  void testIncorrectParameter2() { doTest() }

  void testIncorrectParam3() { doTest() }

  void testEmptyTypeArgs() { doTest() }

  void testIncompleteConstructor() { doTest() }

  void testmembers$identifierOnly() { doTest() }

  void testmembers$capitalIdentifierOnly() { doTest() }

  void testmembers$constructorIdentifierOnly() { doTest() }

  void testmembers$modifierListOnly() { doTest() }

  void testmembers$modifierListAndIdentifier() { doTest() }

  void testmembers$modifierListAndCapitalIdentifier() { doTest() }

  void testmembers$modifierListAndConstructorIdentifier() { doTest() }

  void testmembers$modifierListAndPrimitive() { doTest() }

  void testmembers$modifierListAndRefQualified() { doTest() }

  void testmembers$modifierListAndRefTypeArgs() { doTest() }

  void testmembers$modifierListAndTypeParameters() { doTest() }

  void testmembers$modifierListTypeParametersAndIdentifier() { doTest() }

  void testmembers$modifierListTypeParametersAndCapitalIdentifier() { doTest() }

  void testmembers$modifierListTypeParametersAndConstructorIdentifier() { doTest() }

  void testmembers$modifierListTypeParametersAndPrimitive() { doTest() }

  void testmembers$modifierListTypeParametersAndRefQualified() { doTest() }

  void testmembers$modifierListTypeParametersAndRefTypeArgs() { doTest() }

  void testmembers$modifierListTypeParametersIdentifierAndLeftParen() { doTest() }

  void testmembers$modifierListTypeParametersCapitalIdentifierAndLeftParen() { doTest() }

  void testmembers$modifierListTypeParametersConstructorIdentifierAndLeftParen() { doTest() }

  void testmembers$capitalIdentifierAndLeftParen() { doTest() }

  void testmembers$constructorIdentifierAndLeftParen() { doTest() }

  void testmembers$identifierAndLeftParen() { doTest() }

  void testmembers$constructorAfterInnerClass() { doTest() }
}