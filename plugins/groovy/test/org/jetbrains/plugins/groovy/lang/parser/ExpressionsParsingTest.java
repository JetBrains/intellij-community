// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser
class ExpressionsParsingTest extends GroovyParsingTestCase {
  @Override
  String getBasePath() {
    return super.basePath + "expressions"
  }

  void testarguments$carg1() throws Throwable { doTest() }

  void testarguments$carg2() throws Throwable { doTest() }

  void testarguments$carg3() throws Throwable { doTest() }

  void testarguments$cargs1() throws Throwable { doTest() }

  void testarguments$cargs2() throws Throwable { doTest() }

  void testarguments$cargs3() throws Throwable { doTest() }

  void testarguments$cargs4() throws Throwable { doTest() }

  void testarguments$cargs5() throws Throwable { doTest() }

  void testarguments$cargs6() throws Throwable { doTest() }

  void testarithmetic$add1() throws Throwable { doTest() }

  void testarithmetic$add2() throws Throwable { doTest() }

  void testarithmetic$addbug1() throws Throwable { doTest() }

  void testarithmetic$arif1() throws Throwable { doTest() }

  void testarithmetic$mul1() throws Throwable { doTest() }

  void testarithmetic$mul2() throws Throwable { doTest() }

  void testarithmetic$mul3() throws Throwable { doTest() }

  void testarithmetic$mul4() throws Throwable { doTest() }

  void testarithmetic$post1() throws Throwable { doTest() }

  void testarithmetic$sh1() throws Throwable { doTest() }

  void testarithmetic$shift5() throws Throwable { doTest() }

  void testarithmetic$shift6() throws Throwable { doTest() }

  void testarithmetic$un1() throws Throwable { doTest() }

  void testass1() throws Throwable { doTest() }

  void testass2() throws Throwable { doTest() }

  void testass3() throws Throwable { doTest() }

  void testclosures$appended() throws Throwable { doTest() }

  void testclosures$closparam1() throws Throwable { doTest() }

  void testclosures$closparam2() throws Throwable { doTest() }

  void testclosures$closparam3() throws Throwable { doTest() }

  void testclosures$closparam4() throws Throwable { doTest() }

  void testclosures$closparam5() throws Throwable { doTest() }

  void testclosures$closparam6() throws Throwable { doTest() }

  void testclosures$final_error() throws Throwable { doTest() }

  void testclosures$param6() throws Throwable { doTest() }

  void testclosures$param7() throws Throwable { doTest() }

  void testclosures$withDefaultParam1() throws Throwable { doTest() }

  void testclosures$withDefaultParam2() throws Throwable { doTest() }

  //IDEA-210585
  void _testclosures$withDefaultParam3() throws Throwable { doTest() }

  //IDEA-210585
  void _testclosures$withDefaultParam4() throws Throwable { doTest() }

  void testclosures$withDefaultParam5() throws Throwable { doTest() }

  void testconditional$con1() throws Throwable { doTest() }

  void testconditional$con2() throws Throwable { doTest() }

  void testconditional$elvis1() throws Throwable { doTest() }

  void testconditional$elvis2() throws Throwable { doTest() }

  void testconditional$elvisNlBeforeOperator() { doTest() }

  void testconditional$ternaryQuestionOnly() { doTest() }

  void testconditional$ternaryWithoutElse() { doTest() }

  void testconditional$ternaryWithoutThen() { doTest() }

  void testconditional$ternaryWithoutThenElse() { doTest() }

  void testconditional$ternaryNLBeforeColon() { doTest() }

  void testconditional$ternaryNLBeforeElse() { doTest() }

  void testconditional$ternaryNLBeforeQuestion() { doTest() }

  void testconditional$ternaryNLBeforeThen() { doTest() }

  void testerrors$err_final() throws Throwable { doTest() }

  void testgstring$daniel_sun() throws Throwable { doTest() }

  void testgstring$gravy16532() throws Throwable { doTest("gstring/gravy-1653-2.test") }

  void testgstring$grvy1653() throws Throwable { doTest("gstring/grvy-1653.test") }

  void testgstring$gstr3() throws Throwable { doTest() }

  void testgstring$standTrooper() throws Throwable { doTest() }

  void testgstring$str1() throws Throwable { doTest() }

