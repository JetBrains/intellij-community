/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.parser;

/**
 * @author peter
 */
public class StatementsParsingTest extends GroovyParsingTestCase {
  final String basePath = super.basePath + "statements"

  public void testBlocks$clos1() throws Throwable { doTest(); }
  public void testBlocks$clos2() throws Throwable { doTest(); }
  public void testBlocks$clos3() throws Throwable { doTest(); }
  public void testBlocks$clos4() throws Throwable { doTest(); }
  public void testBlocks$clos5() throws Throwable { doTest(); }
  public void testBlocks$clos6() throws Throwable { doTest(); }
  public void testBlocks$form() throws Throwable { doTest(); }
  public void testBlocks$labeledClosure() throws Throwable { doTest(); }
  public void testBranch$assert1() throws Throwable { doTest(); }
  public void testBranch$assert2() throws Throwable { doTest(); }
  public void testBranch$assert3() throws Throwable { doTest(); }
  public void testBranch$assert4() throws Throwable { doTest(); }
  public void testBranch$break1() throws Throwable { doTest(); }
  public void testBranch$break2() throws Throwable { doTest(); }
  public void testBranch$ret1() throws Throwable { doTest(); }
  public void testBranch$ret2() throws Throwable { doTest(); }
  public void testBranch$ret3() throws Throwable { doTest(); }
  public void testBranch$thr1() throws Throwable { doTest(); }
  public void testBranch$thr2() throws Throwable { doTest(); }
  public void testClass_initializers$class_init1() throws Throwable { doTest(); }
  public void testClass_initializers$stat_block() throws Throwable { doTest(); }
  public void testDeclaration$decl1() throws Throwable { doTest(); }
  public void testDeclaration$decl10() throws Throwable { doTest(); }
  public void testDeclaration$decl11() throws Throwable { doTest(); }
  public void testDeclaration$decl2() throws Throwable { doTest(); }
  public void testDeclaration$decl3() throws Throwable { doTest(); }
  public void testDeclaration$decl4() throws Throwable { doTest(); }
  public void testDeclaration$decl5() throws Throwable { doTest(); }
  public void testDeclaration$decl6() throws Throwable { doTest(); }
  public void testDeclaration$decl7() throws Throwable { doTest(); }
  public void testDeclaration$decl8() throws Throwable { doTest(); }
  public void testDeclaration$decl9() throws Throwable { doTest(); }
  public void testDeclaration$decl12() throws Throwable { doTest(); }
  public void testDeclaration$decl13() throws Throwable { doTest(); }
  public void testDeclaration$exprStatement() throws Throwable { doTest(); }
  public void testDeclaration$conditional1() throws Throwable { doTest(); }
  public void testDeclaration$conditional2() throws Throwable { doTest(); }
  public void testDeclaration$conditional3() throws Throwable { doTest(); }

  public void testDeclaration$dollar() throws Throwable {doTest();}
  public void testDeclaration$groovyMain() throws Throwable { doTest(); }
  public void testDeclaration$GRVY1451() throws Throwable { doTest("declaration/GRVY-1451.test"); }
  public void testDeclaration$methodCallAsMultiDecl() throws Throwable { doTest(); }
  public void testDeclaration$meth_err13() throws Throwable { doTest(); }
  public void testDeclaration$meth_err14() throws Throwable { doTest(); }
  public void testDeclaration$nl_trows() throws Throwable { doTest(); }
  
