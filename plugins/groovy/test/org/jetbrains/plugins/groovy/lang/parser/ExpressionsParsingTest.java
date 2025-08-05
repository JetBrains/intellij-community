// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

public class ExpressionsParsingTest extends GroovyParsingTestCase {
  @Override
  public String getBasePath() {
    return super.getBasePath() + "expressions";
  }

  public void testarguments$carg1() { doTest(); }

  public void testarguments$carg2() { doTest(); }

  public void testarguments$carg3() { doTest(); }

  public void testarguments$cargs1() { doTest(); }

  public void testarguments$cargs2() { doTest(); }

  public void testarguments$cargs3() { doTest(); }

  public void testarguments$cargs4() { doTest(); }

  public void testarguments$cargs5() { doTest(); }

  public void testarguments$cargs6() { doTest(); }

  public void testarithmetic$add1() { doTest(); }

  public void testarithmetic$add2() { doTest(); }

  public void testarithmetic$addbug1() { doTest(); }

  public void testarithmetic$arif1() { doTest(); }

  public void testarithmetic$mul1() { doTest(); }

  public void testarithmetic$mul2() { doTest(); }

  public void testarithmetic$mul3() { doTest(); }

  public void testarithmetic$mul4() { doTest(); }

  public void testarithmetic$post1() { doTest(); }

  public void testarithmetic$sh1() { doTest(); }

  public void testarithmetic$shift5() { doTest(); }

  public void testarithmetic$shift6() { doTest(); }

  public void testarithmetic$un1() { doTest(); }

  public void testass1() { doTest(); }

  public void testass2() { doTest(); }

  public void testass3() { doTest(); }

  public void testclosures$appended() { doTest(); }

  public void testclosures$closparam1() { doTest(); }

  public void testclosures$closparam2() { doTest(); }

  public void testclosures$closparam3() { doTest(); }

  public void testclosures$closparam4() { doTest(); }

  public void testclosures$closparam5() { doTest(); }

  public void testclosures$closparam6() { doTest(); }

  public void testclosures$final_error() { doTest(); }

  public void testclosures$param6() { doTest(); }

  public void testclosures$param7() { doTest(); }

  public void testclosures$withDefaultParam1() { doTest(); }

  public void testclosures$withDefaultParam2() { doTest(); }

  public void _testclosures$withDefaultParam3() { doTest(); }

  public void _testclosures$withDefaultParam4() { doTest(); }

  public void testclosures$withDefaultParam5() { doTest(); }

  public void testconditional$con1() { doTest(); }

  public void testconditional$con2() { doTest(); }

  public void testconditional$elvis1() { doTest(); }

  public void testconditional$elvis2() { doTest(); }

  public void testconditional$elvisNlBeforeOperator() { doTest(); }

  public void testconditional$ternaryQuestionOnly() { doTest(); }

  public void testconditional$ternaryWithoutElse() { doTest(); }

  public void testconditional$ternaryWithoutThen() { doTest(); }

  public void testconditional$ternaryWithoutThenElse() { doTest(); }

  public void testconditional$ternaryNLBeforeColon() { doTest(); }

  public void testconditional$ternaryNLBeforeElse() { doTest(); }

  public void testconditional$ternaryNLBeforeQuestion() { doTest(); }

  public void testconditional$ternaryNLBeforeThen() { doTest(); }

  public void testerrors$err_final() { doTest(); }

  public void testgstring$daniel_sun() { doTest(); }

  public void testgstring$gravy16532() { doTest("gstring/gravy-1653-2.test"); }

  public void testgstring$grvy1653() { doTest("gstring/grvy-1653.test"); }

  public void testgstring$gstr3() { doTest(); }

  public void testgstring$standTrooper() { doTest(); }

  public void testgstring$str1() { doTest(); }

  public void testgstring$str2() { doTest(); }

  public void testgstring$str3() { doTest(); }

  public void testgstring$str4() { doTest(); }

  public void testgstring$str5() { doTest(); }

  public void testgstring$str6() { doTest(); }

  public void testgstring$str7() { doTest(); }

  public void testgstring$str8() { doTest(); }