  void testgstring$str2() throws Throwable { doTest() }

  void testgstring$str3() throws Throwable { doTest() }

  void testgstring$str4() throws Throwable { doTest() }

  void testgstring$str5() throws Throwable { doTest() }

  void testgstring$str6() throws Throwable { doTest() }

  void testgstring$str7() throws Throwable { doTest() }

  void testgstring$str8() throws Throwable { doTest() }

  void testgstring$str9() throws Throwable { doTest() }

  void teststring$singleQuoted() { doTest() }

  void teststring$tripleSingleQuoted$ok() { doTest() }

  void teststring$tripleSingleQuoted$err0() { doTest() }

  void teststring$tripleSingleQuoted$err1() { doTest() }

  void teststring$tripleSingleQuoted$err2() { doTest() }

  void teststring$tripleSingleQuoted$err3() { doTest() }

  void teststring$tripleSingleQuoted$err4() { doTest() }

  void teststring$tripleSingleQuoted$err5() { doTest() }

  void teststring$tripleSingleQuoted$unfinished0() { doTest() }

  void teststring$tripleSingleQuoted$unfinished1() { doTest() }

  void teststring$tripleSingleQuoted$unfinished2() { doTest() }

  void teststring$tripleSingleQuoted$unfinished3() { doTest() }

  void testgstring$str_error1() throws Throwable { doTest() }

  void testgstring$str_error2() throws Throwable { doTest() }

  void testgstring$str_error3() throws Throwable { doTest() }

  void testgstring$str_error4() throws Throwable { doTest() }

  void testgstring$str_error5() throws Throwable { doTest() }

  void testgstring$str_error6() throws Throwable { doTest() }

  void testgstring$str_error7() throws Throwable { doTest() }

  void testgstring$str_error8() throws Throwable { doTest() }

  void testgstring$triple$triple1() throws Throwable { doTest() }

  void testgstring$triple$triple2() throws Throwable { doTest() }

  void testgstring$triple$triple3() throws Throwable { doTest() }

  void testgstring$triple$triple4() throws Throwable { doTest() }

  void testgstring$triple$quote_and_slash() throws Throwable { doTest() }

  void testgstring$ugly_lexer() throws Throwable { doTest() }

  void testgstring$this() { doTest() }

  void testgstring$newline() { doTest() }

  void testmapLiteral() throws Throwable { doTest() }

  void testmapKeys() { doTest() }

  void testnamedArgumentKeys() { doTest() }

  void testexpressionlabelWithoutExpression() { doTest() }

  void testnew$arr_decl() throws Throwable { doTest() }

  void testnew$emptyTypeArgs() { doTest() }

  void testnew$noArgumentList() { doTest() }

  void testnew$emptyArrayInitializer() { doTest() }

  void testnew$arrayInitializer() { doTest() }

  void testnew$arrayInitializerTrailingComma() { doTest() }

  void testnew$nestedArrayInitializer() { doTest() }

  void testnew$noInitializer() { doTest() }

  void testnew$noClosingBrace() { doTest() }

  void testnew$closureAfterArrayDeclaration() { doTest() }

  void testnew$closureAfterArrayInitializer() { doTest() }

  void testnew$newLine() { doTest() }

  void testnew$newLine2() { doTest() }

  void testnew$newLine3() { doTest() }

  void testnew$newLine4() { doTest() }

  void testnew$newLine5() { doTest() }

  void testnew$newLine6() { doTest() }

  void testnew$newLine7() { doTest() }

  void testnew$newLine8() { doTest() }

  void testnew$newLine9() { doTest() }

  void testnew$newLine10() { doTest() }

  void testnew$newLine11() { doTest() }

  void testnew$newLine12() { doTest() }

  void testnew$newLine13() { doTest() }

  void testnew$newLine14() { doTest() }

  void testnew$newLine15() { doTest() }

  void testnew$newLine16() { doTest() }

  void testnew$newLine17() { doTest() }

  void testnew$newLine18() { doTest() }

  void testnew$newLine19() { doTest() }

  void testnew$newLine20() { doTest() }

  void testnew$newLine21() { doTest() }

  void testnew$newLine22() { doTest() }

//  public void testnew$new1() throws Throwable { doTest(); }
  void testanonymous$anonymous() throws Throwable { doTest() }

