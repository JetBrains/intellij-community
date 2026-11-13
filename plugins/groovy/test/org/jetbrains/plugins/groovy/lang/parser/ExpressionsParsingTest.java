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

  private void doTest() {
    parsing.get().doTest();
  }

  @Test public void testArguments$carg1() { doTest(); }
  @Test public void testArguments$carg2() { doTest(); }
  @Test public void testArguments$carg3() { doTest(); }
  @Test public void testArguments$cargs1() { doTest(); }
  @Test public void testArguments$cargs2() { doTest(); }
  @Test public void testArguments$cargs3() { doTest(); }
  @Test public void testArguments$cargs4() { doTest(); }
  @Test public void testArguments$cargs5() { doTest(); }
  @Test public void testArguments$cargs6() { doTest(); }

  @Test public void testArithmetic$add1() { doTest(); }
  @Test public void testArithmetic$add2() { doTest(); }
  @Test public void testArithmetic$addbug1() { doTest(); }
  @Test public void testArithmetic$arif1() { doTest(); }
  @Test public void testArithmetic$mul1() { doTest(); }
  @Test public void testArithmetic$mul2() { doTest(); }
  @Test public void testArithmetic$mul3() { doTest(); }
  @Test public void testArithmetic$mul4() { doTest(); }
  @Test public void testArithmetic$post1() { doTest(); }
  @Test public void testArithmetic$sh1() { doTest(); }
  @Test public void testArithmetic$shift5() { doTest(); }
  @Test public void testArithmetic$shift6() { doTest(); }
  @Test public void testArithmetic$newLineInsideParentheses() { doTest(); }
  @Test public void testArithmetic$newLineInsideIfStatement() { doTest(); }
  @Test public void testArithmetic$un1() { doTest(); }

  @Test public void testAss1() { doTest(); }
  @Test public void testAss2() { doTest(); }
  @Test public void testAss3() { doTest(); }

  @Test public void testClosures$appended() { doTest(); }
  @Test public void testClosures$closparam1() { doTest(); }
  @Test public void testClosures$closparam2() { doTest(); }
  @Test public void testClosures$closparam3() { doTest(); }
  @Test public void testClosures$closparam4() { doTest(); }
  @Test public void testClosures$closparam5() { doTest(); }
  @Test public void testClosures$closparam6() { doTest(); }
  @Test public void testClosures$final_error() { doTest(); }
  @Test public void testClosures$param6() { doTest(); }
  @Test public void testClosures$param7() { doTest(); }
  @Test public void testClosures$withDefaultParam1() { doTest(); }
  @Test public void testClosures$withDefaultParam2() { doTest(); }
  public void _testClosures$withDefaultParam3() { doTest(); }
  public void _testClosures$withDefaultParam4() { doTest(); }
  @Test public void testClosures$withDefaultParam5() { doTest(); }

  @Test public void testConditional$con1() { doTest(); }
  @Test public void testConditional$con2() { doTest(); }
  @Test public void testConditional$elvis1() { doTest(); }
  @Test public void testConditional$elvis2() { doTest(); }
  @Test public void testConditional$elvisNlBeforeOperator() { doTest(); }
  @Test public void testConditional$ternaryQuestionOnly() { doTest(); }
  @Test public void testConditional$ternaryWithoutElse() { doTest(); }
  @Test public void testConditional$ternaryWithoutThen() { doTest(); }
  @Test public void testConditional$ternaryWithoutThenElse() { doTest(); }
  @Test public void testConditional$ternaryNLBeforeColon() { doTest(); }
  @Test public void testConditional$ternaryNLBeforeElse() { doTest(); }
  @Test public void testConditional$ternaryNLBeforeQuestion() { doTest(); }
  @Test public void testConditional$ternaryNLBeforeThen() { doTest(); }

  @Test public void testErrors$err_final() { doTest(); }

  @Test public void testGstring$daniel_sun() { doTest(); }
  @Test public void testGstring$grvy1653_2() { doTest(); }
  @Test public void testGstring$grvy1653() { doTest(); }

  @Test public void testGstring$gstr3() { doTest(); }
  @Test public void testGstring$standTrooper() { doTest(); }
  @Test public void testGstring$str1() { doTest(); }
  @Test public void testGstring$str2() { doTest(); }
  @Test public void testGstring$str3() { doTest(); }
  @Test public void testGstring$str4() { doTest(); }
  @Test public void testGstring$str5() { doTest(); }
  @Test public void testGstring$str6() { doTest(); }
  @Test public void testGstring$str7() { doTest(); }
  @Test public void testGstring$str8() { doTest(); }
  @Test public void testGstring$str9() { doTest(); }

  @Test public void testString$singleQuoted() { doTest(); }
  @Test public void testString$tripleSingleQuoted$ok() { doTest(); }
  @Test public void testString$tripleSingleQuoted$err0() { doTest(); }
  @Test public void testString$tripleSingleQuoted$err1() { doTest(); }
  @Test public void testString$tripleSingleQuoted$err2() { doTest(); }
  @Test public void testString$tripleSingleQuoted$err3() { doTest(); }
  @Test public void testString$tripleSingleQuoted$err4() { doTest(); }
  @Test public void testString$tripleSingleQuoted$err5() { doTest(); }
  @Test public void testString$tripleSingleQuoted$unfinished0() { doTest(); }
  @Test public void testString$tripleSingleQuoted$unfinished1() { doTest(); }
  @Test public void testString$tripleSingleQuoted$unfinished2() { doTest(); }
  @Test public void testString$tripleSingleQuoted$unfinished3() { doTest(); }

  @Test public void testGstring$str_error1() { doTest(); }
  @Test public void testGstring$str_error2() { doTest(); }
  @Test public void testGstring$str_error3() { doTest(); }
  @Test public void testGstring$str_error4() { doTest(); }
  @Test public void testGstring$str_error5() { doTest(); }
  @Test public void testGstring$str_error6() { doTest(); }
  @Test public void testGstring$str_error7() { doTest(); }
  @Test public void testGstring$str_error8() { doTest(); }
  @Test public void testGstring$triple$triple1() { doTest(); }
  @Test public void testGstring$triple$triple2() { doTest(); }
  @Test public void testGstring$triple$triple3() { doTest(); }
  @Test public void testGstring$triple$triple4() { doTest(); }
  @Test public void testGstring$triple$quote_and_slash() { doTest(); }
  @Test public void testGstring$ugly_lexer() { doTest(); }
  @Test public void testGstring$this() { doTest(); }
  @Test public void testGstring$newline() { doTest(); }

  @Test public void testMapLiteral() { doTest(); }

  @Test public void testMapLiteralNewline() { doTest(); }

  @Test public void testMapKeys() { doTest(); }

  @Test public void testNamedArgumentKeys() { doTest(); }

  @Test public void testExpressionlabelWithoutExpression() { doTest(); }

  @Test public void testNew$arr_decl() { doTest(); }
  @Test public void testNew$emptyTypeArgs() { doTest(); }
  @Test public void testNew$noArgumentList() { doTest(); }
  @Test public void testNew$emptyArrayInitializer() { doTest(); }
  @Test public void testNew$arrayInitializer() { doTest(); }
  @Test public void testNew$arrayInitializerTrailingComma() { doTest(); }
  @Test public void testNew$nlInsideArrayDeclaration() { doTest(); }
  @Test public void testNew$nlBeforeArrayDeclaration() { doTest(); }
  @Test public void testNew$nlInsideArrayDeclarationWithExpression() { doTest(); }
  @Test public void testNew$nestedArrayInitializer() { doTest(); }
  @Test public void testNew$emptyNestedArrayInitializer() { doTest(); }
  @Test public void testNew$nestedArrayInitializerWithClosure() { doTest(); }
  @Test public void testNew$nestedArrayInitializerWithLambda() { doTest(); }
  @Test public void testNew$nestedArrayInitializerCommaAfterLastElement() { doTest(); }
  @Test public void testNew$nestedArrayInitializerNewLinesInside() { doTest(); }
  @Test public void testNew$noInitializer() { doTest(); }
  @Test public void testNew$noClosingBrace() { doTest(); }
  @Test public void testNew$closureAfterArrayDeclaration() { doTest(); }
  @Test public void testNew$closureAfterArrayInitializer() { doTest(); }
  @Test public void testNew$newLine() { doTest(); }
  @Test public void testNew$newLine2() { doTest(); }
  @Test public void testNew$newLine3() { doTest(); }
  @Test public void testNew$newLine4() { doTest(); }
  @Test public void testNew$newLine5() { doTest(); }
  @Test public void testNew$newLine6() { doTest(); }
  @Test public void testNew$newLine7() { doTest(); }
  @Test public void testNew$newLine8() { doTest(); }
  @Test public void testNew$newLine9() { doTest(); }
  @Test public void testNew$newLine10() { doTest(); }
  @Test public void testNew$newLine11() { doTest(); }
  @Test public void testNew$newLine12() { doTest(); }
  @Test public void testNew$newLine13() { doTest(); }
  @Test public void testNew$newLine14() { doTest(); }
  @Test public void testNew$newLine15() { doTest(); }
  @Test public void testNew$newLine16() { doTest(); }
  @Test public void testNew$newLine17() { doTest(); }
  @Test public void testNew$newLine18() { doTest(); }
  @Test public void testNew$newLine19() { doTest(); }
  @Test public void testNew$newLine20() { doTest(); }
  @Test public void testNew$newLine21() { doTest(); }
  @Test public void testNew$newLine22() { doTest(); }
  @Test public void testNew$newLine23() { doTest(); }
  @Test public void testNew$newLine24() { doTest(); }

  @Test public void testAnonymous$anonymous() { doTest(); }
  @Test public void testAnonymous$anonymous1() { doTest(); }
  @Test public void testAnonymous$anonymous2() { doTest(); }
  @Test public void testAnonymous$anonymous3() { doTest(); }
  @Test public void testAnonymous$anonymous4() { doTest(); }
  @Test public void testAnonymous$anonymous5() { doTest(); }
  @Test public void testAnonymous$anonymous6() { doTest(); }
  @Test public void testAnonymous$anonymous7() { doTest(); }
  @Test public void testAnonymous$anonymous8() { doTest(); }
  @Test public void testAnonymous$anonymous9() { doTest(); }
  @Test public void testAnonymous$anonymous10() { doTest(); }
  @Test public void testAnonymous$anonymous11() { doTest(); }
  @Test public void testAnonymous$anonymous12() { doTest(); }
  @Test public void testAnonymous$anonymous13() { doTest(); }
  @Test public void testAnonymous$anonymous14() { doTest(); }
  @Test public void testAnonymous$anonymous15() { doTest(); }
  @Test public void testAnonymous$anonymous16() { doTest(); }
  @Test public void testAnonymous$anonymous17() { doTest(); }
  @Test public void testAnonymous$newlineBeforeBodyInCall() { doTest(); }
  @Test public void testAnonymous$newLineInsideParentheses() { doTest(); }
  @Test public void testAnonymous$newLineInsideIfStatement() { doTest(); }

  @Test public void testNumbers() { doTest(); }

  @Test public void testParenthed$exprInParenth() { doTest(); }
  @Test public void testParenthed$paren1() { doTest(); }
  @Test public void testParenthed$paren2() { doTest(); }
  @Test public void testParenthed$paren3() { doTest(); }
  @Test public void testParenthed$paren4() { doTest(); }
  @Test public void testParenthed$paren5() { doTest(); }
  @Test public void testParenthed$paren6() { doTest(); }
  @Test public void testParenthed$newLineAfterOpeningParenthesis() { doTest(); }
  @Test public void testParenthed$newLineBeforeClosingParenthesis() { doTest(); }
  @Test public void testParenthed$capitalNamedArgument() { doTest(); }
  @Test public void testParenthed$capitalListArgument() { doTest(); }

  @Test public void testPath$method$ass4() { doTest(); }
  @Test public void testPath$method$clazz1() { doTest(); }
  @Test public void testPath$method$clazz2() { doTest(); }
  @Test public void testPath$method$clos1() { doTest(); }
  @Test public void testPath$method$clos2() { doTest(); }
  @Test public void testPath$method$clos3() { doTest(); }
  @Test public void testPath$method$clos4() { doTest(); }
  @Test public void testPath$method$ind1() { doTest(); }
  @Test public void testPath$method$ind2() { doTest(); }
  @Test public void testPath$method$ind3() { doTest(); }
  @Test public void testPath$method$method1() { doTest(); }
  @Test public void testPath$method$method10() { doTest(); }
  @Test public void testPath$method$method11() { doTest(); }
  @Test public void testPath$method$method12() { doTest(); }
  @Test public void testPath$method$method13() { doTest(); }
  @Test public void testPath$method$method2() { doTest(); }
  @Test public void testPath$method$method3() { doTest(); }
  @Test public void testPath$method$method4() { doTest(); }
  @Test public void testPath$method$method5() { doTest(); }
  @Test public void testPath$method$method6() { doTest(); }
  @Test public void testPath$method$method7() { doTest(); }
  @Test public void testPath$method$method8() { doTest(); }
  @Test public void testPath$method$method9() { doTest(); }
  @Test public void testPath$method$newLineBeforeOperatorInCall() { doTest(); }
  @Test public void testPath$method$method14() { doTest(); }
  @Test public void testPath$method$method15() { doTest(); }
  @Test public void testPath$path1() { doTest(); }
  @Test public void testPath$path13() { doTest(); }
  @Test public void testPath$path14() { doTest(); }
  @Test public void testPath$path15() { doTest(); }
  @Test public void testPath$path2() { doTest(); }
  @Test public void testPath$path4() { doTest(); }
  @Test public void testPath$path5() { doTest(); }
  @Test public void testPath$path6() { doTest(); }
  @Test public void testPath$path7() { doTest(); }
  @Test public void testPath$path8() { doTest(); }
  @Test public void testPath$path9() { doTest(); }
  @Test public void testPath$path16() { doTest(); }
  @Test public void testPath$path17() { doTest(); }
  @Test public void testPath$path18() { doTest(); }
  @Test public void testPath$path19() { doTest(); }
  @Test public void testPath$regexp() { doTest(); }
  @Test public void testPath$typeVsExpr() { doTest(); }
  @Test public void testPath$stringMethodCall1() { doTest(); }
  @Test public void testPath$stringMethodCall2() { doTest(); }
  @Test public void testPath$stringMethodCall3() { doTest(); }

  @Test public void testReferences$ref1() { doTest(); }
  @Test public void testReferences$ref2() { doTest(); }
  @Test public void testReferences$ref3() { doTest(); }
  @Test public void testReferences$ref4() { doTest(); }
  @Test public void testReferences$ref5() { doTest(); }
  @Test public void testReferences$ref6() { doTest(); }
  @Test public void testReferences$ref7() { doTest(); }
  @Test public void testReferences$ref8() { doTest(); }
  @Test public void testReferences$ref9() { doTest(); }
  @Test public void testReferences$ref10() { doTest(); }
  @Test public void testReferences$ref11() { doTest(); }
  @Test public void testReferences$ref12() { doTest(); }
  @Test public void testReferences$keywords() { doTest(); }
  @Test public void testReferences$emptyTypeArgs() { doTest(); }
  @Test public void testReferences$dots() { doTest(); }

  @Test public void testRegex$chen() { doTest(); }
  @Test public void testRegex$GRVY1509err() { doTest(); }
  @Test public void testRegex$GRVY1509norm() { doTest(); }
  @Test public void testRegex$GRVY1509test() { doTest(); }
  @Test public void testRegex$regex1() { doTest(); }
  @Test public void testRegex$regex10() { doTest(); }
  @Test public void testRegex$regex11() { doTest(); }
  @Test public void testRegex$regex12() { doTest(); }
  @Test public void testRegex$regex13() { doTest(); }
  @Test public void testRegex$regex14() { doTest(); }
  @Test public void testRegex$regex15() { doTest(); }
  @Test public void testRegex$regex16() { doTest(); }
  @Test public void testRegex$regex17() { doTest(); }
  @Test public void testRegex$regex18() { doTest(); }
  @Test public void testRegex$regex19() { doTest(); }
  @Test public void testRegex$regex2() { doTest(); }
  @Test public void testRegex$regex20() { doTest(); }
  @Test public void testRegex$regex21() { doTest(); }
  @Test public void testRegex$regex22() { doTest(); }
  @Test public void testRegex$regex23() { doTest(); }
  @Test public void testRegex$regex24() { doTest(); }
  @Test public void testRegex$regex25() { doTest(); }
  @Test public void testRegex$regex3() { doTest(); }
  @Test public void testRegex$regex33() { doTest(); }
  @Test public void testRegex$regex4() { doTest(); }
  @Test public void testRegex$regex5() { doTest(); }
  @Test public void testRegex$regex6() { doTest(); }
  @Test public void testRegex$regex7() { doTest(); }
  @Test public void testRegex$regex8() { doTest(); }
  @Test public void testRegex$regex9() { doTest(); }
  @Test public void testRegex$regex_begin() { doTest(); }
  @Test public void testRegex$regex_begin2() { doTest(); }
  @Test public void testRegex$slashyEq() { doTest(); }
  @Test public void testRegex$multiLineSlashy() { doTest(); }
  @Test public void testRegex$dollarSlashy() { doTest(); }
  @Test public void testRegex$dollarSlashy2() { doTest(); }
  @Test public void testRegex$dollarSlashy3() { doTest(); }
  @Test public void testRegex$dollarSlashy4() { doTest(); }
  @Test public void testRegex$dollarSlashy5() { doTest(); }
  @Test public void testRegex$dollarSlashy6() { doTest(); }
  @Test public void testRegex$dollarSlashy7() { doTest(); }
  @Test public void testRegex$dollarSlashy8() { doTest(); }
  @Test public void testRegex$dollarSlashy9() { doTest(); }
  @Test public void testRegex$dollarSlashy10() { doTest(); }
  @Test public void testRegex$dollarSlashy11() { doTest(); }
  @Test public void testRegex$dollarSlashyCode() { doTest(); }
  @Test public void testRegex$dollarSlashyCodeUnfinished() { doTest(); }
  @Test public void testRegex$dollarSlashyEof() { doTest(); }
  @Test public void testRegex$dollarSlashyRegex() { doTest(); }
  @Test public void testRegex$dollarSlashyRegexFinishedTwice() { doTest(); }
  @Test public void testRegex$dollarSlashyRegexUnfinished() { doTest(); }
  @Test public void testRegex$dollarSlashyUnfinished() { doTest(); }
  @Test public void testRegex$dollarSlashyWindowsPaths() { doTest(); }
  @Test public void testRegex$dollarSlashyXml() { doTest(); }
  @Test public void testRegex$dollarSlashyDouble() { doTest(); }
  @Test public void testRegex$dollarSlashyTriple() { doTest(); }
  @Test public void testRegex$dollarSlashyUltimate() { doTest(); }
  @Test public void testRegex$afterNewLine() { doTest(); }
  @Test public void testRegex$afterDollarSlashyString() { doTest(); }
  @Test public void testRegex$afterDoubleQuotedString() { doTest(); }
  @Test public void testRegex$afterSingleQuotedString() { doTest(); }
  @Test public void testRegex$afterSlashyString() { doTest(); }
  @Test public void testRegex$afterTripleSingleQuotedString() { doTest(); }
  @Test public void testRegex$afterTripleDoubleQuotedString() { doTest(); }

  @Test public void testRelational$eq1() { doTest(); }
  @Test public void testRelational$inst0() { doTest(); }
  @Test public void testRelational$inst1() { doTest(); }
  @Test public void testRelational$inst2() { doTest(); }
  @Test public void testRelational$rel1() { doTest(); }
  @Test public void testRelational$newlineAfterOperator() { doTest(); }
  @Test public void testRelational$noRValue() { doTest(); }
  @Test public void testRelational$exclamationAfterExpression() { doTest(); }
  @Test public void testRelational$inNegated() { doTest(); }
  @Test public void testRelational$inNegatedWithSpace() { doTest(); }
  @Test public void testRelational$inNegatedIdentifier() { doTest(); }
  @Test public void testRelational$instanceOfNegated() { doTest(); }
  @Test public void testRelational$instanceOfNegatedWithSpace() { doTest(); }
  @Test public void testRelational$instanceOfNegatedIdentifier() { doTest(); }

  @Test public void testSpecial$grvy1173() { doTest(); }
  @Test public void testSpecial$list1() { doTest(); }
  @Test public void testSpecial$list2() { doTest(); }
  @Test public void testSpecial$list3() { doTest(); }
  @Test public void testSpecial$map1() { doTest(); }
  @Test public void testSpecial$map2() { doTest(); }
  @Test public void testSpecial$map3() { doTest(); }
  @Test public void testSpecial$map4() { doTest(); }
  @Test public void testSpecial$map5() { doTest(); }
  @Test public void testSpecial$map6() { doTest(); }
  @Test public void testSpecial$map7() { doTest(); }
  @Test public void testSpecial$map8() { doTest(); }
  @Test public void testSpecial$paren13() { doTest(); }

  @Test public void testTypecast$castToObject() { doTest(); }
  @Test public void testTypecast$una1() { doTest(); }
  @Test public void testTypecast$una2() { doTest(); }
  @Test public void testTypecast$una3() { doTest(); }
  @Test public void testTypecast$una4() { doTest(); }
  @Test public void testTypecast$una5() { doTest(); }
  @Test public void testTypecast$una6() { doTest(); }
  @Test public void testTypecast$elvis() { doTest(); }
  @Test public void testTypecast$equality() { doTest(); }
  @Test public void testTypecast$parenthesized() { doTest(); }
  @Test public void testTypecast$noExpression() { doTest(); }
  @Test public void testTypecast$parenthesizedOperand() { doTest(); }
  @Test public void testTypecast$parenthesizedQualifier() { doTest(); }
  @Test public void testTypecast$parenthesizedOperandError() { doTest(); }
  @Test public void testTypecast$nested() { doTest(); }
  @Test public void testTypecast$vsMethodCall() { doTest(); }
  @Test public void testTypecast$conditional() { doTest(); }

  @Test public void testAtHang() { doTest(); }

  @Test public void testDollar() { doTest(); }

  @Test public void testNoArrowClosure() { doTest(); }

  @Test public void testNoArrowClosure2() { doTest(); }

  @Test public void testPropertyAccessError() { doTest(); }

  @Test public void testThis$qualifiedThis() { doTest(); }

  @Test public void testSuper$qualifiedSuper() { doTest(); }

  @Test public void testThis$this() { doTest(); }

  @Test public void testSuper$super() { doTest(); }

  @Test public void testBinary$implicationSimple() { doTest(); }
  @Test public void testBinary$implicationWithNewLineAfter() { doTest(); }
  @Test public void testBinary$implicationWithNewLineBefore() { doTest(); }
  @Test public void testBinary$implicationRightAssociativity() { doTest(); }
  @Test public void testBinary$implicationLowPriority() { doTest(); }
  @Test public void testBinary$identity() { doTest(); }
  @Test public void testBinary$elvisAssign() { doTest(); }
  @Test public void testBinary$elvisAssignNewLine() { doTest(); }
  @Test public void testBinary$elvisAssignWithoutRValue() { doTest(); }
  @Test public void testBinary$assignmentError() { doTest(); }

  @Test public void testCommandExpr$closureArg() { doTest(); }
  @Test public void testCommandExpr$simple() { doTest(); }
  @Test public void testCommandExpr$callArg1() { doTest(); }
  @Test public void testCommandExpr$callArg2() { doTest(); }
  @Test public void testCommandExpr$threeArgs1() { doTest(); }
  @Test public void testCommandExpr$threeArgs2() { doTest(); }
  @Test public void testCommandExpr$threeArgs3() { doTest(); }
  @Test public void testCommandExpr$fourArgs() { doTest(); }
  @Test public void testCommandExpr$fiveArgs() { doTest(); }
  @Test public void testCommandExpr$multiArgs() { doTest(); }
  @Test public void testCommandExpr$RHS() { doTest(); }
  @Test public void testCommandExpr$oddArgCount() { doTest(); }
  @Test public void testCommandExpr$indexAccess1() { doTest(); }
  @Test public void testCommandExpr$indexAccess2() { doTest(); }
  @Test public void testCommandExpr$indexAccess3() { doTest(); }
  @Test public void testCommandExpr$indexAccess4() { doTest(); }
  @Test public void testCommandExpr$closureArg2() { doTest(); }
  @Test public void testCommandExpr$closureArg3() { doTest(); }
  @Test public void testCommandExpr$closureArg4() { doTest(); }
  @Test public void testCommandExpr$closureArg5() { doTest(); }
  @Test public void testCommandExpr$not() { doTest(); }
  @Test public void testCommandExpr$methodCall() { doTest(); }
  @Test public void testCommandExpr$indexProperty() { doTest(); }
  @Test public void testCommandExpr$instanceof() { doTest(); }
  @Test public void testCommandExpr$instanceof2() { doTest(); }
  @Test public void testCommandExpr$in() { doTest(); }
  @Test public void testCommandExpr$as() { doTest(); }
  @Test public void testCommandExpr$arrayAccess() { doTest(); }
  @Test public void testCommandExpr$keywords() { doTest(); }
  @Test public void testCommandExpr$literalInvoked() { doTest(); }
  @Test public void testCommandExpr$literalInvokedWithUnfinishedLiteral() { doTest(); }
  @Test public void testCommandExpr$slashyInvoked() { doTest(); }
  @Test public void testCommandExpr$safeIndex() { doTest(); }
  @Test public void testCommandExpr$safeIndexEmpty() { doTest(); }
  @Test public void testCommandExpr$safeIndexEmptyMap() { doTest(); }
  @Test public void testCommandExpr$safeIndexLBrack() { doTest(); }
  @Test public void testCommandExpr$safeIndexMap() { doTest(); }

  @Test public void testDiamond() { doTest(); }

  @Test public void testDiamondErrors() { doTest(); }

  @Test public void testSpacesInStringAfterSlash() { doTest(); }

  @Test public void testDiamondInPathRefElement() { doTest(); }

  @Test public void testNewMethodName() { doTest(); }

  @Test public void testRefElementsWithKeywords() { doTest(); }

  @Test public void testFinishArgumentListOnKeywordOccurrence() { doTest(); }

  @Test public void testConditionalExpressionWithLineFeed() { doTest(); }

  @Test public void testSpecial$mapHang() { doTest(); }

  @Test public void testIndexPropertyWithUnfinishedInvokedExpression() { doTest(); }

  @Test public void testIndex$safeIndex() { doTest(); }
  @Test public void testIndex$safeIndexEmpty() { doTest(); }
  @Test public void testIndex$safeIndexEmptyMap() { doTest(); }
  @Test public void testIndex$safeIndexLBrack() { doTest(); }
  @Test public void testIndex$safeIndexMap() { doTest(); }
  @Test public void testIndex$safeIndexNoRBrack() { doTest(); }
  @Test public void testIndex$safeIndexVsTernary() { doTest(); }
  @Test public void testIndex$safeIndexVsTernary2() { doTest(); }
  @Test public void testIndex$safeIndexVsTernary3() { doTest(); }
  @Test public void testIndex$safeIndexVsTernary4() { doTest(); }
  @Test public void testIndex$safeIndexVsTernary5() { doTest(); }
  @Test public void testIndex$safeIndexNewLineAfterQ() { doTest(); }
  @Test public void testIndex$safeIndexNewLineBeforeQ() { doTest(); }

  @Test public void testNl$binary() { doTest(); }
  @Test public void testNl$cast() { doTest(); }
  @Test public void testNl$index() { doTest(); }
  @Test public void testNl$postfixDec() { doTest(); }
  @Test public void testNl$postfixInc() { doTest(); }
  @Test public void testNl$unary() { doTest(); }

  @Test public void testLambda$parenthesizedAdd() { doTest(); }
  @Test public void testLambda$standalone1() { doTest(); }
  @Test public void testLambda$standalone2() { doTest(); }
  @Test public void testLambda$standalone3() { doTest(); }
  @Test public void testLambda$standalone4() { doTest(); }
  @Test public void testLambda$standalone5() { doTest(); }
  @Test public void testLambda$standalone6() { doTest(); }
  @Test public void testLambda$standalone7() { doTest(); }
  @Test public void testLambda$standalone8() { doTest(); }
  @Test public void testLambda$standalone9() { doTest(); }
  @Test public void testLambda$standalone10() { doTest(); }
  @Test public void testLambda$standalone11() { doTest(); }
  @Test public void testLambda$standalone12() { doTest(); }
  @Test public void testLambda$closureLike() { doTest(); }
  @Test public void testLambda$nestedLambda() { doTest(); }
  @Test public void testLambda$nestedLambda2() { doTest(); }
  @Test public void testLambda$nestedLambda3() { doTest(); }
  @Test public void testLambda$nestedLambda4() { doTest(); }
  @Test public void testLambda$nestedLambda5() { doTest(); }
  @Test public void testLambda$nestedLambda6() { doTest(); }
  @Test public void testLambda$assign1() { doTest(); }
  @Test public void testLambda$assign2() { doTest(); }
  @Test public void testLambda$assign3() { doTest(); }
  @Test public void testLambda$assign4() { doTest(); }
  @Test public void testLambda$assign5() { doTest(); }
  @Test public void testLambda$assign6() { doTest(); }
  @Test public void testLambda$assign7() { doTest(); }
  @Test public void testLambda$assign8() { doTest(); }
  @Test public void testLambda$methodCall1() { doTest(); }
  @Test public void testLambda$methodCall2() { doTest(); }
  @Test public void testLambda$methodCall3() { doTest(); }
  @Test public void testLambda$methodCall4() { doTest(); }
  @Test public void testLambda$methodCall5() { doTest(); }
  @Test public void testLambda$methodCall6() { doTest(); }
  @Test public void testLambda$methodCall7() { doTest(); }
  @Test public void testLambda$methodCall8() { doTest(); }
  @Test public void testLambda$methodCall9() { doTest(); }
  @Test public void testLambda$methodCall10() { doTest(); }
  @Test public void testLambda$methodCall11() { doTest(); }
  @Test public void testLambda$methodCall12() { doTest(); }
  @Test public void testLambda$methodCall13() { doTest(); }
  @Test public void testLambda$methodCall14() { doTest(); }
  @Test public void testLambda$methodCall15() { doTest(); }
  @Test public void testLambda$methodCall16() { doTest(); }
  @Test public void testLambda$methodCall17() { doTest(); }
  @Test public void testLambda$methodCall18() { doTest(); }
  @Test public void testLambda$methodCall19() { doTest(); }
  @Test public void testLambda$command1() { doTest(); }
  @Test public void testLambda$command2() { doTest(); }
  @Test public void testLambda$command3() { doTest(); }
  @Test public void testLambda$command4() { doTest(); }
  @Test public void testLambda$command5() { doTest(); }
  @Test public void testLambda$command6() { doTest(); }
  @Test public void testLambda$command7() { doTest(); }
  @Test public void testLambda$command8() { doTest(); }
  @Test public void testLambda$command9() { doTest(); }
  @Test public void testLambda$command10() { doTest(); }
  @Test public void testLambda$command11() { doTest(); }
  @Test public void testLambda$command12() { doTest(); }
  @Test public void testLambda$commandInLambda() { doTest(); }
  @Test public void testLambda$implicitReturn1() { doTest(); }
  @Test public void testLambda$implicitReturn2() { doTest(); }
  @Test public void testLambda$implicitReturn3() { doTest(); }
  @Test public void testLambda$implicitReturn4() { doTest(); }
  @Test public void testLambda$return1() { doTest(); }
  @Test public void testLambda$return2() { doTest(); }
  @Test public void testLambda$return3() { doTest(); }
  @Test public void testLambda$return4() { doTest(); }

  @Test public void testTypeAnnotations$field1() { doTest(); }
  @Test public void testTypeAnnotations$field2() { doTest(); }
  @Test public void testTypeAnnotations$methodSignature() { doTest(); }
  @Test public void testTypeAnnotations$methodSignature2() { doTest(); }
  @Test public void testTypeAnnotations$methodSignature3() { doTest(); }
  @Test public void testTypeAnnotations$classDecl1() { doTest(); }
  @Test public void testTypeAnnotations$classDecl2() { doTest(); }
  @Test public void testTypeAnnotations$tryCatch() { doTest(); }
  @Test public void testTypeAnnotations$cast() { doTest(); }
  @Test public void testTypeAnnotations$newExpression1() { doTest(); }
  @Test public void testTypeAnnotations$newExpression2() { doTest(); }
  @Test public void testTypeAnnotations$newExpression3() { doTest(); }
}
