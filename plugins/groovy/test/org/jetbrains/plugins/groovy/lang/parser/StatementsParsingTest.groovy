// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser

/**
 * @author peter
 */
class StatementsParsingTest extends GroovyParsingTestCase {
  final String basePath = super.basePath + "statements"

  void testBlocks$clos1() throws Throwable { doTest() }

  void testBlocks$clos2() throws Throwable { doTest() }

  void testBlocks$clos3() throws Throwable { doTest() }

  void testBlocks$clos4() throws Throwable { doTest() }

  void testBlocks$clos5() throws Throwable { doTest() }

  void testBlocks$clos6() throws Throwable { doTest() }

  void testBlocks$form() throws Throwable { doTest() }

  void testBlocks$labeledClosure() throws Throwable { doTest() }

  void testBranch$assert1() throws Throwable { doTest() }

  void testBranch$assert2() throws Throwable { doTest() }

  void testBranch$assert3() throws Throwable { doTest() }

  void testBranch$assert4() throws Throwable { doTest() }

  void testBranch$break1() throws Throwable { doTest() }

  void testBranch$break2() throws Throwable { doTest() }

  void testBranch$ret1() throws Throwable { doTest() }

  void testBranch$ret2() throws Throwable { doTest() }

  void testBranch$ret3() throws Throwable { doTest() }

  void testBranch$thr1() throws Throwable { doTest() }

  void testBranch$thr2() throws Throwable { doTest() }

  void testClass_initializers$class_init1() throws Throwable { doTest() }

  void testClass_initializers$stat_block() throws Throwable { doTest() }

  void testDeclaration$decl1() throws Throwable { doTest() }

  void testDeclaration$decl10() throws Throwable { doTest() }

  void testDeclaration$decl11() throws Throwable { doTest() }

  void testDeclaration$decl2() throws Throwable { doTest() }

  void testDeclaration$decl3() throws Throwable { doTest() }

  void testDeclaration$decl4() throws Throwable { doTest() }

  void testDeclaration$decl5() throws Throwable { doTest() }

  void testDeclaration$decl6() throws Throwable { doTest() }

  void testDeclaration$decl7() throws Throwable { doTest() }

  void testDeclaration$decl8() throws Throwable { doTest() }

  void testDeclaration$decl9() throws Throwable { doTest() }

  void testDeclaration$decl12() throws Throwable { doTest() }

  void testDeclaration$decl13() throws Throwable { doTest() }

  void testDeclaration$exprStatement() throws Throwable { doTest() }

  void testDeclaration$conditional1() throws Throwable { doTest() }

  void testDeclaration$conditional2() throws Throwable { doTest() }

  void testDeclaration$conditional3() throws Throwable { doTest() }

  void testDeclaration$dollar() throws Throwable { doTest() }

  void testDeclaration$groovyMain() throws Throwable { doTest() }

  void testDeclaration$GRVY1451() throws Throwable { doTest("declaration/GRVY-1451.test") }

  void testDeclaration$methodCallAsMultiDecl() throws Throwable { doTest() }

  void testDeclaration$meth_err13() throws Throwable { doTest() }

  void testDeclaration$meth_err14() throws Throwable { doTest() }

  void testDeclaration$nl_trows() throws Throwable { doTest() }

  void testFor$for1() throws Throwable { doTest() }

  void testFor$for11() throws Throwable { doTest() }

  void testFor$for12() throws Throwable { doTest() }

  void testFor$for13() throws Throwable { doTest() }

  void testFor$for2() throws Throwable { doTest() }

  void testFor$for3() throws Throwable { doTest() }

  void testFor$for4() throws Throwable { doTest() }

  void testFor$for5() throws Throwable { doTest() }

  void testFor$for6() throws Throwable { doTest() }

  void testFor$for7() throws Throwable { doTest() }

  void testFor$for8() throws Throwable { doTest() }

  void testFor$for9() throws Throwable { doTest() }

  void testFor$idenfierAfterLParen() { doTest() }

