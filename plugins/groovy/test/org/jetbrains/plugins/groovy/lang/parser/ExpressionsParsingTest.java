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
public class ExpressionsParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "expressions");

  @Test
  public void testArguments$carg1() { parsing.get().doTest(); }
  @Test
  public void testArguments$carg2() { parsing.get().doTest(); }
  @Test
  public void testArguments$carg3() { parsing.get().doTest(); }
  @Test
  public void testArguments$cargs1() { parsing.get().doTest(); }
  @Test
  public void testArguments$cargs2() { parsing.get().doTest(); }
  @Test
  public void testArguments$cargs3() { parsing.get().doTest(); }
  @Test
  public void testArguments$cargs4() { parsing.get().doTest(); }
  @Test
  public void testArguments$cargs5() { parsing.get().doTest(); }
  @Test
  public void testArguments$cargs6() { parsing.get().doTest(); }

  @Test
  public void testArithmetic$add1() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$add2() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$addbug1() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$arif1() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$mul1() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$mul2() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$mul3() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$mul4() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$post1() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$sh1() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$shift5() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$shift6() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$newLineInsideParentheses() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$newLineInsideIfStatement() { parsing.get().doTest(); }
  @Test
  public void testArithmetic$un1() { parsing.get().doTest(); }

  @Test
  public void testAss1() { parsing.get().doTest(); }
  @Test
  public void testAss2() { parsing.get().doTest(); }
  @Test
  public void testAss3() { parsing.get().doTest(); }

  @Test
  public void testClosures$appended() { parsing.get().doTest(); }
  @Test
  public void testClosures$closparam1() { parsing.get().doTest(); }
  @Test
  public void testClosures$closparam2() { parsing.get().doTest(); }
  @Test
  public void testClosures$closparam3() { parsing.get().doTest(); }
  @Test
  public void testClosures$closparam4() { parsing.get().doTest(); }
  @Test
  public void testClosures$closparam5() { parsing.get().doTest(); }
  @Test
  public void testClosures$closparam6() { parsing.get().doTest(); }
  @Test
  public void testClosures$final_error() { parsing.get().doTest(); }
  @Test
  public void testClosures$param6() { parsing.get().doTest(); }
  @Test
  public void testClosures$param7() { parsing.get().doTest(); }
  @Test
  public void testClosures$withDefaultParam1() { parsing.get().doTest(); }
  @Test
  public void testClosures$withDefaultParam2() { parsing.get().doTest(); }
  public void _testClosures$withDefaultParam3() { parsing.get().doTest(); }
  public void _testClosures$withDefaultParam4() { parsing.get().doTest(); }
  @Test
  public void testClosures$withDefaultParam5() { parsing.get().doTest(); }

  @Test
  public void testConditional$con1() { parsing.get().doTest(); }
  @Test
  public void testConditional$con2() { parsing.get().doTest(); }
  @Test
  public void testConditional$elvis1() { parsing.get().doTest(); }
  @Test
  public void testConditional$elvis2() { parsing.get().doTest(); }
  @Test
  public void testConditional$elvisNlBeforeOperator() { parsing.get().doTest(); }
  @Test
  public void testConditional$ternaryQuestionOnly() { parsing.get().doTest(); }
  @Test
  public void testConditional$ternaryWithoutElse() { parsing.get().doTest(); }
  @Test
  public void testConditional$ternaryWithoutThen() { parsing.get().doTest(); }
  @Test
  public void testConditional$ternaryWithoutThenElse() { parsing.get().doTest(); }
  @Test
  public void testConditional$ternaryNLBeforeColon() { parsing.get().doTest(); }
  @Test
  public void testConditional$ternaryNLBeforeElse() { parsing.get().doTest(); }
  @Test
  public void testConditional$ternaryNLBeforeQuestion() { parsing.get().doTest(); }
  @Test
  public void testConditional$ternaryNLBeforeThen() { parsing.get().doTest(); }

  @Test
  public void testErrors$err_final() { parsing.get().doTest(); }

  @Test
  public void testGstring$daniel_sun() { parsing.get().doTest(); }
  @Test
  public void testGstring$gravy16532() { parsing.get().doTest("gstring/gravy-1653-2.test"); }
  @Test
  public void testGstring$grvy1653() { parsing.get().doTest("gstring/grvy-1653.test"); }

  @Test
  public void testGstring$gstr3() { parsing.get().doTest(); }
  @Test
  public void testGstring$standTrooper() { parsing.get().doTest(); }
  @Test
  public void testGstring$str1() { parsing.get().doTest(); }
  @Test
  public void testGstring$str2() { parsing.get().doTest(); }
  @Test
  public void testGstring$str3() { parsing.get().doTest(); }
  @Test
  public void testGstring$str4() { parsing.get().doTest(); }
  @Test
  public void testGstring$str5() { parsing.get().doTest(); }
  @Test
  public void testGstring$str6() { parsing.get().doTest(); }
  @Test
  public void testGstring$str7() { parsing.get().doTest(); }
  @Test
  public void testGstring$str8() { parsing.get().doTest(); }
  @Test
  public void testGstring$str9() { parsing.get().doTest(); }

  @Test
  public void testString$singleQuoted() { parsing.get().doTest(); }
  @Test
  public void testString$tripleSingleQuoted$ok() { parsing.get().doTest(); }
  @Test
  public void testString$tripleSingleQuoted$err0() { parsing.get().doTest(); }
  @Test
  public void testString$tripleSingleQuoted$err1() { parsing.get().doTest(); }
  @Test
  public void testString$tripleSingleQuoted$err2() { parsing.get().doTest(); }
  @Test
  public void testString$tripleSingleQuoted$err3() { parsing.get().doTest(); }
  @Test
  public void testString$tripleSingleQuoted$err4() { parsing.get().doTest(); }
  @Test
  public void testString$tripleSingleQuoted$err5() { parsing.get().doTest(); }
  @Test
  public void testString$tripleSingleQuoted$unfinished0() { parsing.get().doTest(); }
  @Test
  public void testString$tripleSingleQuoted$unfinished1() { parsing.get().doTest(); }
  @Test
  public void testString$tripleSingleQuoted$unfinished2() { parsing.get().doTest(); }
  @Test
  public void testString$tripleSingleQuoted$unfinished3() { parsing.get().doTest(); }

  @Test
  public void testGstring$str_error1() { parsing.get().doTest(); }
  @Test
  public void testGstring$str_error2() { parsing.get().doTest(); }
  @Test
  public void testGstring$str_error3() { parsing.get().doTest(); }
  @Test
  public void testGstring$str_error4() { parsing.get().doTest(); }
  @Test
  public void testGstring$str_error5() { parsing.get().doTest(); }
  @Test
  public void testGstring$str_error6() { parsing.get().doTest(); }
  @Test
  public void testGstring$str_error7() { parsing.get().doTest(); }
  @Test
  public void testGstring$str_error8() { parsing.get().doTest(); }
  @Test
  public void testGstring$triple$triple1() { parsing.get().doTest(); }
  @Test
  public void testGstring$triple$triple2() { parsing.get().doTest(); }
  @Test
  public void testGstring$triple$triple3() { parsing.get().doTest(); }
  @Test
  public void testGstring$triple$triple4() { parsing.get().doTest(); }
  @Test
  public void testGstring$triple$quote_and_slash() { parsing.get().doTest(); }
  @Test
  public void testGstring$ugly_lexer() { parsing.get().doTest(); }
  @Test
  public void testGstring$this() { parsing.get().doTest(); }
  @Test
  public void testGstring$newline() { parsing.get().doTest(); }

  @Test
  public void testMapLiteral() { parsing.get().doTest(); }

  @Test
  public void testMapLiteralNewline() { parsing.get().doTest(); }

  @Test
  public void testMapKeys() { parsing.get().doTest(); }

  @Test
  public void testNamedArgumentKeys() { parsing.get().doTest(); }

  @Test
  public void testExpressionlabelWithoutExpression() { parsing.get().doTest(); }

  @Test
  public void testNew$arr_decl() { parsing.get().doTest(); }
  @Test
  public void testNew$emptyTypeArgs() { parsing.get().doTest(); }
  @Test
  public void testNew$noArgumentList() { parsing.get().doTest(); }
  @Test
  public void testNew$emptyArrayInitializer() { parsing.get().doTest(); }
  @Test
  public void testNew$arrayInitializer() { parsing.get().doTest(); }
  @Test
  public void testNew$arrayInitializerTrailingComma() { parsing.get().doTest(); }
  @Test
  public void testNew$nlInsideArrayDeclaration() { parsing.get().doTest(); }
  @Test
  public void testNew$nlBeforeArrayDeclaration() { parsing.get().doTest(); }
  @Test
  public void testNew$nlInsideArrayDeclarationWithExpression() { parsing.get().doTest(); }
  @Test
  public void testNew$nestedArrayInitializer() { parsing.get().doTest(); }
  @Test
  public void testNew$emptyNestedArrayInitializer() { parsing.get().doTest(); }
  @Test
  public void testNew$nestedArrayInitializerWithClosure() { parsing.get().doTest(); }
  @Test
  public void testNew$nestedArrayInitializerWithLambda() { parsing.get().doTest(); }
  @Test
  public void testNew$nestedArrayInitializerCommaAfterLastElement() { parsing.get().doTest(); }
  @Test
  public void testNew$nestedArrayInitializerNewLinesInside() { parsing.get().doTest(); }
  @Test
  public void testNew$noInitializer() { parsing.get().doTest(); }
  @Test
  public void testNew$noClosingBrace() { parsing.get().doTest(); }
  @Test
  public void testNew$closureAfterArrayDeclaration() { parsing.get().doTest(); }
  @Test
  public void testNew$closureAfterArrayInitializer() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine2() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine3() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine4() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine5() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine6() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine7() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine8() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine9() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine10() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine11() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine12() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine13() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine14() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine15() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine16() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine17() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine18() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine19() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine20() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine21() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine22() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine23() { parsing.get().doTest(); }
  @Test
  public void testNew$newLine24() { parsing.get().doTest(); }

  @Test
  public void testAnonymous$anonymous() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous1() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous2() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous3() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous4() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous5() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous6() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous7() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous8() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous9() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous10() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous11() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous12() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous13() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous14() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous15() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous16() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$anonymous17() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$newlineBeforeBodyInCall() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$newLineInsideParentheses() { parsing.get().doTest(); }
  @Test
  public void testAnonymous$newLineInsideIfStatement() { parsing.get().doTest(); }

  @Test
  public void testNumbers() { parsing.get().doTest(); }

  @Test
  public void testParenthed$exprInParenth() { parsing.get().doTest(); }
  @Test
  public void testParenthed$paren1() { parsing.get().doTest(); }
  @Test
  public void testParenthed$paren2() { parsing.get().doTest(); }
  @Test
  public void testParenthed$paren3() { parsing.get().doTest(); }
  @Test
  public void testParenthed$paren4() { parsing.get().doTest(); }
  @Test
  public void testParenthed$paren5() { parsing.get().doTest(); }
  @Test
  public void testParenthed$paren6() { parsing.get().doTest(); }
  @Test
  public void testParenthed$newLineAfterOpeningParenthesis() { parsing.get().doTest(); }
  @Test
  public void testParenthed$newLineBeforeClosingParenthesis() { parsing.get().doTest(); }
  @Test
  public void testParenthed$capitalNamedArgument() { parsing.get().doTest(); }
  @Test
  public void testParenthed$capitalListArgument() { parsing.get().doTest(); }

  @Test
  public void testPath$method$ass4() { parsing.get().doTest(); }
  @Test
  public void testPath$method$clazz1() { parsing.get().doTest(); }
  @Test
  public void testPath$method$clazz2() { parsing.get().doTest(); }
  @Test
  public void testPath$method$clos1() { parsing.get().doTest(); }
  @Test
  public void testPath$method$clos2() { parsing.get().doTest(); }
  @Test
  public void testPath$method$clos3() { parsing.get().doTest(); }
  @Test
  public void testPath$method$clos4() { parsing.get().doTest(); }
  @Test
  public void testPath$method$ind1() { parsing.get().doTest(); }
  @Test
  public void testPath$method$ind2() { parsing.get().doTest(); }
  @Test
  public void testPath$method$ind3() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method1() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method10() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method11() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method12() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method13() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method2() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method3() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method4() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method5() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method6() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method7() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method8() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method9() { parsing.get().doTest(); }
  @Test
  public void testPath$method$newLineBeforeOperatorInCall() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method14() { parsing.get().doTest(); }
  @Test
  public void testPath$method$method15() { parsing.get().doTest(); }
  @Test
  public void testPath$path1() { parsing.get().doTest(); }
  @Test
  public void testPath$path13() { parsing.get().doTest(); }
  @Test
  public void testPath$path14() { parsing.get().doTest(); }
  @Test
  public void testPath$path15() { parsing.get().doTest(); }
  @Test
  public void testPath$path2() { parsing.get().doTest(); }
  @Test
  public void testPath$path4() { parsing.get().doTest(); }
  @Test
  public void testPath$path5() { parsing.get().doTest(); }
  @Test
  public void testPath$path6() { parsing.get().doTest(); }
  @Test
  public void testPath$path7() { parsing.get().doTest(); }
  @Test
  public void testPath$path8() { parsing.get().doTest(); }
  @Test
  public void testPath$path9() { parsing.get().doTest(); }
  @Test
  public void testPath$path16() { parsing.get().doTest(); }
  @Test
  public void testPath$path17() { parsing.get().doTest(); }
  @Test
  public void testPath$path18() { parsing.get().doTest(); }
  @Test
  public void testPath$path19() { parsing.get().doTest(); }
  @Test
  public void testPath$regexp() { parsing.get().doTest(); }
  @Test
  public void testPath$typeVsExpr() { parsing.get().doTest(); }
  @Test
  public void testPath$stringMethodCall1() { parsing.get().doTest(); }
  @Test
  public void testPath$stringMethodCall2() { parsing.get().doTest(); }
  @Test
  public void testPath$stringMethodCall3() { parsing.get().doTest(); }

  @Test
  public void testReferences$ref1() { parsing.get().doTest(); }
  @Test
  public void testReferences$ref2() { parsing.get().doTest(); }
  @Test
  public void testReferences$ref3() { parsing.get().doTest(); }
  @Test
  public void testReferences$ref4() { parsing.get().doTest(); }
  @Test
  public void testReferences$ref5() { parsing.get().doTest(); }
  @Test
  public void testReferences$ref6() { parsing.get().doTest(); }
  @Test
  public void testReferences$ref7() { parsing.get().doTest(); }
  @Test
  public void testReferences$ref8() { parsing.get().doTest(); }
  @Test
  public void testReferences$ref9() { parsing.get().doTest(); }
  @Test
  public void testReferences$ref10() { parsing.get().doTest(); }
  @Test
  public void testReferences$ref11() { parsing.get().doTest(); }
  @Test
  public void testReferences$ref12() { parsing.get().doTest(); }
  @Test
  public void testReferences$keywords() { parsing.get().doTest(); }
  @Test
  public void testReferences$emptyTypeArgs() { parsing.get().doTest(); }
  @Test
  public void testReferences$dots() { parsing.get().doTest(); }

  @Test
  public void testRegex$chen() { parsing.get().doTest(); }
  @Test
  public void testRegex$GRVY1509err() { parsing.get().doTest("regex/GRVY-1509err.test"); }
  @Test
  public void testRegex$GRVY1509norm() { parsing.get().doTest("regex/GRVY-1509norm.test"); }
  @Test
  public void testRegex$GRVY1509test() { parsing.get().doTest("regex/GRVY-1509test.test"); }
  @Test
  public void testRegex$regex1() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex10() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex11() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex12() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex13() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex14() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex15() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex16() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex17() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex18() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex19() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex2() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex20() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex21() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex22() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex23() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex24() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex25() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex3() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex33() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex4() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex5() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex6() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex7() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex8() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex9() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex_begin() { parsing.get().doTest(); }
  @Test
  public void testRegex$regex_begin2() { parsing.get().doTest(); }
  @Test
  public void testRegex$slashyEq() { parsing.get().doTest(); }
  @Test
  public void testRegex$multiLineSlashy() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashy() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashy2() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashy3() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashy4() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashy5() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashy6() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashy7() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashy8() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashy9() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashy10() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashy11() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashyCode() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashyCodeUnfinished() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashyEof() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashyRegex() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashyRegexFinishedTwice() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashyRegexUnfinished() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashyUnfinished() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashyWindowsPaths() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashyXml() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashyDouble() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashyTriple() { parsing.get().doTest(); }
  @Test
  public void testRegex$dollarSlashyUltimate() { parsing.get().doTest(); }
  @Test
  public void testRegex$afterNewLine() { parsing.get().doTest(); }
  @Test
  public void testRegex$afterDollarSlashyString() { parsing.get().doTest(); }
  @Test
  public void testRegex$afterDoubleQuotedString() { parsing.get().doTest(); }
  @Test
  public void testRegex$afterSingleQuotedString() { parsing.get().doTest(); }
  @Test
  public void testRegex$afterSlashyString() { parsing.get().doTest(); }
  @Test
  public void testRegex$afterTripleSingleQuotedString() { parsing.get().doTest(); }
  @Test
  public void testRegex$afterTripleDoubleQuotedString() { parsing.get().doTest(); }

  @Test
  public void testRelational$eq1() { parsing.get().doTest(); }
  @Test
  public void testRelational$inst0() { parsing.get().doTest(); }
  @Test
  public void testRelational$inst1() { parsing.get().doTest(); }
  @Test
  public void testRelational$inst2() { parsing.get().doTest(); }
  @Test
  public void testRelational$rel1() { parsing.get().doTest(); }
  @Test
  public void testRelational$newlineAfterOperator() { parsing.get().doTest(); }
  @Test
  public void testRelational$noRValue() { parsing.get().doTest(); }
  @Test
  public void testRelational$exclamationAfterExpression() { parsing.get().doTest(); }
  @Test
  public void testRelational$inNegated() { parsing.get().doTest(); }
  @Test
  public void testRelational$inNegatedWithSpace() { parsing.get().doTest(); }
  @Test
  public void testRelational$inNegatedIdentifier() { parsing.get().doTest(); }
  @Test
  public void testRelational$instanceOfNegated() { parsing.get().doTest(); }
  @Test
  public void testRelational$instanceOfNegatedWithSpace() { parsing.get().doTest(); }
  @Test
  public void testRelational$instanceOfNegatedIdentifier() { parsing.get().doTest(); }

  @Test
  public void testSpecial$grvy1173() { parsing.get().doTest(); }
  @Test
  public void testSpecial$list1() { parsing.get().doTest(); }
  @Test
  public void testSpecial$list2() { parsing.get().doTest(); }
  @Test
  public void testSpecial$list3() { parsing.get().doTest(); }
  @Test
  public void testSpecial$map1() { parsing.get().doTest(); }
  @Test
  public void testSpecial$map2() { parsing.get().doTest(); }
  @Test
  public void testSpecial$map3() { parsing.get().doTest(); }
  @Test
  public void testSpecial$map4() { parsing.get().doTest(); }
  @Test
  public void testSpecial$map5() { parsing.get().doTest(); }
  @Test
  public void testSpecial$map6() { parsing.get().doTest(); }
  @Test
  public void testSpecial$map7() { parsing.get().doTest(); }
  @Test
  public void testSpecial$map8() { parsing.get().doTest(); }
  @Test
  public void testSpecial$paren13() { parsing.get().doTest(); }

  @Test
  public void testTypecast$castToObject() { parsing.get().doTest(); }
  @Test
  public void testTypecast$una1() { parsing.get().doTest(); }
  @Test
  public void testTypecast$una2() { parsing.get().doTest(); }
  @Test
  public void testTypecast$una3() { parsing.get().doTest(); }
  @Test
  public void testTypecast$una4() { parsing.get().doTest(); }
  @Test
  public void testTypecast$una5() { parsing.get().doTest(); }
  @Test
  public void testTypecast$una6() { parsing.get().doTest(); }
  @Test
  public void testTypecast$elvis() { parsing.get().doTest(); }
  @Test
  public void testTypecast$equality() { parsing.get().doTest(); }
  @Test
  public void testTypecast$parenthesized() { parsing.get().doTest(); }
  @Test
  public void testTypecast$noExpression() { parsing.get().doTest(); }
  @Test
  public void testTypecast$parenthesizedOperand() { parsing.get().doTest(); }
  @Test
  public void testTypecast$parenthesizedQualifier() { parsing.get().doTest(); }
  @Test
  public void testTypecast$parenthesizedOperandError() { parsing.get().doTest(); }
  @Test
  public void testTypecast$nested() { parsing.get().doTest(); }
  @Test
  public void testTypecast$vsMethodCall() { parsing.get().doTest(); }
  @Test
  public void testTypecast$conditional() { parsing.get().doTest(); }

  @Test
  public void testAtHang() { parsing.get().doTest(); }

  @Test
  public void testDollar() { parsing.get().doTest(); }

  @Test
  public void testNoArrowClosure() { parsing.get().doTest(); }

  @Test
  public void testNoArrowClosure2() { parsing.get().doTest(); }

  @Test
  public void testPropertyAccessError() { parsing.get().doTest(); }

  @Test
  public void testThis$qualifiedThis() { parsing.get().doTest(); }

  @Test
  public void testSuper$qualifiedSuper() { parsing.get().doTest(); }

  @Test
  public void testThis$this() { parsing.get().doTest(); }

  @Test
  public void testSuper$super() { parsing.get().doTest(); }

  @Test
  public void testBinary$implicationSimple() { parsing.get().doTest(); }
  @Test
  public void testBinary$implicationWithNewLineAfter() { parsing.get().doTest(); }
  @Test
  public void testBinary$implicationWithNewLineBefore() { parsing.get().doTest(); }
  @Test
  public void testBinary$implicationRightAssociativity() { parsing.get().doTest(); }
  @Test
  public void testBinary$implicationLowPriority() { parsing.get().doTest(); }
  @Test
  public void testBinary$identity() { parsing.get().doTest(); }
  @Test
  public void testBinary$elvisAssign() { parsing.get().doTest(); }
  @Test
  public void testBinary$elvisAssignNewLine() { parsing.get().doTest(); }
  @Test
  public void testBinary$elvisAssignWithoutRValue() { parsing.get().doTest(); }
  @Test
  public void testBinary$assignmentError() { parsing.get().doTest(); }

  @Test
  public void testCommandExpr$closureArg() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$simple() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$callArg1() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$callArg2() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$threeArgs1() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$threeArgs2() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$threeArgs3() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$fourArgs() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$fiveArgs() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$multiArgs() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$RHS() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$oddArgCount() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$indexAccess1() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$indexAccess2() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$indexAccess3() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$indexAccess4() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$closureArg2() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$closureArg3() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$closureArg4() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$closureArg5() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$not() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$methodCall() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$indexProperty() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$instanceof() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$instanceof2() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$in() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$as() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$arrayAccess() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$keywords() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$literalInvoked() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$literalInvokedWithUnfinishedLiteral() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$slashyInvoked() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$safeIndex() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$safeIndexEmpty() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$safeIndexEmptyMap() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$safeIndexLBrack() { parsing.get().doTest(); }
  @Test
  public void testCommandExpr$safeIndexMap() { parsing.get().doTest(); }

  @Test
  public void testDiamond() { parsing.get().doTest(); }

  @Test
  public void testDiamondErrors() { parsing.get().doTest(); }

  @Test
  public void testSpacesInStringAfterSlash() { parsing.get().doTest(); }

  @Test
  public void testDiamondInPathRefElement() { parsing.get().doTest(); }

  @Test
  public void testNewMethodName() { parsing.get().doTest(); }

  @Test
  public void testRefElementsWithKeywords() { parsing.get().doTest(); }

  @Test
  public void test_finish_argument_list_on_keyword_occurrence() { parsing.get().doTest("finishArgumentListOnKeywordOccurrence.test"); }

  @Test
  public void testConditionalExpressionWithLineFeed() { parsing.get().doTest(); }

  @Test
  public void testSpecial$mapHang() { parsing.get().doTest(); }

  @Test
  public void testIndexpropertyWithUnfinishedInvokedExpression() { parsing.get().doTest(); }

  @Test
  public void testIndex$safeIndex() { parsing.get().doTest(); }
  @Test
  public void testIndex$safeIndexEmpty() { parsing.get().doTest(); }
  @Test
  public void testIndex$safeIndexEmptyMap() { parsing.get().doTest(); }
  @Test
  public void testIndex$safeIndexLBrack() { parsing.get().doTest(); }
  @Test
  public void testIndex$safeIndexMap() { parsing.get().doTest(); }
  @Test
  public void testIndex$safeIndexNoRBrack() { parsing.get().doTest(); }
  @Test
  public void testIndex$safeIndexVsTernary() { parsing.get().doTest(); }
  @Test
  public void testIndex$safeIndexVsTernary2() { parsing.get().doTest(); }
  @Test
  public void testIndex$safeIndexVsTernary3() { parsing.get().doTest(); }
  @Test
  public void testIndex$safeIndexVsTernary4() { parsing.get().doTest(); }
  @Test
  public void testIndex$safeIndexVsTernary5() { parsing.get().doTest(); }
  @Test
  public void testIndex$safeIndexNewLineAfterQ() { parsing.get().doTest(); }
  @Test
  public void testIndex$safeIndexNewLineBeforeQ() { parsing.get().doTest(); }

  @Test
  public void testNl$binary() { parsing.get().doTest(); }
  @Test
  public void testNl$cast() { parsing.get().doTest(); }
  @Test
  public void testNl$index() { parsing.get().doTest(); }
  @Test
  public void testNl$postfixDec() { parsing.get().doTest(); }
  @Test
  public void testNl$postfixInc() { parsing.get().doTest(); }
  @Test
  public void testNl$unary() { parsing.get().doTest(); }

  @Test
  public void testLambda$parenthesizedAdd() { parsing.get().doTest(); }
  @Test
  public void testLambda$standalone1() { parsing.get().doTest(); }
  @Test
  public void testLambda$standalone2() { parsing.get().doTest(); }
  @Test
  public void testLambda$standalone3() { parsing.get().doTest(); }
  @Test
  public void testLambda$standalone4() { parsing.get().doTest(); }
  @Test
  public void testLambda$standalone5() { parsing.get().doTest(); }
  @Test
  public void testLambda$standalone6() { parsing.get().doTest(); }
  @Test
  public void testLambda$standalone7() { parsing.get().doTest(); }
  @Test
  public void testLambda$standalone8() { parsing.get().doTest(); }
  @Test
  public void testLambda$standalone9() { parsing.get().doTest(); }
  @Test
  public void testLambda$standalone10() { parsing.get().doTest(); }
  @Test
  public void testLambda$standalone11() { parsing.get().doTest(); }
  @Test
  public void testLambda$standalone12() { parsing.get().doTest(); }
  @Test
  public void testLambda$closureLike() { parsing.get().doTest(); }
  @Test
  public void testLambda$nestedLambda() { parsing.get().doTest(); }
  @Test
  public void testLambda$nestedLambda2() { parsing.get().doTest(); }
  @Test
  public void testLambda$nestedLambda3() { parsing.get().doTest(); }
  @Test
  public void testLambda$nestedLambda4() { parsing.get().doTest(); }
  @Test
  public void testLambda$nestedLambda5() { parsing.get().doTest(); }
  @Test
  public void testLambda$nestedLambda6() { parsing.get().doTest(); }
  @Test
  public void testLambda$assign1() { parsing.get().doTest(); }
  @Test
  public void testLambda$assign2() { parsing.get().doTest(); }
  @Test
  public void testLambda$assign3() { parsing.get().doTest(); }
  @Test
  public void testLambda$assign4() { parsing.get().doTest(); }
  @Test
  public void testLambda$assign5() { parsing.get().doTest(); }
  @Test
  public void testLambda$assign6() { parsing.get().doTest(); }
  @Test
  public void testLambda$assign7() { parsing.get().doTest(); }
  @Test
  public void testLambda$assign8() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall1() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall2() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall3() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall4() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall5() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall6() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall7() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall8() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall9() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall10() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall11() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall12() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall13() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall14() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall15() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall16() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall17() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall18() { parsing.get().doTest(); }
  @Test
  public void testLambda$methodCall19() { parsing.get().doTest(); }
  @Test
  public void testLambda$command1() { parsing.get().doTest(); }
  @Test
  public void testLambda$command2() { parsing.get().doTest(); }
  @Test
  public void testLambda$command3() { parsing.get().doTest(); }
  @Test
  public void testLambda$command4() { parsing.get().doTest(); }
  @Test
  public void testLambda$command5() { parsing.get().doTest(); }
  @Test
  public void testLambda$command6() { parsing.get().doTest(); }
  @Test
  public void testLambda$command7() { parsing.get().doTest(); }
  @Test
  public void testLambda$command8() { parsing.get().doTest(); }
  @Test
  public void testLambda$command9() { parsing.get().doTest(); }
  @Test
  public void testLambda$command10() { parsing.get().doTest(); }
  @Test
  public void testLambda$command11() { parsing.get().doTest(); }
  @Test
  public void testLambda$command12() { parsing.get().doTest(); }
  @Test
  public void testLambda$commandInLambda() { parsing.get().doTest(); }
  @Test
  public void testLambda$implicitReturn1() { parsing.get().doTest(); }
  @Test
  public void testLambda$implicitReturn2() { parsing.get().doTest(); }
  @Test
  public void testLambda$implicitReturn3() { parsing.get().doTest(); }
  @Test
  public void testLambda$implicitReturn4() { parsing.get().doTest(); }
  @Test
  public void testLambda$return1() { parsing.get().doTest(); }
  @Test
  public void testLambda$return2() { parsing.get().doTest(); }
  @Test
  public void testLambda$return3() { parsing.get().doTest(); }
  @Test
  public void testLambda$return4() { parsing.get().doTest(); }

  @Test
  public void testTypeAnnotations$field1() { parsing.get().doTest(); }
  @Test
  public void testTypeAnnotations$field2() { parsing.get().doTest(); }
  @Test
  public void testTypeAnnotations$methodSignature() { parsing.get().doTest(); }
  @Test
  public void testTypeAnnotations$methodSignature2() { parsing.get().doTest(); }
  @Test
  public void testTypeAnnotations$methodSignature3() { parsing.get().doTest(); }
  @Test
  public void testTypeAnnotations$classDecl1() { parsing.get().doTest(); }
  @Test
  public void testTypeAnnotations$classDecl2() { parsing.get().doTest(); }
  @Test
  public void testTypeAnnotations$tryCatch() { parsing.get().doTest(); }
  @Test
  public void testTypeAnnotations$cast() { parsing.get().doTest(); }
  @Test
  public void testTypeAnnotations$newExpression1() { parsing.get().doTest(); }
  @Test
  public void testTypeAnnotations$newExpression2() { parsing.get().doTest(); }
  @Test
  public void testTypeAnnotations$newExpression3() { parsing.get().doTest(); }
}