  void testanonymous$anonymous1() throws Throwable { doTest() }

  void testanonymous$anonymous2() throws Throwable { doTest() }

  void testanonymous$anonymous3() throws Throwable { doTest() }

  void testanonymous$anonymous4() throws Throwable { doTest() }

  void testanonymous$anonymous5() throws Throwable { doTest() }

  void testanonymous$anonymous6() throws Throwable { doTest() }

  void testanonymous$anonymous7() throws Throwable { doTest() }

  void testanonymous$anonymous8() throws Throwable { doTest() }

  void testanonymous$anonymous9() throws Throwable { doTest() }

  void testanonymous$anonymous10() throws Throwable { doTest() }

  void testanonymous$anonymous11() throws Throwable { doTest() }

  void testanonymous$anonymous12() throws Throwable { doTest() }

  void testanonymous$anonymous13() throws Throwable { doTest() }

  void testanonymous$anonymous14() throws Throwable { doTest() }

  void testanonymous$anonymous15() throws Throwable { doTest() }

  void testanonymous$anonymous16() throws Throwable { doTest() }

  void testanonymous$anonymous17() throws Throwable { doTest() }

  void testanonymous$newlineBeforeBodyInCall() { doTest() }

  void testnumbers() throws Throwable { doTest() }

  void testparenthed$exprInParenth() throws Throwable { doTest() }

  void testparenthed$paren1() throws Throwable { doTest() }

  void testparenthed$paren2() throws Throwable { doTest() }

  void testparenthed$paren3() throws Throwable { doTest() }

  void testparenthed$paren4() throws Throwable { doTest() }

  void testparenthed$paren5() throws Throwable { doTest() }

  void testparenthed$paren6() throws Throwable { doTest() }

  void testparenthed$newLineAfterOpeningParenthesis() throws Throwable { doTest() }

  void testparenthed$newLineBeforeClosingParenthesis() throws Throwable { doTest() }

  void testparenthed$capitalNamedArgument() { doTest() }

  void testparenthed$capitalListArgument() { doTest() }

  void testpath$method$ass4() throws Throwable { doTest() }

  void testpath$method$clazz1() throws Throwable { doTest() }

  void testpath$method$clazz2() throws Throwable { doTest() }

  void testpath$method$clos1() throws Throwable { doTest() }

  void testpath$method$clos2() throws Throwable { doTest() }

  void testpath$method$clos3() throws Throwable { doTest() }

  void testpath$method$clos4() throws Throwable { doTest() }

  void testpath$method$ind1() throws Throwable { doTest() }

  void testpath$method$ind2() throws Throwable { doTest() }

  void testpath$method$ind3() throws Throwable { doTest() }

  void testpath$method$method1() throws Throwable { doTest() }

  void testpath$method$method10() throws Throwable { doTest() }

  void testpath$method$method11() throws Throwable { doTest() }

  void testpath$method$method12() throws Throwable { doTest() }

  void testpath$method$method13() throws Throwable { doTest() }

  void testpath$method$method2() throws Throwable { doTest() }

  void testpath$method$method3() throws Throwable { doTest() }

  void testpath$method$method4() throws Throwable { doTest() }

  void testpath$method$method5() throws Throwable { doTest() }

  void testpath$method$method6() throws Throwable { doTest() }

  void testpath$method$method7() throws Throwable { doTest() }

  void testpath$method$method8() throws Throwable { doTest() }

  void testpath$method$method9() throws Throwable { doTest() }

  void testpath$method$newLineBeforeOperatorInCall() { doTest() }

  void testpath$method$method14() { doTest() }

  void testpath$method$method15() { doTest() }

  void testpath$path1() throws Throwable { doTest() }

  void testpath$path13() throws Throwable { doTest() }

  void testpath$path14() throws Throwable { doTest() }

  void testpath$path15() throws Throwable { doTest() }

  void testpath$path2() throws Throwable { doTest() }

  void testpath$path4() throws Throwable { doTest() }

  void testpath$path5() throws Throwable { doTest() }

  void testpath$path6() throws Throwable { doTest() }

  void testpath$path7() throws Throwable { doTest() }

  void testpath$path8() throws Throwable { doTest() }