  public void testgstring$str9() { doTest(); }

  public void teststring$singleQuoted() { doTest(); }

  public void teststring$tripleSingleQuoted$ok() { doTest(); }

  public void teststring$tripleSingleQuoted$err0() { doTest(); }

  public void teststring$tripleSingleQuoted$err1() { doTest(); }

  public void teststring$tripleSingleQuoted$err2() { doTest(); }

  public void teststring$tripleSingleQuoted$err3() { doTest(); }

  public void teststring$tripleSingleQuoted$err4() { doTest(); }

  public void teststring$tripleSingleQuoted$err5() { doTest(); }

  public void teststring$tripleSingleQuoted$unfinished0() { doTest(); }

  public void teststring$tripleSingleQuoted$unfinished1() { doTest(); }

  public void teststring$tripleSingleQuoted$unfinished2() { doTest(); }

  public void teststring$tripleSingleQuoted$unfinished3() { doTest(); }

  public void testgstring$str_error1() { doTest(); }

  public void testgstring$str_error2() { doTest(); }

  public void testgstring$str_error3() { doTest(); }

  public void testgstring$str_error4() { doTest(); }

  public void testgstring$str_error5() { doTest(); }

  public void testgstring$str_error6() { doTest(); }

  public void testgstring$str_error7() { doTest(); }

  public void testgstring$str_error8() { doTest(); }

  public void testgstring$triple$triple1() { doTest(); }

  public void testgstring$triple$triple2() { doTest(); }

  public void testgstring$triple$triple3() { doTest(); }

  public void testgstring$triple$triple4() { doTest(); }

  public void testgstring$triple$quote_and_slash() { doTest(); }

  public void testgstring$ugly_lexer() { doTest(); }

  public void testgstring$this() { doTest(); }

  public void testgstring$newline() { doTest(); }

  public void testmapLiteral() { doTest(); }

  public void testmapKeys() { doTest(); }

  public void testnamedArgumentKeys() { doTest(); }

  public void testexpressionlabelWithoutExpression() { doTest(); }

  public void testnew$arr_decl() { doTest(); }

  public void testnew$emptyTypeArgs() { doTest(); }

  public void testnew$noArgumentList() { doTest(); }

  public void testnew$emptyArrayInitializer() { doTest(); }

  public void testnew$arrayInitializer() { doTest(); }

  public void testnew$arrayInitializerTrailingComma() { doTest(); }

  public void testnew$nestedArrayInitializer() { doTest(); }

  public void testnew$noInitializer() { doTest(); }

  public void testnew$noClosingBrace() { doTest(); }

  public void testnew$closureAfterArrayDeclaration() { doTest(); }

  public void testnew$closureAfterArrayInitializer() { doTest(); }

  public void testnew$newLine() { doTest(); }

  public void testnew$newLine2() { doTest(); }

  public void testnew$newLine3() { doTest(); }

  public void testnew$newLine4() { doTest(); }

  public void testnew$newLine5() { doTest(); }

  public void testnew$newLine6() { doTest(); }

  public void testnew$newLine7() { doTest(); }

  public void testnew$newLine8() { doTest(); }

  public void testnew$newLine9() { doTest(); }

  public void testnew$newLine10() { doTest(); }

  public void testnew$newLine11() { doTest(); }

  public void testnew$newLine12() { doTest(); }

  public void testnew$newLine13() { doTest(); }

  public void testnew$newLine14() { doTest(); }

  public void testnew$newLine15() { doTest(); }

  public void testnew$newLine16() { doTest(); }

  public void testnew$newLine17() { doTest(); }

  public void testnew$newLine18() { doTest(); }

  public void testnew$newLine19() { doTest(); }

  public void testnew$newLine20() { doTest(); }

  public void testnew$newLine21() { doTest(); }

  public void testnew$newLine22() { doTest(); }

  public void testanonymous$anonymous() { doTest(); }

  public void testanonymous$anonymous1() { doTest(); }

  public void testanonymous$anonymous2() { doTest(); }

  public void testanonymous$anonymous3() { doTest(); }

  public void testanonymous$anonymous4() { doTest(); }