  public void testFor$for1() throws Throwable { doTest(); }
  public void testFor$for10() throws Throwable { doTest(); }
  public void testFor$for11() throws Throwable { doTest(); }
  public void testFor$for12() throws Throwable { doTest(); }
  public void testFor$for13() throws Throwable { doTest(); }
  public void testFor$for2() throws Throwable { doTest(); }
  public void testFor$for3() throws Throwable { doTest(); }
  public void testFor$for4() throws Throwable { doTest(); }
  public void testFor$for5() throws Throwable { doTest(); }
  public void testFor$for6() throws Throwable { doTest(); }
  public void testFor$for7() throws Throwable { doTest(); }
  public void testFor$for8() throws Throwable { doTest(); }
  public void testFor$for9() throws Throwable { doTest(); }
  public void testIfstmt$if1() throws Throwable { doTest(); }
  public void testIfstmt$if2() throws Throwable { doTest(); }
  public void testIfstmt$if3() throws Throwable { doTest(); }
  public void testIfstmt$if4() throws Throwable { doTest(); }
  public void testIfstmt$if5() throws Throwable { doTest(); }
  public void testIfstmt$if6() throws Throwable { doTest(); }
  public void testImports$imp0() throws Throwable { doTest(); }
  public void testImports$imp1() throws Throwable { doTest(); }
  public void testImports$imp2() throws Throwable { doTest(); }
  public void testImports$imp3() throws Throwable { doTest(); }
  public void testImports$imp4() throws Throwable { doTest(); }
  public void testImports$imp5() throws Throwable { doTest(); }
  public void testImports$imp6() throws Throwable { doTest(); }
  public void testImports$imp7() throws Throwable { doTest(); }
  public void testImports$imp8() throws Throwable { doTest(); }
  public void testKing_regex$king1() throws Throwable { doTest(); }
  public void testKing_regex$king2() throws Throwable { doTest(); }
  public void testKing_regex$king3() throws Throwable { doTest(); }
  public void testKing_regex$king4() throws Throwable { doTest(); }
  public void testLabeled$label1() throws Throwable { doTest(); }
  public void testLabeled$label2() throws Throwable { doTest(); }
  public void testLabeled$label3() throws Throwable { doTest(); }
  public void testLoop$while1() throws Throwable { doTest(); }
  public void testLoop$while2() throws Throwable { doTest(); }
  public void testLoop$while3() throws Throwable { doTest(); }
  public void testLoop$while4() throws Throwable { doTest(); }
  public void testLoop$while6() throws Throwable { doTest(); }
  public void testMethods$method1() throws Throwable { doTest(); }
  public void testMethods$method2() throws Throwable { doTest(); }
  public void testMethods$method3() throws Throwable { doTest(); }
  public void testMethods$method4() throws Throwable { doTest(); }
  public void testMethods$method5() throws Throwable { doTest(); }
  public void testMethods$vararg() throws Throwable { doTest(); }
  public void testMultiple_assign$grvy2086() throws Throwable { doTest("multiple_assign/grvy-2086.test"); }
  public void testMultiple_assign$mult_assign() throws Throwable { doTest(); }
  public void testMultiple_assign$mult_def() throws Throwable { doTest(); }
  public void testMultiple_assign$without_assign() throws Throwable { doTest(); }
  public void testSwitch$laforge1() throws Throwable { doTest(); }
  public void testSwitch$swit1() throws Throwable { doTest(); }
  public void testSwitch$swit2() throws Throwable { doTest(); }
  public void testSwitch$swit3() throws Throwable { doTest(); }
  public void testSwitch$swit4() throws Throwable { doTest(); }
  public void testSwitch$swit5() throws Throwable { doTest(); }
  public void testSwitch$swit6() throws Throwable { doTest(); }
  public void testSwitch$swit7() throws Throwable { doTest(); }
  public void testSwitch$swit8() throws Throwable { doTest(); }
  public void testSyn$syn1() throws Throwable { doTest(); }
  public void testTop_methods$method1() throws Throwable { doTest(); }
  public void testTop_methods$method2() throws Throwable { doTest(); }
  public void testTop_methods$method3() throws Throwable { doTest(); }
  public void testTop_methods$method4() throws Throwable { doTest(); }
  public void testTry_catch$try1() throws Throwable { doTest(); }
  public void testTry_catch$try2() throws Throwable { doTest(); }
  public void testTry_catch$try3() throws Throwable { doTest(); }
  public void testTry_catch$try4() throws Throwable { doTest(); }
  public void testTry_catch$try5() throws Throwable { doTest(); }
  public void testTry_catch$try6() throws Throwable { doTest(); }
  public void testTry_catch$try7() throws Throwable { doTest(); }
  public void testTuples$doubleParens() throws Throwable { doTest(); }
  public void testTuples$methCallNotTuple() throws Throwable { doTest(); }
  public void testTuples$nestedTupleUnsupp() throws Throwable { doTest(); }
  public void testTuples$tupleInClass() throws Throwable { doTest(); }
  public void testTuples$tupleInScript() throws Throwable { doTest(); }
  public void testTuples$tupleNotInit() throws Throwable { doTest(); }
  public void testTuples$tupleOneVarInLine() throws Throwable { doTest(); }
  public void testTuples$tupleTypeErr() throws Throwable { doTest(); }
  public void testTuples$tupleWithoutDef() throws Throwable { doTest(); }
  public void testTypedef$classes$abstr() throws Throwable { doTest(); }
  public void testTypedef$classes$class1() throws Throwable { doTest(); }
  public void testTypedef$classes$class10() throws Throwable { doTest(); }
  public void testTypedef$classes$class11() throws Throwable { doTest(); }
  public void testTypedef$classes$class2() throws Throwable { doTest(); }
  public void testTypedef$classes$class3() throws Throwable { doTest(); }
  public void testTypedef$classes$class4() throws Throwable { doTest(); }
  public void testTypedef$classes$class5() throws Throwable { doTest(); }
  public void testTypedef$classes$class6() throws Throwable { doTest(); }
  public void testTypedef$classes$class7() throws Throwable { doTest(); }
  public void testTypedef$classes$class8() throws Throwable { doTest(); }
  public void testTypedef$classes$class9() throws Throwable { doTest(); }
  public void testTypedef$classes$errors$classerr1() throws Throwable { doTest(); }
  public void testTypedef$classes$errors$classerr2() throws Throwable { doTest(); }
  public void testTypedef$classes$errors$classerr3() throws Throwable { doTest(); }
  public void testTypedef$classes$errors$class_error() throws Throwable { doTest(); }
  public void testTypedef$constructors$construct12() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor1() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor10() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor11() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor13() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor14() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor15() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor2() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor3() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor4() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor5() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor6() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor7() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor8() throws Throwable { doTest(); }
  public void testTypedef$constructors$constructor9() throws Throwable { doTest(); }
  public void testTypedef$enums$enum1() throws Throwable { doTest(); }
  public void testTypedef$enums$enum10() throws Throwable { doTest(); }
  public void testTypedef$enums$enum11() throws Throwable { doTest(); }
  public void testTypedef$enums$enum12() throws Throwable { doTest(); }
  public void testTypedef$enums$enum13() throws Throwable { doTest(); }
  public void testTypedef$enums$enum2() throws Throwable { doTest(); }
  public void testTypedef$enums$enum3() throws Throwable { doTest(); }
  public void testTypedef$enums$enum4() throws Throwable { doTest(); }
  public void testTypedef$enums$enum5() throws Throwable { doTest(); }
  public void testTypedef$enums$enum6() throws Throwable { doTest(); }
  public void testTypedef$enums$enum7() throws Throwable { doTest(); }
  public void testTypedef$enums$enum8() throws Throwable { doTest(); }
  public void testTypedef$enums$enum9() throws Throwable { doTest(); }
  public void testTypedef$interfaces$errors$interfaceerr1() throws Throwable { doTest(); }
  public void testTypedef$interfaces$errors$interfaceerr2() throws Throwable { doTest(); }
  public void testTypedef$interfaces$errors$interfaceerr3() throws Throwable { doTest(); }
  public void testTypedef$interfaces$interface1() throws Throwable { doTest(); }
  public void testTypedef$interfaces$interface2() throws Throwable { doTest(); }
  public void testTypedef$interfaces$interface3() throws Throwable { doTest(); }
  public void testTypedef$interfaces$interface4() throws Throwable { doTest(); }
  public void testTypedef$interfaces$interface5() throws Throwable { doTest(); }
  public void testTypedef$interfaces$member3() throws Throwable { doTest(); }
  public void testTypedef$interfaces$members$member1() throws Throwable { doTest(); }
  public void testTypedef$interfaces$members$member2() throws Throwable { doTest(); }
  public void testTypedef$interfaces$members$member3() throws Throwable { doTest(); }
  public void testTypedef$interfaces$members$member4() throws Throwable { doTest(); }
  public void testTypedef$interfaces$members$member5() throws Throwable { doTest(); }
  public void testTypedef$interfaces$members$member6() throws Throwable { doTest(); }
  public void testTypedef$interfaces$members$memeber7() throws Throwable { doTest(); }
  public void testTypedef$traits$trait1() {doTest()}
  public void testTypedef$methods$method2() throws Throwable { doTest(); }
  public void testTypedef$methods$method3() throws Throwable { doTest(); }
  public void testTypedef$methods$method4() throws Throwable { doTest(); }
  public void testUse$use1() throws Throwable { doTest(); }
  public void testVardef$vardef1() throws Throwable { doTest(); }
  public void testVardef$vardef2() throws Throwable { doTest(); }
  public void testVardef$vardef3() throws Throwable { doTest(); }
  public void testVardef$vardeferr() throws Throwable { doTest(); }
  public void testVardef$vardeferrsingle4() throws Throwable { doTest(); }
  public void testWith$with1() throws Throwable { doTest(); }
  public void testWith$with2() throws Throwable { doTest(); }

  public void testAfterAs() throws Throwable { doTest(); }
  public void testUnnamedField() throws Throwable { doTest(); }
  public void testIfRecovery() throws Throwable { doTest(); }
  public void testSemicolonsOnDifferentLines() throws Throwable { doTest(); }

}