  void testpath$path9() throws Throwable { doTest() }

  void testpath$path16() throws Throwable { doTest() }

  void testpath$path17() throws Throwable { doTest() }

  void testpath$path18() throws Throwable { doTest() }

  void testpath$path19() throws Throwable { doTest() }

  void testpath$regexp() { doTest() }

  void testpath$typeVsExpr() { doTest() }

  void testreferences$ref1() throws Throwable { doTest() }

  void testreferences$ref2() throws Throwable { doTest() }

  void testreferences$ref3() throws Throwable { doTest() }

  void testreferences$ref4() throws Throwable { doTest() }

  void testreferences$ref5() throws Throwable { doTest() }

  void testreferences$ref6() throws Throwable { doTest() }

  void testreferences$ref7() throws Throwable { doTest() }

  void testreferences$ref8() throws Throwable { doTest() }

  void testreferences$ref9() throws Throwable { doTest() }

  void testreferences$ref10() { doTest() }

  void testreferences$ref11() { doTest() }

  void testreferences$ref12() { doTest() }

  void testreferences$keywords() { doTest() }

  void testreferences$emptyTypeArgs() { doTest() }

  void testreferences$dots() { doTest() }

  void testregex$chen() throws Throwable { doTest() }

  void testregex$GRVY1509err() throws Throwable { doTest("regex/GRVY-1509err.test") }

  void testregex$GRVY1509norm() throws Throwable { doTest("regex/GRVY-1509norm.test") }

  void testregex$GRVY1509test() throws Throwable { doTest("regex/GRVY-1509test.test") }

  void testregex$regex1() throws Throwable { doTest() }

  void testregex$regex10() throws Throwable { doTest() }

  void testregex$regex11() throws Throwable { doTest() }

  void testregex$regex12() throws Throwable { doTest() }

  void testregex$regex13() throws Throwable { doTest() }

  void testregex$regex14() throws Throwable { doTest() }

  void testregex$regex15() throws Throwable { doTest() }

  void testregex$regex16() throws Throwable { doTest() }

  void testregex$regex17() throws Throwable { doTest() }

  void testregex$regex18() throws Throwable { doTest() }

  void testregex$regex19() throws Throwable { doTest() }

  void testregex$regex2() throws Throwable { doTest() }

  void testregex$regex20() throws Throwable { doTest() }

  void testregex$regex21() throws Throwable { doTest() }

  void testregex$regex22() throws Throwable { doTest() }

  void testregex$regex23() throws Throwable { doTest() }

  void testregex$regex24() throws Throwable { doTest() }

  void testregex$regex25() throws Throwable { doTest() }

  void testregex$regex3() throws Throwable { doTest() }

  void testregex$regex33() throws Throwable { doTest() }

  void testregex$regex4() throws Throwable { doTest() }

  void testregex$regex5() throws Throwable { doTest() }

  void testregex$regex6() throws Throwable { doTest() }

  void testregex$regex7() throws Throwable { doTest() }

  void testregex$regex8() throws Throwable { doTest() }

  void testregex$regex9() throws Throwable { doTest() }

  void testregex$regex_begin() throws Throwable { doTest() }

  void testregex$regex_begin2() throws Throwable { doTest() }

  void testregex$slashyEq() { doTest() }

  void testregex$multiLineSlashy() throws Throwable { doTest() }

  void testregex$dollarSlashy() throws Throwable { doTest() }

  void testregex$dollarSlashy2() throws Throwable { doTest() }

  void testregex$dollarSlashy3() throws Throwable { doTest() }

  void testregex$dollarSlashy4() throws Throwable { doTest() }

  void testregex$dollarSlashy5() throws Throwable { doTest() }

  void testregex$dollarSlashy6() throws Throwable { doTest() }

  void testregex$dollarSlashy7() throws Throwable { doTest() }

  void testregex$dollarSlashy8() throws Throwable { doTest() }

  void testregex$dollarSlashy9() throws Throwable { doTest() }

  void testregex$dollarSlashy10() { doTest() }

  void testregex$dollarSlashy11() { doTest() }

  void testregex$dollarSlashyCode() throws Throwable { doTest() }

  void testregex$dollarSlashyCodeUnfinished() throws Throwable { doTest() }

