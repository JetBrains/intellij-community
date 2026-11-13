// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyParsingFixtureKt.groovyParsingFixture;

@TestApplication
public class TypesParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "types");

  private void doTest() {
    parsing.get().doTest();
  }

  @Test public void testAnn_def1() { doTest(); }

  @Test public void testAnn_def2() { doTest(); }
  @Test public void testAnn_def3() { doTest(); }

  @Test public void testIdentifierAfterAnnotationMethod() { doTest(); }

  @Test public void testDefault1() { doTest(); }
  @Test public void testDefault2() { doTest(); }

  @Test public void testType1() { doTest(); }
  @Test public void testType2() { doTest(); }
  @Test public void testType3() { doTest(); }
  @Test public void testType4() { doTest(); }
  @Test public void testType5() { doTest(); }
  @Test public void testType6() { doTest(); }
  @Test public void testType7() { doTest(); }
  @Test public void testType8() { doTest(); }
  @Test public void testType9() { doTest(); }
  @Test public void testType10() { doTest(); }
  @Test public void testType11() { doTest(); }
  @Test public void testType12() { doTest(); }
  @Test public void testType13() { doTest(); }
  @Test public void testType14() { doTest(); }
  @Test public void testType15() { doTest(); }
  @Test public void testType16() { doTest(); }
  @Test public void testType17() { doTest(); }

  @Test public void testIdentifierInsteadOfImplements() { doTest(); }

  @Test public void testInnerEnum() { doTest(); }

  @Test public void testNewlineBeforeClassBrace() { doTest(); }
  @Test public void testNewLineBeforeClassBraceAfterExtends() { doTest(); }
  @Test public void testNewLineBeforeClassBraceAfterImplements() { doTest(); }
  @Test public void testNewlineBeforeExtends() { doTest(); }
  @Test public void testNewLineAfterMethodModifiers() { doTest(); }
  @Test public void testNewLineAfterLAngleInTypeArgumentList() { doTest(); }
  @Test public void testNewLineBeforeRAngleInTypeArgumentList() { doTest(); }
  @Test public void testNewLineBetweenTypeArguments() { doTest(); }
  @Test public void testNewLineBetweenTypeArgumentsError() { doTest(); }
  @Test public void testNewLineBetweenExtendsImplements() { doTest(); }

  @Test public void testStaticInitializer() { doTest(); }

  @Test public void testInterfaceWithGroovyDoc() { doTest(); }

  @Test public void testIncorrectParam1() { doTest(); }
  @Test public void testIncorrectParameter2() { doTest(); }
  @Test public void testIncorrectParam3() { doTest(); }

  @Test public void testEmptyTypeArgs() { doTest(); }

  @Test public void testIncompleteConstructor() { doTest(); }

  @Test public void testWeakKeywordType1() { doTest(); }

  @Test public void testWeakKeywordType2() { doTest(); }

  @Test public void testMembers$identifierOnly() { doTest(); }
  @Test public void testMembers$capitalIdentifierOnly() { doTest(); }
  @Test public void testMembers$constructorIdentifierOnly() { doTest(); }
  @Test public void testMembers$modifierListOnly() { doTest(); }
  @Test public void testMembers$modifierListAndIdentifier() { doTest(); }
  @Test public void testMembers$modifierListAndCapitalIdentifier() { doTest(); }
  @Test public void testMembers$modifierListAndConstructorIdentifier() { doTest(); }
  @Test public void testMembers$modifierListAndPrimitive() { doTest(); }
  @Test public void testMembers$modifierListAndRefQualified() { doTest(); }
  @Test public void testMembers$modifierListAndRefTypeArgs() { doTest(); }
  @Test public void testMembers$modifierListAndTypeParameters() { doTest(); }
  @Test public void testMembers$modifierListTypeParametersAndIdentifier() { doTest(); }
  @Test public void testMembers$modifierListTypeParametersAndCapitalIdentifier() { doTest(); }
  @Test public void testMembers$modifierListTypeParametersAndConstructorIdentifier() { doTest(); }
  @Test public void testMembers$modifierListTypeParametersAndPrimitive() { doTest(); }
  @Test public void testMembers$modifierListTypeParametersAndRefQualified() { doTest(); }
  @Test public void testMembers$modifierListTypeParametersAndRefTypeArgs() { doTest(); }
  @Test public void testMembers$modifierListTypeParametersIdentifierAndLeftParen() { doTest(); }
  @Test public void testMembers$modifierListTypeParametersCapitalIdentifierAndLeftParen() { doTest(); }
  @Test public void testMembers$modifierListTypeParametersConstructorIdentifierAndLeftParen() { doTest(); }
  @Test public void testMembers$capitalIdentifierAndLeftParen() { doTest(); }
  @Test public void testMembers$constructorIdentifierAndLeftParen() { doTest(); }
  @Test public void testMembers$identifierAndLeftParen() { doTest(); }
  @Test public void testMembers$constructorAfterInnerClass() { doTest(); }
  @Test public void testMembers$varField1() { doTest(); }
  @Test public void testMembers$varField2() { doTest(); }
  @Test public void testMembers$varField3() { doTest(); }
  @Test public void testMembers$varField4() { doTest(); }
  @Test public void testMembers$varField5() { doTest(); }
  @Test public void testMembers$varField6() { doTest(); }
  @Test public void testMembers$varField7() { doTest(); }
  @Test public void testMembers$varField8() { doTest(); }

  @Test public void testLowercaseTypeElement() { doTest(); }
}
