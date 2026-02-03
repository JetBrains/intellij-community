// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

public class TypesParsingTest extends GroovyParsingTestCase {
  @Override
  public String getBasePath() {
    return super.getBasePath() + "types";
  }

  public void testAnn_def1() { doTest(); }

  public void testAnn_def2() { doTest(); }

  public void testAnn_def3() { doTest(); }

  public void testIdentifierAfterAnnotationMethod() { doTest(); }

  public void testDefault1() { doTest(); }

  public void testDefault2() { doTest(); }

  public void testType1() { doTest(); }

  public void testType10() { doTest(); }

  public void testType11() { doTest(); }

  public void testType12() { doTest(); }

  public void testType13() { doTest(); }

  public void testType14() { doTest(); }

  public void testType15() { doTest(); }

  public void testType2() { doTest(); }

  public void testType3() { doTest(); }

  public void testType4() { doTest(); }

  public void testType5() { doTest(); }

  public void testType6() { doTest(); }

  public void testType7() { doTest(); }

  public void testType8() { doTest(); }

  public void testType9() { doTest(); }

  public void testType16() { doTest(); }

  public void testType17() { doTest(); }

  public void testIdentifierInsteadOfImplements() { doTest(); }

  public void testInnerEnum() { doTest(); }

  public void testNewlineBeforeClassBrace() { doTest(); }

  public void testNewLineBeforeClassBraceAfterExtends() { doTest(); }

  public void testNewLineBeforeClassBraceAfterImplements() { doTest(); }

  public void testNewlineBeforeExtends() { doTest(); }

  public void testNewLineAfterMethodModifiers() { doTest(); }

  public void testNewLineAfterLAngleInTypeArgumentList() { doTest(); }

  public void testNewLineBeforeRAngleInTypeArgumentList() { doTest(); }

  public void testNewLineBetweenTypeArguments() { doTest(); }

  public void testNewLineBetweenTypeArgumentsError() { doTest(); }

  public void testNewLineBetweenExtendsImplements() { doTest(); }

  public void testStaticInitializer() { doTest(); }

  public void testInterfaceWithGroovyDoc() { doTest(); }

  public void testIncorrectParam1() { doTest(); }

  public void testIncorrectParameter2() { doTest(); }

  public void testIncorrectParam3() { doTest(); }

  public void testEmptyTypeArgs() { doTest(); }

  public void testIncompleteConstructor() { doTest(); }

  public void _testWeak_keyword_type1() { doTest(); }

  public void testmembers$identifierOnly() { doTest(); }

  public void testmembers$capitalIdentifierOnly() { doTest(); }

  public void testmembers$constructorIdentifierOnly() { doTest(); }

  public void testmembers$modifierListOnly() { doTest(); }

  public void testmembers$modifierListAndIdentifier() { doTest(); }

  public void testmembers$modifierListAndCapitalIdentifier() { doTest(); }

  public void testmembers$modifierListAndConstructorIdentifier() { doTest(); }

  public void testmembers$modifierListAndPrimitive() { doTest(); }

  public void testmembers$modifierListAndRefQualified() { doTest(); }

  public void testmembers$modifierListAndRefTypeArgs() { doTest(); }

  public void testmembers$modifierListAndTypeParameters() { doTest(); }

  public void testmembers$modifierListTypeParametersAndIdentifier() { doTest(); }

  public void testmembers$modifierListTypeParametersAndCapitalIdentifier() { doTest(); }

  public void testmembers$modifierListTypeParametersAndConstructorIdentifier() { doTest(); }

  public void testmembers$modifierListTypeParametersAndPrimitive() { doTest(); }

  public void testmembers$modifierListTypeParametersAndRefQualified() { doTest(); }

  public void testmembers$modifierListTypeParametersAndRefTypeArgs() { doTest(); }

  public void testmembers$modifierListTypeParametersIdentifierAndLeftParen() { doTest(); }

  public void testmembers$modifierListTypeParametersCapitalIdentifierAndLeftParen() { doTest(); }

  public void testmembers$modifierListTypeParametersConstructorIdentifierAndLeftParen() { doTest(); }

  public void testmembers$capitalIdentifierAndLeftParen() { doTest(); }

  public void testmembers$constructorIdentifierAndLeftParen() { doTest(); }

  public void testmembers$identifierAndLeftParen() { doTest(); }

  public void testmembers$constructorAfterInnerClass() { doTest(); }

  public void testmembers$varField1() { doTest(); }

  public void testmembers$varField2() { doTest(); }

  public void testmembers$varField3() { doTest(); }

  public void testmembers$varField4() { doTest(); }

  public void testmembers$varField5() { doTest(); }

  public void testmembers$varField6() { doTest(); }

  public void testmembers$varField7() { doTest(); }

  public void testmembers$varField8() { doTest(); }

  public void testLowercaseTypeElement() { doTest(); }
}