  void testregex$dollarSlashyEof() throws Throwable { doTest() }

  void testregex$dollarSlashyRegex() throws Throwable { doTest() }

  void testregex$dollarSlashyRegexFinishedTwice() throws Throwable { doTest() }

  void testregex$dollarSlashyRegexUnfinished() throws Throwable { doTest() }

  void testregex$dollarSlashyUnfinished() throws Throwable { doTest() }

  void testregex$dollarSlashyWindowsPaths() throws Throwable { doTest() }

  void testregex$dollarSlashyXml() throws Throwable { doTest() }

  void testregex$dollarSlashyDouble() throws Throwable { doTest() }

  void testregex$dollarSlashyTriple() throws Throwable { doTest() }

  void testregex$dollarSlashyUltimate() { doTest() }

  void testregex$afterNewLine() { doTest() }

  void testregex$afterDollarSlashyString() { doTest() }

  void testregex$afterDoubleQuotedString() { doTest() }

  void testregex$afterSingleQuotedString() { doTest() }

  void testregex$afterSlashyString() { doTest() }

  void testregex$afterTripleSingleQuotedString() { doTest() }

  void testregex$afterTripleDoubleQuotedString() { doTest() }

  void testrelational$eq1() throws Throwable { doTest() }

  void testrelational$inst0() throws Throwable { doTest() }

  void testrelational$inst1() throws Throwable { doTest() }

  void testrelational$inst2() throws Throwable { doTest() }

  void testrelational$rel1() throws Throwable { doTest() }

  void testrelational$newlineAfterOperator() { doTest() }

  void testrelational$noRValue() { doTest() }

  void testrelational$exclamationAfterExpression() { doTest() }

  void testrelational$inNegated() { doTest() }

  void testrelational$inNegatedWithSpace() { doTest() }

  void testrelational$inNegatedIdentifier() { doTest() }

  void testrelational$instanceOfNegated() { doTest() }

  void testrelational$instanceOfNegatedWithSpace() { doTest() }

  void testrelational$instanceOfNegatedIdentifier() { doTest() }

  void testspecial$grvy1173() throws Throwable { doTest() }

  void testspecial$list1() throws Throwable { doTest() }

  void testspecial$list2() throws Throwable { doTest() }

  void testspecial$list3() throws Throwable { doTest() }

  void testspecial$map1() throws Throwable { doTest() }

  void testspecial$map2() throws Throwable { doTest() }

  void testspecial$map3() throws Throwable { doTest() }

  void testspecial$map4() throws Throwable { doTest() }

  void testspecial$map5() throws Throwable { doTest() }

  void testspecial$map6() throws Throwable { doTest() }

  void testspecial$map7() throws Throwable { doTest() }

  void testspecial$map8() throws Throwable { doTest() }

  void testspecial$paren13() throws Throwable { doTest() }

  void testtypecast$castToObject() throws Throwable { doTest() }

  void testtypecast$una1() throws Throwable { doTest() }

  void testtypecast$una2() throws Throwable { doTest() }

  void testtypecast$una3() throws Throwable { doTest() }

  void testtypecast$una4() throws Throwable { doTest() }

  void testtypecast$una5() throws Throwable { doTest() }

  void testtypecast$una6() throws Throwable { doTest() }

  void testtypecast$elvis() throws Throwable { doTest() }

  void testtypecast$equality() { doTest() }

  void testtypecast$parenthesized() { doTest() }

  void testtypecast$noExpression() { doTest() }

  void testtypecast$parenthesizedOperand() { doTest() }

  void testtypecast$parenthesizedQualifier() { doTest() }

  void testtypecast$parenthesizedOperandError() { doTest() }

  void testtypecast$nested() { doTest() }

  void testtypecast$vsMethodCall() { doTest() }

  void testtypecast$conditional() throws Throwable { doTest() }

  void testAtHang() throws Throwable { doTest() }

  void testDollar() throws Throwable { doTest() }

  void testNoArrowClosure() throws Throwable { doTest() }

  void testNoArrowClosure2() throws Throwable { doTest() }

  void testPropertyAccessError() throws Throwable { doTest() }

  void testthis$qualifiedThis() throws Throwable { doTest() }

  void testsuper$qualifiedSuper() throws Throwable { doTest() }