  void testFor$keywordOnly() { doTest() }

  void testFor$lParen() { doTest() }

  void testIfstmt$if1() throws Throwable { doTest() }

  void testIfstmt$if2() throws Throwable { doTest() }

  void testIfstmt$if3() throws Throwable { doTest() }

  void testIfstmt$if4() throws Throwable { doTest() }

  void testIfstmt$if5() throws Throwable { doTest() }

  void testIfstmt$if6() throws Throwable { doTest() }

  void testIfstmt$applicaitonCondition() { doTest() }

  void testIfstmt$keywordOnly() { doTest() }

  void testIfstmt$keywordLParen() { doTest() }

  void testIfstmt$noCondition() { doTest() }

  void testIfstmt$noRParen() { doTest() }

  void testIfstmt$noThen() { doTest() }

  void testIfstmt$noThenNL() { doTest() }

  void testIfstmt$noElse() { doTest() }

  void testIfstmt$noThenWithElse() { doTest() }

  void testIfstmt$nls() { doTest() }

  void testIfstmt$separatorsBeforeElse() { doTest() }

  void testImports$imp0() throws Throwable { doTest() }

  void testImports$imp1() throws Throwable { doTest() }

  void testImports$imp2() throws Throwable { doTest() }

  void testImports$imp3() throws Throwable { doTest() }

  void testImports$imp4() throws Throwable { doTest() }

  void testImports$imp5() throws Throwable { doTest() }

  void testImports$imp6() throws Throwable { doTest() }

  void testImports$imp7() throws Throwable { doTest() }

  void testImports$imp8() throws Throwable { doTest() }

  void testKing_regex$king1() throws Throwable { doTest() }

  void testKing_regex$king2() throws Throwable { doTest() }

  void testKing_regex$king3() throws Throwable { doTest() }

  void testKing_regex$king4() throws Throwable { doTest() }

  void testLabeled$label1() throws Throwable { doTest() }

  void testLabeled$label2() throws Throwable { doTest() }

  void testLabeled$label3() throws Throwable { doTest() }

  void testLoop$while1() throws Throwable { doTest() }

  void testLoop$while2() throws Throwable { doTest() }

  void testLoop$while3() throws Throwable { doTest() }

  void testLoop$while4() throws Throwable { doTest() }

  void testLoop$while6() throws Throwable { doTest() }

  void testLoop$while7() throws Throwable { doTest() }

  void testLoop$dowhile0() { doTest() }

  void testLoop$dowhile1() { doTest() }

  void testLoop$dowhile2() { doTest() }

  void testLoop$dowhile3() { doTest() }

  void testLoop$dowhile4() { doTest() }

  void testLoop$dowhile5() { doTest() }

  void testMethods$method1() throws Throwable { doTest() }

  void testMethods$method2() throws Throwable { doTest() }

  void testMethods$method3() throws Throwable { doTest() }

  void testMethods$method4() throws Throwable { doTest() }

  void testMethods$method5() throws Throwable { doTest() }

  void testMethods$method6() throws Throwable { doTest() }

  void testMethods$vararg() throws Throwable { doTest() }

  void testMultiple_assign$grvy2086() throws Throwable { doTest("multiple_assign/grvy-2086.test") }

  void testMultiple_assign$mult_assign() throws Throwable { doTest() }

  void testMultiple_assign$mult_def() throws Throwable { doTest() }

  void testMultiple_assign$without_assign() throws Throwable { doTest() }

  void testSwitch$laforge1() throws Throwable { doTest() }

  void testSwitch$swit1() throws Throwable { doTest() }

  void testSwitch$swit2() throws Throwable { doTest() }

  void testSwitch$swit3() throws Throwable { doTest() }

  void testSwitch$swit4() throws Throwable { doTest() }

  void testSwitch$swit5() throws Throwable { doTest() }

  void testSwitch$swit6() throws Throwable { doTest() }

  void testSwitch$swit7() throws Throwable { doTest() }