  public void testanonymous$anonymous5() { doTest(); }

  public void testanonymous$anonymous6() { doTest(); }

  public void testanonymous$anonymous7() { doTest(); }

  public void testanonymous$anonymous8() { doTest(); }

  public void testanonymous$anonymous9() { doTest(); }

  public void testanonymous$anonymous10() { doTest(); }

  public void testanonymous$anonymous11() { doTest(); }

  public void testanonymous$anonymous12() { doTest(); }

  public void testanonymous$anonymous13() { doTest(); }

  public void testanonymous$anonymous14() { doTest(); }

  public void testanonymous$anonymous15() { doTest(); }

  public void testanonymous$anonymous16() { doTest(); }

  public void testanonymous$anonymous17() { doTest(); }

  public void testanonymous$newlineBeforeBodyInCall() { doTest(); }

  public void testnumbers() { doTest(); }

  public void testparenthed$exprInParenth() { doTest(); }

  public void testparenthed$paren1() { doTest(); }

  public void testparenthed$paren2() { doTest(); }

  public void testparenthed$paren3() { doTest(); }

  public void testparenthed$paren4() { doTest(); }

  public void testparenthed$paren5() { doTest(); }

  public void testparenthed$paren6() { doTest(); }

  public void testparenthed$newLineAfterOpeningParenthesis() { doTest(); }

  public void testparenthed$newLineBeforeClosingParenthesis() { doTest(); }

  public void testparenthed$capitalNamedArgument() { doTest(); }

  public void testparenthed$capitalListArgument() { doTest(); }

  public void testpath$method$ass4() { doTest(); }

  public void testpath$method$clazz1() { doTest(); }

  public void testpath$method$clazz2() { doTest(); }

  public void testpath$method$clos1() { doTest(); }

  public void testpath$method$clos2() { doTest(); }

  public void testpath$method$clos3() { doTest(); }

  public void testpath$method$clos4() { doTest(); }

  public void testpath$method$ind1() { doTest(); }

  public void testpath$method$ind2() { doTest(); }

  public void testpath$method$ind3() { doTest(); }

  public void testpath$method$method1() { doTest(); }

  public void testpath$method$method10() { doTest(); }

  public void testpath$method$method11() { doTest(); }

  public void testpath$method$method12() { doTest(); }

  public void testpath$method$method13() { doTest(); }

  public void testpath$method$method2() { doTest(); }

  public void testpath$method$method3() { doTest(); }

  public void testpath$method$method4() { doTest(); }

  public void testpath$method$method5() { doTest(); }

  public void testpath$method$method6() { doTest(); }

  public void testpath$method$method7() { doTest(); }

  public void testpath$method$method8() { doTest(); }

  public void testpath$method$method9() { doTest(); }

  public void testpath$method$newLineBeforeOperatorInCall() { doTest(); }

  public void testpath$method$method14() { doTest(); }

  public void testpath$method$method15() { doTest(); }

  public void testpath$path1() { doTest(); }

  public void testpath$path13() { doTest(); }

  public void testpath$path14() { doTest(); }

  public void testpath$path15() { doTest(); }

  public void testpath$path2() { doTest(); }

  public void testpath$path4() { doTest(); }

  public void testpath$path5() { doTest(); }

  public void testpath$path6() { doTest(); }

  public void testpath$path7() { doTest(); }

  public void testpath$path8() { doTest(); }

  public void testpath$path9() { doTest(); }

  public void testpath$path16() { doTest(); }

  public void testpath$path17() { doTest(); }

  public void testpath$path18() { doTest(); }

  public void testpath$path19() { doTest(); }

  public void testpath$regexp() { doTest(); }

  public void testpath$typeVsExpr() { doTest(); }

  public void testreferences$ref1() { doTest(); }

  public void testreferences$ref2() { doTest(); }

  public void testreferences$ref3() { doTest(); }

  public void testreferences$ref4() { doTest(); }

  public void testreferences$ref5() { doTest(); }

  public void testreferences$ref6() { doTest(); }

  public void testreferences$ref7() { doTest(); }

  public void testreferences$ref8() { doTest(); }