  void testthis$this() throws Throwable { doTest() }

  void testsuper$super() throws Throwable { doTest() }

  void testbinary$identity() { doTest() }

  void testbinary$elvisAssign() { doTest() }

  void testbinary$elvisAssignNewLine() { doTest() }

  void testbinary$elvisAssignWithoutRValue() { doTest() }

  void testbinary$assignmentError() { doTest() }

  void testcommandExpr$closureArg() { doTest() }

  void testcommandExpr$simple() { doTest() }

  void testcommandExpr$callArg1() { doTest() }

  void testcommandExpr$callArg2() { doTest() }

  void testcommandExpr$threeArgs1() { doTest() }

  void testcommandExpr$threeArgs2() { doTest() }

  void testcommandExpr$threeArgs3() { doTest() }

  void testcommandExpr$fourArgs() { doTest() }

  void testcommandExpr$fiveArgs() { doTest() }

  void testcommandExpr$multiArgs() { doTest() }

  void testcommandExpr$RHS() { doTest() }

  void testcommandExpr$oddArgCount() { doTest() }

  void testcommandExpr$indexAccess1() { doTest() }

  void testcommandExpr$indexAccess2() { doTest() }

  void testcommandExpr$indexAccess3() { doTest() }

  void testcommandExpr$indexAccess4() { doTest() }

  void testcommandExpr$closureArg2() { doTest() }

  void testcommandExpr$closureArg3() { doTest() }

  void testcommandExpr$closureArg4() { doTest() }

  void testcommandExpr$closureArg5() { doTest() }

  void testcommandExpr$not() { doTest() }

  void testcommandExpr$methodCall() { doTest() }

  void testcommandExpr$indexProperty() { doTest() }

  void testcommandExpr$instanceof() { doTest() }

  void testcommandExpr$instanceof2() { doTest() }

  void testcommandExpr$in() { doTest() }

  void testcommandExpr$as() { doTest() }

  void testcommandExpr$arrayAccess() { doTest() }

  void testcommandExpr$keywords() { doTest() }

  void testcommandExpr$literalInvoked() { doTest() }

  void testcommandExpr$literalInvokedWithUnfinishedLiteral() { doTest() }

  void testcommandExpr$slashyInvoked() { doTest() }

  void testcommandExpr$safeIndex() { doTest() }

  void testcommandExpr$safeIndexEmpty() { doTest() }

  void testcommandExpr$safeIndexEmptyMap() { doTest() }

  void testcommandExpr$safeIndexLBrack() { doTest() }

  void testcommandExpr$safeIndexMap() { doTest() }

  void testDiamond() { doTest() }

  void testDiamondErrors() { doTest() }

  void testpath$stringMethodCall1() { doTest() }

  void testpath$stringMethodCall2() { doTest() }

  void testpath$stringMethodCall3() { doTest() }

  void testSpacesInStringAfterSlash() { doTest() }

  void testDiamondInPathRefElement() { doTest() }

  void testNewMethodName() { doTest() }

  void testRefElementsWithKeywords() { doTest() }

  void "test finish argument list on keyword occurrence"() { doTest("finishArgumentListOnKeywordOccurrence.test") }

  void testConditionalExpressionWithLineFeed() { doTest() }

  void testspecial$mapHang() { doTest() }

  void testindexpropertyWithUnfinishedInvokedExpression() { doTest() }

  void testindex$safeIndex() { doTest() }

  void testindex$safeIndexEmpty() { doTest() }

  void testindex$safeIndexEmptyMap() { doTest() }

  void testindex$safeIndexLBrack() { doTest() }

  void testindex$safeIndexMap() { doTest() }

  void testindex$safeIndexNoRBrack() { doTest() }

  void testindex$safeIndexVsTernary() { doTest() }

  void testindex$safeIndexVsTernary2() { doTest() }

  void testindex$safeIndexVsTernary3() { doTest() }

  void testindex$safeIndexVsTernary4() { doTest() }

  void testindex$safeIndexVsTernary5() { doTest() }

  void testindex$safeIndexNewLineAfterQ() { doTest() }

  void testindex$safeIndexNewLineBeforeQ() { doTest() }

  void testnl$binary() { doTest() }