  void testSwitch$swit8() throws Throwable { doTest() }

  void testSwitch$identifierWithin() { doTest() }

  void testSyn$syn1() throws Throwable { doTest() }

  void testTop_methods$method1() throws Throwable { doTest() }

  void testTop_methods$method2() throws Throwable { doTest() }

  void testTop_methods$method3() throws Throwable { doTest() }

  void testTop_methods$method4() throws Throwable { doTest() }

  void testTry_catch$try1() throws Throwable { doTest() }

  void testTry_catch$try2() throws Throwable { doTest() }

  void testTry_catch$try3() throws Throwable { doTest() }

  void testTry_catch$try4() throws Throwable { doTest() }

  void testTry_catch$try5() throws Throwable { doTest() }

  void testTry_catch$try6() throws Throwable { doTest() }

  void testTry_catch$try7() throws Throwable { doTest() }

  void testTry_catch$tryResources() { doTest() }

  void testTry_catch$tryResourcesEmpty() { doTest() }

  void testTry_catch$tryResourcesNoRparen() { doTest() }

  void testTry_catch$tryResourcesNoRparenAfterResource() { doTest() }

  void testTry_catch$tryResourcesNLAfterResource() { doTest() }

  void testTry_catch$tryResourcesSeparators() { doTest() }

  void testTry_catch$tryResourcesTyped() { doTest() }

  void testTuples$doubleParens() throws Throwable { doTest() }

  void testTuples$methCallNotTuple() throws Throwable { doTest() }

  void testTuples$nestedTupleUnsupp() throws Throwable { doTest() }

  void testTuples$tupleInClass() throws Throwable { doTest() }

  void testTuples$tupleInScript() throws Throwable { doTest() }

  void testTuples$tupleNotInit() throws Throwable { doTest() }

  void testTuples$tupleNotInit2() { doTest() }

  void testTuples$tupleOneVarInLine() throws Throwable { doTest() }

  void testTuples$tupleTypeErr() throws Throwable { doTest() }

  void testTuples$tupleWithoutDef() throws Throwable { doTest() }

  void testTuples$tupleWithoutVariables() { doTest() }

  void testTuples$differentModifiers() { doTest() }

  void testTypedef$classes$abstr() throws Throwable { doTest() }

  void testTypedef$classes$class1() throws Throwable { doTest() }

  void testTypedef$classes$class10() throws Throwable { doTest() }

  void testTypedef$classes$class11() throws Throwable { doTest() }

  void testTypedef$classes$class2() throws Throwable { doTest() }

  void testTypedef$classes$class3() throws Throwable { doTest() }

  void testTypedef$classes$class4() throws Throwable { doTest() }

  void testTypedef$classes$class5() throws Throwable { doTest() }

  void testTypedef$classes$class6() throws Throwable { doTest() }

  void testTypedef$classes$class7() throws Throwable { doTest() }

  void testTypedef$classes$class8() throws Throwable { doTest() }

  void testTypedef$classes$class9() throws Throwable { doTest() }

  void testTypedef$classes$errors$classerr1() throws Throwable { doTest() }

  void testTypedef$classes$errors$classerr2() throws Throwable { doTest() }

  void testTypedef$classes$errors$classerr3() throws Throwable { doTest() }

  void testTypedef$classes$errors$class_error() throws Throwable { doTest() }

  void testTypedef$constructors$construct12() throws Throwable { doTest() }

  void testTypedef$constructors$constructor1() throws Throwable { doTest() }

  void testTypedef$constructors$constructor10() throws Throwable { doTest() }

  void testTypedef$constructors$constructor11() throws Throwable { doTest() }

  void testTypedef$constructors$constructor13() throws Throwable { doTest() }

  void testTypedef$constructors$constructor14() throws Throwable { doTest() }

  void testTypedef$constructors$constructor15() throws Throwable { doTest() }

  void testTypedef$constructors$constructor2() throws Throwable { doTest() }

  void testTypedef$constructors$constructor3() throws Throwable { doTest() }

