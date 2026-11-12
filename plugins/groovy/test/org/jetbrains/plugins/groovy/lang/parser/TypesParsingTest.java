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

  @Test
  public void testAnn_def1() { parsing.get().doTest(); }
  @Test
  public void testAnn_def2() { parsing.get().doTest(); }
  @Test
  public void testAnn_def3() { parsing.get().doTest(); }

  @Test
  public void testIdentifierAfterAnnotationMethod() { parsing.get().doTest(); }

  @Test
  public void testDefault1() { parsing.get().doTest(); }
  @Test
  public void testDefault2() { parsing.get().doTest(); }

  @Test
  public void testType1() { parsing.get().doTest(); }
  @Test
  public void testType2() { parsing.get().doTest(); }
  @Test
  public void testType3() { parsing.get().doTest(); }
  @Test
  public void testType4() { parsing.get().doTest(); }
  @Test
  public void testType5() { parsing.get().doTest(); }
  @Test
  public void testType6() { parsing.get().doTest(); }
  @Test
  public void testType7() { parsing.get().doTest(); }
  @Test
  public void testType8() { parsing.get().doTest(); }
  @Test
  public void testType9() { parsing.get().doTest(); }
  @Test
  public void testType10() { parsing.get().doTest(); }
  @Test
  public void testType11() { parsing.get().doTest(); }
  @Test
  public void testType12() { parsing.get().doTest(); }
  @Test
  public void testType13() { parsing.get().doTest(); }
  @Test
  public void testType14() { parsing.get().doTest(); }
  @Test
  public void testType15() { parsing.get().doTest(); }
  @Test
  public void testType16() { parsing.get().doTest(); }
  @Test
  public void testType17() { parsing.get().doTest(); }

  @Test
  public void testIdentifierInsteadOfImplements() { parsing.get().doTest(); }

  @Test
  public void testInnerEnum() { parsing.get().doTest(); }

  @Test
  public void testNewlineBeforeClassBrace() { parsing.get().doTest(); }
  @Test
  public void testNewLineBeforeClassBraceAfterExtends() { parsing.get().doTest(); }
  @Test
  public void testNewLineBeforeClassBraceAfterImplements() { parsing.get().doTest(); }
  @Test
  public void testNewlineBeforeExtends() { parsing.get().doTest(); }
  @Test
  public void testNewLineAfterMethodModifiers() { parsing.get().doTest(); }
  @Test
  public void testNewLineAfterLAngleInTypeArgumentList() { parsing.get().doTest(); }
  @Test
  public void testNewLineBeforeRAngleInTypeArgumentList() { parsing.get().doTest(); }
  @Test
  public void testNewLineBetweenTypeArguments() { parsing.get().doTest(); }
  @Test
  public void testNewLineBetweenTypeArgumentsError() { parsing.get().doTest(); }
  @Test
  public void testNewLineBetweenExtendsImplements() { parsing.get().doTest(); }

  @Test
  public void testStaticInitializer() { parsing.get().doTest(); }

  @Test
  public void testInterfaceWithGroovyDoc() { parsing.get().doTest(); }

  @Test
  public void testIncorrectParam1() { parsing.get().doTest(); }
  @Test
  public void testIncorrectParameter2() { parsing.get().doTest(); }
  @Test
  public void testIncorrectParam3() { parsing.get().doTest(); }

  @Test
  public void testEmptyTypeArgs() { parsing.get().doTest(); }

  @Test
  public void testIncompleteConstructor() { parsing.get().doTest(); }

  @Test
  public void testWeakKeywordType1() { parsing.get().doTest(); }

  @Test
  public void testWeakKeywordType2() { parsing.get().doTest(); }

  @Test
  public void testMembers$identifierOnly() { parsing.get().doTest(); }
  @Test
  public void testMembers$capitalIdentifierOnly() { parsing.get().doTest(); }
  @Test
  public void testMembers$constructorIdentifierOnly() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListOnly() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListAndIdentifier() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListAndCapitalIdentifier() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListAndConstructorIdentifier() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListAndPrimitive() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListAndRefQualified() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListAndRefTypeArgs() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListAndTypeParameters() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListTypeParametersAndIdentifier() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListTypeParametersAndCapitalIdentifier() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListTypeParametersAndConstructorIdentifier() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListTypeParametersAndPrimitive() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListTypeParametersAndRefQualified() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListTypeParametersAndRefTypeArgs() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListTypeParametersIdentifierAndLeftParen() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListTypeParametersCapitalIdentifierAndLeftParen() { parsing.get().doTest(); }
  @Test
  public void testMembers$modifierListTypeParametersConstructorIdentifierAndLeftParen() { parsing.get().doTest(); }
  @Test
  public void testMembers$capitalIdentifierAndLeftParen() { parsing.get().doTest(); }
  @Test
  public void testMembers$constructorIdentifierAndLeftParen() { parsing.get().doTest(); }
  @Test
  public void testMembers$identifierAndLeftParen() { parsing.get().doTest(); }
  @Test
  public void testMembers$constructorAfterInnerClass() { parsing.get().doTest(); }
  @Test
  public void testMembers$varField1() { parsing.get().doTest(); }
  @Test
  public void testMembers$varField2() { parsing.get().doTest(); }
  @Test
  public void testMembers$varField3() { parsing.get().doTest(); }
  @Test
  public void testMembers$varField4() { parsing.get().doTest(); }
  @Test
  public void testMembers$varField5() { parsing.get().doTest(); }
  @Test
  public void testMembers$varField6() { parsing.get().doTest(); }
  @Test
  public void testMembers$varField7() { parsing.get().doTest(); }
  @Test
  public void testMembers$varField8() { parsing.get().doTest(); }

  @Test
  public void testLowercaseTypeElement() { parsing.get().doTest(); }
}