  void testnl$cast() { doTest() }

  void testnl$index() { doTest() }

  void testnl$postfixDec() { doTest() }

  void testnl$postfixInc() { doTest() }

  void testnl$unary() { doTest() }

  void testlambda$parenthesizedAdd() { doTest() }

  void testlambda$standalone1() { doTest() }

  void testlambda$standalone2() { doTest() }

  void testlambda$standalone3() { doTest() }

  void testlambda$standalone4() { doTest() }

  void testlambda$standalone5() { doTest() }

  void testlambda$standalone6() { doTest() }

  void testlambda$standalone7() { doTest() }

  void testlambda$standalone8() { doTest() }

  void testlambda$standalone9() { doTest() }

  void testlambda$standalone10() { doTest() }

  void testlambda$standalone11() { doTest() }

  void testlambda$standalone12() { doTest() }

  void testlambda$closureLike() { doTest() }

  void testlambda$nestedLambda() { doTest() }

  void testlambda$nestedLambda2() { doTest() }

  void testlambda$nestedLambda3() { doTest() }

  void testlambda$nestedLambda4() { doTest() }

  void testlambda$nestedLambda5() { doTest() }

  void testlambda$nestedLambda6() { doTest() }

  void testlambda$assign1() { doTest() }

  void testlambda$assign2() { doTest() }

  void testlambda$assign3() { doTest() }

  void testlambda$assign4() { doTest() }

  void testlambda$assign5() { doTest() }

  void testlambda$assign6() { doTest() }

  void testlambda$assign7() { doTest() }

  void testlambda$assign8() { doTest() }

  void testlambda$methodCall1() { doTest() }

  void testlambda$methodCall2() { doTest() }

  void testlambda$methodCall3() { doTest() }

  void testlambda$methodCall4() { doTest() }

  void testlambda$methodCall5() { doTest() }

  void testlambda$methodCall6() { doTest() }

  void testlambda$methodCall7() { doTest() }

  void testlambda$methodCall8() { doTest() }

  void testlambda$methodCall9() { doTest() }

  void testlambda$methodCall10() { doTest() }

  void testlambda$methodCall11() { doTest() }

  void testlambda$methodCall12() { doTest() }

  void testlambda$methodCall13() { doTest() }

  void testlambda$methodCall14() { doTest() }

  void testlambda$methodCall15() { doTest() }

  void testlambda$methodCall16() { doTest() }

  void testlambda$methodCall17() { doTest() }

  void testlambda$methodCall18() { doTest() }

  void testlambda$methodCall19() { doTest() }

  void testlambda$command1() { doTest() }

  void testlambda$command2() { doTest() }

  void testlambda$command3() { doTest() }

  void testlambda$command4() { doTest() }

  void testlambda$command5() { doTest() }

  void testlambda$command6() { doTest() }

  void testlambda$command7() { doTest() }

  void testlambda$command8() { doTest() }

  void testlambda$command9() { doTest() }

  void testlambda$command10() { doTest() }

  void testlambda$command11() { doTest() }

  void testlambda$command12() { doTest() }

  void testlambda$commandInLambda() { doTest() }

  void testlambda$implicitReturn1() { doTest() }

  void testlambda$implicitReturn2() { doTest() }

  void testlambda$implicitReturn3() { doTest() }

  void testlambda$implicitReturn4() { doTest() }

  void testlambda$return1() { doTest() }

  void testlambda$return2() { doTest() }

  void testlambda$return3() { doTest() }

  void testlambda$return4() { doTest() }

  void testtypeAnnotations$field1() { doTest() }

  void testtypeAnnotations$field2() { doTest() }

  void testtypeAnnotations$methodSignature() { doTest() }

  void testtypeAnnotations$methodSignature2() { doTest() }

  void testtypeAnnotations$methodSignature3() { doTest() }

  void testtypeAnnotations$classDecl1() { doTest() }

  void testtypeAnnotations$classDecl2() { doTest() }

  void testtypeAnnotations$tryCatch() { doTest() }

  void testtypeAnnotations$cast() { doTest() }

  void testtypeAnnotations$newExpression1() { doTest() }

  void testtypeAnnotations$newExpression2() { doTest() }

  void testtypeAnnotations$newExpression3() { doTest() }
}