  void testTypedef$constructors$constructor4() throws Throwable { doTest() }

  void testTypedef$constructors$constructor5() throws Throwable { doTest() }

  void testTypedef$constructors$constructor6() throws Throwable { doTest() }

  void testTypedef$constructors$constructor7() throws Throwable { doTest() }

  void testTypedef$constructors$constructor8() throws Throwable { doTest() }

  void testTypedef$constructors$constructor9() throws Throwable { doTest() }

  void testTypedef$enums$enum1() throws Throwable { doTest() }

  void testTypedef$enums$enum10() throws Throwable { doTest() }

  void testTypedef$enums$enum11() throws Throwable { doTest() }

  void testTypedef$enums$enum12() throws Throwable { doTest() }

  void testTypedef$enums$enum13() throws Throwable { doTest() }

  void testTypedef$enums$enum2() throws Throwable { doTest() }

  void testTypedef$enums$enum3() throws Throwable { doTest() }

  void testTypedef$enums$enum4() throws Throwable { doTest() }

  void testTypedef$enums$enum5() throws Throwable { doTest() }

  void testTypedef$enums$enum6() throws Throwable { doTest() }

  void testTypedef$enums$enum7() throws Throwable { doTest() }

  void testTypedef$enums$enum8() throws Throwable { doTest() }

  void testTypedef$enums$enum9() throws Throwable { doTest() }

  void testTypedef$interfaces$errors$interfaceerr1() throws Throwable { doTest() }

  void testTypedef$interfaces$errors$interfaceerr2() throws Throwable { doTest() }

  void testTypedef$interfaces$errors$interfaceerr3() throws Throwable { doTest() }

  void testTypedef$interfaces$interface1() throws Throwable { doTest() }

  void testTypedef$interfaces$interface2() throws Throwable { doTest() }

  void testTypedef$interfaces$interface3() throws Throwable { doTest() }

  void testTypedef$interfaces$interface4() throws Throwable { doTest() }

  void testTypedef$interfaces$interface5() throws Throwable { doTest() }

  void testTypedef$interfaces$member3() throws Throwable { doTest() }

  void testTypedef$interfaces$members$member1() throws Throwable { doTest() }

  void testTypedef$interfaces$members$member2() throws Throwable { doTest() }

  void testTypedef$interfaces$members$member3() throws Throwable { doTest() }

  void testTypedef$interfaces$members$member4() throws Throwable { doTest() }

  void testTypedef$interfaces$members$member5() throws Throwable { doTest() }

  void testTypedef$interfaces$members$member6() throws Throwable { doTest() }

  void testTypedef$interfaces$members$memeber7() throws Throwable { doTest() }

  void testTypedef$traits$trait1() { doTest() }

  void testTypedef$methods$method2() throws Throwable { doTest() }

  void testTypedef$methods$method3() throws Throwable { doTest() }

  void testTypedef$methods$method4() throws Throwable { doTest() }

  void testUse$use1() throws Throwable { doTest() }

  void testVardef$vardef1() throws Throwable { doTest() }

  void testVardef$vardef2() throws Throwable { doTest() }

  void testVardef$vardef3() throws Throwable { doTest() }

  void testVardef$vardef4() throws Throwable { doTest() }

  void testVardef$vardeferr() throws Throwable { doTest() }

  void testVardef$vardeferrsingle4() throws Throwable { doTest() }

  void testVardef$newlineAfterModifiers() { doTest() }

  void testWith$with1() throws Throwable { doTest() }

  void testWith$with2() throws Throwable { doTest() }

  void testAfterAs() throws Throwable { doTest() }

  void testUnnamedField() throws Throwable { doTest() }

  void testIfRecovery() throws Throwable { doTest() }

  void testSemicolonsOnDifferentLines() throws Throwable { doTest() }

  void testRecoverySameLine() { doTest() }

  void testRecoveryNewLine() { doTest() }

  void testRecoveryMissingSeparator() { doTest() }
}