  public void testreferences$ref9() { doTest(); }

  public void testreferences$ref10() { doTest(); }

  public void testreferences$ref11() { doTest(); }

  public void testreferences$ref12() { doTest(); }

  public void testreferences$keywords() { doTest(); }

  public void testreferences$emptyTypeArgs() { doTest(); }

  public void testreferences$dots() { doTest(); }

  public void testregex$chen() { doTest(); }

  public void testregex$GRVY1509err() { doTest("regex/GRVY-1509err.test"); }

  public void testregex$GRVY1509norm() { doTest("regex/GRVY-1509norm.test"); }

  public void testregex$GRVY1509test() { doTest("regex/GRVY-1509test.test"); }

  public void testregex$regex1() { doTest(); }

  public void testregex$regex10() { doTest(); }

  public void testregex$regex11() { doTest(); }

  public void testregex$regex12() { doTest(); }

  public void testregex$regex13() { doTest(); }

  public void testregex$regex14() { doTest(); }

  public void testregex$regex15() { doTest(); }

  public void testregex$regex16() { doTest(); }

  public void testregex$regex17() { doTest(); }

  public void testregex$regex18() { doTest(); }

  public void testregex$regex19() { doTest(); }

  public void testregex$regex2() { doTest(); }

  public void testregex$regex20() { doTest(); }

  public void testregex$regex21() { doTest(); }

  public void testregex$regex22() { doTest(); }

  public void testregex$regex23() { doTest(); }

  public void testregex$regex24() { doTest(); }

  public void testregex$regex25() { doTest(); }

  public void testregex$regex3() { doTest(); }

  public void testregex$regex33() { doTest(); }

  public void testregex$regex4() { doTest(); }

  public void testregex$regex5() { doTest(); }

  public void testregex$regex6() { doTest(); }

  public void testregex$regex7() { doTest(); }

  public void testregex$regex8() { doTest(); }

  public void testregex$regex9() { doTest(); }

  public void testregex$regex_begin() { doTest(); }

  public void testregex$regex_begin2() { doTest(); }

  public void testregex$slashyEq() { doTest(); }

  public void testregex$multiLineSlashy() { doTest(); }

  public void testregex$dollarSlashy() { doTest(); }

  public void testregex$dollarSlashy2() { doTest(); }

  public void testregex$dollarSlashy3() { doTest(); }

  public void testregex$dollarSlashy4() { doTest(); }

  public void testregex$dollarSlashy5() { doTest(); }

  public void testregex$dollarSlashy6() { doTest(); }

  public void testregex$dollarSlashy7() { doTest(); }

  public void testregex$dollarSlashy8() { doTest(); }

  public void testregex$dollarSlashy9() { doTest(); }

  public void testregex$dollarSlashy10() { doTest(); }

  public void testregex$dollarSlashy11() { doTest(); }

  public void testregex$dollarSlashyCode() { doTest(); }

  public void testregex$dollarSlashyCodeUnfinished() { doTest(); }

  public void testregex$dollarSlashyEof() { doTest(); }

  public void testregex$dollarSlashyRegex() { doTest(); }

  public void testregex$dollarSlashyRegexFinishedTwice() { doTest(); }

  public void testregex$dollarSlashyRegexUnfinished() { doTest(); }

  public void testregex$dollarSlashyUnfinished() { doTest(); }

  public void testregex$dollarSlashyWindowsPaths() { doTest(); }

  public void testregex$dollarSlashyXml() { doTest(); }

  public void testregex$dollarSlashyDouble() { doTest(); }

  public void testregex$dollarSlashyTriple() { doTest(); }

  public void testregex$dollarSlashyUltimate() { doTest(); }

  public void testregex$afterNewLine() { doTest(); }

  public void testregex$afterDollarSlashyString() { doTest(); }

  public void testregex$afterDoubleQuotedString() { doTest(); }

  public void testregex$afterSingleQuotedString() { doTest(); }

  public void testregex$afterSlashyString() { doTest(); }

  public void testregex$afterTripleSingleQuotedString() { doTest(); }

  public void testregex$afterTripleDoubleQuotedString() { doTest(); }

  public void testrelational$eq1() { doTest(); }

  public void testrelational$inst0() { doTest(); }

  public void testrelational$inst1() { doTest(); }

  public void testrelational$inst2() { doTest(); }

  public void testrelational$rel1() { doTest(); }

  public void testrelational$newlineAfterOperator() { doTest(); }

  public void testrelational$noRValue() { doTest(); }

  public void testrelational$exclamationAfterExpression() { doTest(); }

  public void testrelational$inNegated() { doTest(); }

  public void testrelational$inNegatedWithSpace() { doTest(); }

  public void testrelational$inNegatedIdentifier() { doTest(); }

  public void testrelational$instanceOfNegated() { doTest(); }

  public void testrelational$instanceOfNegatedWithSpace() { doTest(); }

  public void testrelational$instanceOfNegatedIdentifier() { doTest(); }

  public void testspecial$grvy1173() { doTest(); }

  public void testspecial$list1() { doTest(); }

  public void testspecial$list2() { doTest(); }

  public void testspecial$list3() { doTest(); }

  public void testspecial$map1() { doTest(); }

  public void testspecial$map2() { doTest(); }

  public void testspecial$map3() { doTest(); }

  public void testspecial$map4() { doTest(); }

  public void testspecial$map5() { doTest(); }

  public void testspecial$map6() { doTest(); }

  public void testspecial$map7() { doTest(); }

  public void testspecial$map8() { doTest(); }

  public void testspecial$paren13() { doTest(); }

  public void testtypecast$castToObject() { doTest(); }

  public void testtypecast$una1() { doTest(); }

  public void testtypecast$una2() { doTest(); }

  public void testtypecast$una3() { doTest(); }

  public void testtypecast$una4() { doTest(); }

  public void testtypecast$una5() { doTest(); }

  public void testtypecast$una6() { doTest(); }

  public void testtypecast$elvis() { doTest(); }

  public void testtypecast$equality() { doTest(); }

  public void testtypecast$parenthesized() { doTest(); }

  public void testtypecast$noExpression() { doTest(); }

  public void testtypecast$parenthesizedOperand() { doTest(); }

  public void testtypecast$parenthesizedQualifier() { doTest(); }

  public void testtypecast$parenthesizedOperandError() { doTest(); }

  public void testtypecast$nested() { doTest(); }

  public void testtypecast$vsMethodCall() { doTest(); }

  public void testtypecast$conditional() { doTest(); }

  public void testAtHang() { doTest(); }

  public void testDollar() { doTest(); }

  public void testNoArrowClosure() { doTest(); }

  public void testNoArrowClosure2() { doTest(); }

  public void testPropertyAccessError() { doTest(); }

  public void testthis$qualifiedThis() { doTest(); }

  public void testsuper$qualifiedSuper() { doTest(); }

  public void testthis$this() { doTest(); }

  public void testsuper$super() { doTest(); }

  public void testbinary$implicationSimple() { doTest(); }

  public void testbinary$implicationWithNewLineAfter() { doTest(); }

  public void testbinary$implicationWithNewLineBefore() { doTest(); }

  public void testbinary$implicationRightAssociativity() { doTest(); }

  public void testbinary$implicationLowPriority() { doTest(); }

  public void testbinary$identity() { doTest(); }

  public void testbinary$elvisAssign() { doTest(); }

  public void testbinary$elvisAssignNewLine() { doTest(); }

  public void testbinary$elvisAssignWithoutRValue() { doTest(); }

  public void testbinary$assignmentError() { doTest(); }

  public void testcommandExpr$closureArg() { doTest(); }

  public void testcommandExpr$simple() { doTest(); }

  public void testcommandExpr$callArg1() { doTest(); }

  public void testcommandExpr$callArg2() { doTest(); }

  public void testcommandExpr$threeArgs1() { doTest(); }

  public void testcommandExpr$threeArgs2() { doTest(); }

  public void testcommandExpr$threeArgs3() { doTest(); }

  public void testcommandExpr$fourArgs() { doTest(); }

  public void testcommandExpr$fiveArgs() { doTest(); }

  public void testcommandExpr$multiArgs() { doTest(); }

  public void testcommandExpr$RHS() { doTest(); }

  public void testcommandExpr$oddArgCount() { doTest(); }

  public void testcommandExpr$indexAccess1() { doTest(); }

  public void testcommandExpr$indexAccess2() { doTest(); }

  public void testcommandExpr$indexAccess3() { doTest(); }

  public void testcommandExpr$indexAccess4() { doTest(); }

  public void testcommandExpr$closureArg2() { doTest(); }

  public void testcommandExpr$closureArg3() { doTest(); }

  public void testcommandExpr$closureArg4() { doTest(); }

  public void testcommandExpr$closureArg5() { doTest(); }

  public void testcommandExpr$not() { doTest(); }

  public void testcommandExpr$methodCall() { doTest(); }

  public void testcommandExpr$indexProperty() { doTest(); }

  public void testcommandExpr$instanceof() { doTest(); }

  public void testcommandExpr$instanceof2() { doTest(); }

  public void testcommandExpr$in() { doTest(); }

  public void testcommandExpr$as() { doTest(); }

  public void testcommandExpr$arrayAccess() { doTest(); }

  public void testcommandExpr$keywords() { doTest(); }

  public void testcommandExpr$literalInvoked() { doTest(); }

  public void testcommandExpr$literalInvokedWithUnfinishedLiteral() { doTest(); }

  public void testcommandExpr$slashyInvoked() { doTest(); }

  public void testcommandExpr$safeIndex() { doTest(); }

  public void testcommandExpr$safeIndexEmpty() { doTest(); }

  public void testcommandExpr$safeIndexEmptyMap() { doTest(); }

  public void testcommandExpr$safeIndexLBrack() { doTest(); }

  public void testcommandExpr$safeIndexMap() { doTest(); }

  public void testDiamond() { doTest(); }

  public void testDiamondErrors() { doTest(); }

  public void testpath$stringMethodCall1() { doTest(); }

  public void testpath$stringMethodCall2() { doTest(); }

  public void testpath$stringMethodCall3() { doTest(); }

  public void testSpacesInStringAfterSlash() { doTest(); }

  public void testDiamondInPathRefElement() { doTest(); }

  public void testNewMethodName() { doTest(); }

  public void testRefElementsWithKeywords() { doTest(); }

  public void test_finish_argument_list_on_keyword_occurrence() { doTest("finishArgumentListOnKeywordOccurrence.test"); }

  public void testConditionalExpressionWithLineFeed() { doTest(); }

  public void testspecial$mapHang() { doTest(); }

  public void testindexpropertyWithUnfinishedInvokedExpression() { doTest(); }

  public void testindex$safeIndex() { doTest(); }

  public void testindex$safeIndexEmpty() { doTest(); }

  public void testindex$safeIndexEmptyMap() { doTest(); }

  public void testindex$safeIndexLBrack() { doTest(); }

  public void testindex$safeIndexMap() { doTest(); }

  public void testindex$safeIndexNoRBrack() { doTest(); }

  public void testindex$safeIndexVsTernary() { doTest(); }

  public void testindex$safeIndexVsTernary2() { doTest(); }

  public void testindex$safeIndexVsTernary3() { doTest(); }

  public void testindex$safeIndexVsTernary4() { doTest(); }

  public void testindex$safeIndexVsTernary5() { doTest(); }

  public void testindex$safeIndexNewLineAfterQ() { doTest(); }

  public void testindex$safeIndexNewLineBeforeQ() { doTest(); }

  public void testnl$binary() { doTest(); }

  public void testnl$cast() { doTest(); }

  public void testnl$index() { doTest(); }

  public void testnl$postfixDec() { doTest(); }

  public void testnl$postfixInc() { doTest(); }

  public void testnl$unary() { doTest(); }

  public void testlambda$parenthesizedAdd() { doTest(); }

  public void testlambda$standalone1() { doTest(); }

  public void testlambda$standalone2() { doTest(); }

  public void testlambda$standalone3() { doTest(); }

  public void testlambda$standalone4() { doTest(); }

  public void testlambda$standalone5() { doTest(); }

  public void testlambda$standalone6() { doTest(); }

  public void testlambda$standalone7() { doTest(); }

  public void testlambda$standalone8() { doTest(); }

  public void testlambda$standalone9() { doTest(); }

  public void testlambda$standalone10() { doTest(); }

  public void testlambda$standalone11() { doTest(); }

  public void testlambda$standalone12() { doTest(); }

  public void testlambda$closureLike() { doTest(); }

  public void testlambda$nestedLambda() { doTest(); }

  public void testlambda$nestedLambda2() { doTest(); }

  public void testlambda$nestedLambda3() { doTest(); }

  public void testlambda$nestedLambda4() { doTest(); }

  public void testlambda$nestedLambda5() { doTest(); }

  public void testlambda$nestedLambda6() { doTest(); }

  public void testlambda$assign1() { doTest(); }

  public void testlambda$assign2() { doTest(); }

  public void testlambda$assign3() { doTest(); }

  public void testlambda$assign4() { doTest(); }

  public void testlambda$assign5() { doTest(); }

  public void testlambda$assign6() { doTest(); }

  public void testlambda$assign7() { doTest(); }

  public void testlambda$assign8() { doTest(); }

  public void testlambda$methodCall1() { doTest(); }

  public void testlambda$methodCall2() { doTest(); }

  public void testlambda$methodCall3() { doTest(); }

  public void testlambda$methodCall4() { doTest(); }

  public void testlambda$methodCall5() { doTest(); }

  public void testlambda$methodCall6() { doTest(); }

  public void testlambda$methodCall7() { doTest(); }

  public void testlambda$methodCall8() { doTest(); }

  public void testlambda$methodCall9() { doTest(); }

  public void testlambda$methodCall10() { doTest(); }

  public void testlambda$methodCall11() { doTest(); }

  public void testlambda$methodCall12() { doTest(); }

  public void testlambda$methodCall13() { doTest(); }

  public void testlambda$methodCall14() { doTest(); }

  public void testlambda$methodCall15() { doTest(); }

  public void testlambda$methodCall16() { doTest(); }

  public void testlambda$methodCall17() { doTest(); }

  public void testlambda$methodCall18() { doTest(); }

  public void testlambda$methodCall19() { doTest(); }

  public void testlambda$command1() { doTest(); }

  public void testlambda$command2() { doTest(); }

  public void testlambda$command3() { doTest(); }

  public void testlambda$command4() { doTest(); }

  public void testlambda$command5() { doTest(); }

  public void testlambda$command6() { doTest(); }

  public void testlambda$command7() { doTest(); }

  public void testlambda$command8() { doTest(); }

  public void testlambda$command9() { doTest(); }

  public void testlambda$command10() { doTest(); }

  public void testlambda$command11() { doTest(); }

  public void testlambda$command12() { doTest(); }

  public void testlambda$commandInLambda() { doTest(); }

  public void testlambda$implicitReturn1() { doTest(); }

  public void testlambda$implicitReturn2() { doTest(); }

  public void testlambda$implicitReturn3() { doTest(); }

  public void testlambda$implicitReturn4() { doTest(); }

  public void testlambda$return1() { doTest(); }

  public void testlambda$return2() { doTest(); }

  public void testlambda$return3() { doTest(); }

  public void testlambda$return4() { doTest(); }

  public void testtypeAnnotations$field1() { doTest(); }

  public void testtypeAnnotations$field2() { doTest(); }

  public void testtypeAnnotations$methodSignature() { doTest(); }

  public void testtypeAnnotations$methodSignature2() { doTest(); }

  public void testtypeAnnotations$methodSignature3() { doTest(); }

  public void testtypeAnnotations$classDecl1() { doTest(); }

  public void testtypeAnnotations$classDecl2() { doTest(); }

  public void testtypeAnnotations$tryCatch() { doTest(); }

  public void testtypeAnnotations$cast() { doTest(); }

  public void testtypeAnnotations$newExpression1() { doTest(); }

  public void testtypeAnnotations$newExpression2() { doTest(); }

  public void testtypeAnnotations$newExpression3() { doTest(); }
}
