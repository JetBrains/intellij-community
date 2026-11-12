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
public class StatementsParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "statements");

  private void doTest() {
    parsing.get().doTest();
  }

  @Test public void testBlocks$clos1() { doTest(); }
  @Test public void testBlocks$clos2() { doTest(); }
  @Test public void testBlocks$clos3() { doTest(); }
  @Test public void testBlocks$clos4() { doTest(); }
  @Test public void testBlocks$clos5() { doTest(); }
  @Test public void testBlocks$clos6() { doTest(); }

  @Test public void testBlocks$nested1() { doTest(); }
  @Test public void testBlocks$nested2() { doTest(); }
  @Test public void testBlocks$nested3() { doTest(); }
  @Test public void testBlocks$nested4() { doTest(); }
  @Test public void testBlocks$nested5() { doTest(); }
  @Test public void testBlocks$nested6() { doTest(); }
  @Test public void testBlocks$nested7() { doTest(); }
  @Test public void testBlocks$nested8() { doTest(); }
  @Test public void testBlocks$nested9() { doTest(); }
  @Test public void testBlocks$nested10() { doTest(); }
  @Test public void testBlocks$nested11() { doTest(); }
  @Test public void testBlocks$nested12() { doTest(); }
  @Test public void testBlocks$nested13() { doTest(); }
  @Test public void testBlocks$nested14() { doTest(); }
  @Test public void testBlocks$nested15() { doTest(); }
  @Test public void testBlocks$nested16() { doTest(); }
  @Test public void testBlocks$nested17() { doTest(); }
  @Test public void testBlocks$nested18() { doTest(); }
  public void _testBlocks$nested19() { doTest(); }
  @Test public void testBlocks$nested20() { doTest(); }
  public void _testBlocks$nested21() { doTest(); }
  @Test public void testBlocks$nested22() { doTest(); }
  @Test public void testBlocks$nested23() { doTest(); }
  @Test public void testBlocks$nested24() { doTest(); }
  @Test public void testBlocks$closureWithCall() { doTest(); }
  @Test public void testBlocks$form() { doTest(); }
  @Test public void testBlocks$labeledClosure() { doTest(); }

  @Test public void testBranch$assert1() { doTest(); }
  @Test public void testBranch$assert2() { doTest(); }
  @Test public void testBranch$assert3() { doTest(); }
  @Test public void testBranch$assert4() { doTest(); }
  @Test public void testBranch$break1() { doTest(); }
  @Test public void testBranch$break2() { doTest(); }
  @Test public void testBranch$ret1() { doTest(); }
  @Test public void testBranch$ret2() { doTest(); }
  @Test public void testBranch$ret3() { doTest(); }
  @Test public void testBranch$thr1() { doTest(); }
  @Test public void testBranch$thr2() { doTest(); }

  @Test public void testClass_initializers$class_init1() { doTest(); }
  @Test public void testClass_initializers$stat_block() { doTest(); }

  @Test public void testDeclaration$decl1() { doTest(); }
  @Test public void testDeclaration$decl2() { doTest(); }
  @Test public void testDeclaration$decl3() { doTest(); }
  @Test public void testDeclaration$decl4() { doTest(); }
  @Test public void testDeclaration$decl5() { doTest(); }
  @Test public void testDeclaration$decl6() { doTest(); }
  @Test public void testDeclaration$decl7() { doTest(); }
  @Test public void testDeclaration$decl8() { doTest(); }
  @Test public void testDeclaration$decl9() { doTest(); }
  @Test public void testDeclaration$decl10() { doTest(); }
  @Test public void testDeclaration$decl11() { doTest(); }
  @Test public void testDeclaration$decl12() { doTest(); }
  @Test public void testDeclaration$decl13() { doTest(); }
  @Test public void testDeclaration$exprStatement() { doTest(); }
  @Test public void testDeclaration$conditional1() { doTest(); }
  @Test public void testDeclaration$conditional2() { doTest(); }
  @Test public void testDeclaration$conditional3() { doTest(); }
  @Test public void testDeclaration$dollar() { doTest(); }
  @Test public void testDeclaration$groovyMain() { doTest(); }
  @Test public void testDeclaration$GRVY1451() { doTest(); }
  @Test public void testDeclaration$methodCallAsMultiDecl() { doTest(); }
  @Test public void testDeclaration$meth_err13() { doTest(); }
  @Test public void testDeclaration$meth_err14() { doTest(); }
  @Test public void testDeclaration$nl_trows() { doTest(); }
  @Test public void testDeclaration$var1() { doTest(); }
  @Test public void testDeclaration$var2() { doTest(); }
  @Test public void testDeclaration$var3() { doTest(); }
  @Test public void testDeclaration$var4() { doTest(); }
  @Test public void testDeclaration$var5() { doTest(); }
  @Test public void testDeclaration$var6() { doTest(); }
  @Test public void testDeclaration$var7() { doTest(); }
  @Test public void testDeclaration$var8() { doTest(); }
  @Test public void testDeclaration$var9() { doTest(); }
  @Test public void testDeclaration$var10() { doTest(); }
  @Test public void testDeclaration$val1() { doTest(); }
  @Test public void testDeclaration$val2() { doTest(); }
  @Test public void testDeclaration$val3() { doTest(); }
  @Test public void testDeclaration$multilineTuple() { doTest(); }

  @Test public void testAsync$defer1() { doTest(); }
  @Test public void testAsync$yieldReturn1() { doTest(); }
  @Test public void testAsync$yieldReturn2() { doTest(); }
  @Test public void testAsync$await() { doTest(); }
  @Test public void testAsync$defer() { doTest(); }
  
  @Test public void testFor$for1() { doTest(); }
  @Test public void testFor$for2() { doTest(); }
  @Test public void testFor$for3() { doTest(); }
  @Test public void testFor$for4() { doTest(); }
  @Test public void testFor$for5() { doTest(); }
  @Test public void testFor$for6() { doTest(); }
  @Test public void testFor$for7() { doTest(); }
  @Test public void testFor$for8() { doTest(); }
  @Test public void testFor$for9() { doTest(); }
  @Test public void testFor$for11() { doTest(); }
  @Test public void testFor$for12() { doTest(); }
  @Test public void testFor$for13() { doTest(); }
  @Test public void testFor$for14() { doTest(); }
  @Test public void testFor$for15() { doTest(); }
  @Test public void testFor$for16() { doTest(); }
  @Test public void testFor$for17() { doTest(); }
  @Test public void testFor$for18() { doTest(); }
  @Test public void testFor$for19() { doTest(); }
  @Test public void testFor$for20() { doTest(); }
  @Test public void testFor$for21() { doTest(); }
  @Test public void testFor$for22() { doTest(); }
  @Test public void testFor$for23() { doTest(); }
  @Test public void testFor$await() { doTest(); }
  @Test public void testFor$identifierAfterLParen() { doTest(); }
  @Test public void testFor$keywordOnly() { doTest(); }
  @Test public void testFor$lParen() { doTest(); }
  @Test public void testFor$identifierAfterParameter() { doTest(); }
  @Test public void testFor$multiDeclaration() { doTest(); }
  @Test public void testFor$multiDeclaration2() { doTest(); }
  @Test public void testFor$multiDeclaration3() { doTest(); }
  @Test public void testFor$multiDeclaration4() { doTest(); }
  @Test public void testFor$tupleDeclaration() { doTest(); }
  @Test public void testFor$tupleDeclarationCommaAfterVar() { doTest(); }
  @Test public void testFor$tupleDeclarationNoInitializer() { doTest(); }
  @Test public void testFor$tupleDeclarationNoInitializerValue() { doTest(); }
  @Test public void testFor$tupleDeclarationSingleVariable() { doTest(); }
  @Test public void testFor$multilineClause() { doTest(); }

  @Test public void testIfstmt$if1() { doTest(); }
  @Test public void testIfstmt$if2() { doTest(); }
  @Test public void testIfstmt$if3() { doTest(); }
  @Test public void testIfstmt$if4() { doTest(); }
  @Test public void testIfstmt$if5() { doTest(); }
  @Test public void testIfstmt$if6() { doTest(); }
  @Test public void testIfstmt$if7() { doTest(); }
  @Test public void testIfstmt$applicaitonCondition() { doTest(); }
  @Test public void testIfstmt$keywordOnly() { doTest(); }
  @Test public void testIfstmt$keywordLParen() { doTest(); }
  @Test public void testIfstmt$noCondition() { doTest(); }
  @Test public void testIfstmt$noRParen() { doTest(); }
  @Test public void testIfstmt$noThen() { doTest(); }
  @Test public void testIfstmt$noThenNL() { doTest(); }
  @Test public void testIfstmt$noElse() { doTest(); }
  @Test public void testIfstmt$noThenWithElse() { doTest(); }
  @Test public void testIfstmt$nls() { doTest(); }
  @Test public void testIfstmt$separatorsBeforeElse() { doTest(); }

  @Test public void testImports$imp0() { doTest(); }
  @Test public void testImports$imp1() { doTest(); }
  @Test public void testImports$imp2() { doTest(); }
  @Test public void testImports$imp3() { doTest(); }
  @Test public void testImports$imp4() { doTest(); }
  @Test public void testImports$imp5() { doTest(); }
  @Test public void testImports$imp6() { doTest(); }
  @Test public void testImports$imp7() { doTest(); }
  @Test public void testImports$imp8() { doTest(); }
  @Test public void testImports$module0() { doTest(); }
  @Test public void testImports$module1() { doTest(); }
  @Test public void testImports$module2() { doTest(); }
  @Test public void testImports$typeArguments() { doTest(); }

  @Test public void testKing_regex$king1() { doTest(); }
  @Test public void testKing_regex$king2() { doTest(); }
  @Test public void testKing_regex$king3() { doTest(); }
  @Test public void testKing_regex$king4() { doTest(); }

  @Test public void testLabeled$label1() { doTest(); }
  @Test public void testLabeled$label2() { doTest(); }
  @Test public void testLabeled$label3() { doTest(); }

  @Test public void testLoop$while1() { doTest(); }
  @Test public void testLoop$while2() { doTest(); }
  @Test public void testLoop$while3() { doTest(); }
  @Test public void testLoop$while4() { doTest(); }
  @Test public void testLoop$while6() { doTest(); }
  @Test public void testLoop$while7() { doTest(); }
  @Test public void testLoop$whileEmptyBody() { doTest(); }
  @Test public void testLoop$dowhile0() { doTest(); }
  @Test public void testLoop$dowhile1() { doTest(); }
  @Test public void testLoop$dowhile2() { doTest(); }
  @Test public void testLoop$dowhile3() { doTest(); }
  @Test public void testLoop$dowhile4() { doTest(); }
  @Test public void testLoop$dowhile5() { doTest(); }
  @Test public void testLoop$dowhile6() { doTest(); }
  @Test public void testLoop$dowhile7() { doTest(); }

  @Test public void testMethods$method1() { doTest(); }
  @Test public void testMethods$method2() { doTest(); }
  @Test public void testMethods$method3() { doTest(); }
  @Test public void testMethods$method4() { doTest(); }
  @Test public void testMethods$method5() { doTest(); }
  @Test public void testMethods$method6() { doTest(); }
  @Test public void testMethods$vararg() { doTest(); }

  @Test public void testMultiple_assign$grvy2086() { doTest(); }
  @Test public void testMultiple_assign$mult_assign() { doTest(); }
  @Test public void testMultiple_assign$mult_def() { doTest(); }
  @Test public void testMultiple_assign$without_assign() { doTest(); }
  @Test public void testMultiple_assign$lineBreakAfterLeftParen() { doTest(); }
  @Test public void testMultiple_assign$lineBreakBeforeComma() { doTest(); }
  @Test public void testMultiple_assign$lineBreakAfterComma() { doTest(); }
  @Test public void testMultiple_assign$lineBreakBeforeRightParen() { doTest(); }
  @Test public void testMultiple_assign$lineBreakBeforeAssignment() { doTest(); }
  @Test public void testMultiple_assign$lineBreakAfterAssignment() { doTest(); }

  @Test public void testSwitch$laforge1() { doTest(); }
  @Test public void testSwitch$swit1() { doTest(); }
  @Test public void testSwitch$swit2() { doTest(); }
  @Test public void testSwitch$swit3() { doTest(); }
  @Test public void testSwitch$swit4() { doTest(); }
  @Test public void testSwitch$swit5() { doTest(); }
  @Test public void testSwitch$swit6() { doTest(); }
  @Test public void testSwitch$swit7() { doTest(); }
  @Test public void testSwitch$swit8() { doTest(); }
  @Test public void testSwitch$identifierWithin() { doTest(); }

  @Test public void testSyn$syn1() { doTest(); }
  @Test public void testSyn$newLineBeforeBlock() { doTest(); }

  @Test public void testTop_methods$method1() { doTest(); }
  @Test public void testTop_methods$method2() { doTest(); }
  @Test public void testTop_methods$method3() { doTest(); }
  @Test public void testTop_methods$method4() { doTest(); }

  @Test public void testTry_catch$try1() { doTest(); }
  @Test public void testTry_catch$try2() { doTest(); }
  @Test public void testTry_catch$try3() { doTest(); }
  @Test public void testTry_catch$try4() { doTest(); }
  @Test public void testTry_catch$try5() { doTest(); }
  @Test public void testTry_catch$try6() { doTest(); }
  @Test public void testTry_catch$try7() { doTest(); }
  @Test public void testTry_catch$newLineBeforeCatchBlock() { doTest(); }
  @Test public void testTry_catch$newLineBeforeFinallyBlock() { doTest(); }
  @Test public void testTry_catch$tryResources() { doTest(); }
  @Test public void testTry_catch$tryResourcesEmpty() { doTest(); }
  @Test public void testTry_catch$tryResourcesNoRparen() { doTest(); }
  @Test public void testTry_catch$tryResourcesNoRparenAfterResource() { doTest(); }
  @Test public void testTry_catch$tryResourcesNLAfterResource() { doTest(); }
  @Test public void testTry_catch$tryResourcesSeparators() { doTest(); }
  @Test public void testTry_catch$tryResourcesTyped() { doTest(); }
  @Test public void testTry_catch$tryResourcesMultiline() { doTest(); }
  @Test public void testTry_catch$multicatchMultiline() { doTest(); }

  @Test public void testTuples$doubleParens() { doTest(); }
  @Test public void testTuples$methCallNotTuple() { doTest(); }
  @Test public void testTuples$nestedTupleUnsupp() { doTest(); }
  @Test public void testTuples$tupleInClass() { doTest(); }
  @Test public void testTuples$tupleInScript() { doTest(); }
  @Test public void testTuples$tupleNotInit() { doTest(); }
  @Test public void testTuples$tupleNotInit2() { doTest(); }
  @Test public void testTuples$tupleOneVarInLine() { doTest(); }
  @Test public void testTuples$tupleTypeErr() { doTest(); }
  @Test public void testTuples$tupleWithoutDef() { doTest(); }
  @Test public void testTuples$tupleWithoutVariables() { doTest(); }
  @Test public void testTuples$differentModifiers() { doTest(); }

  @Test public void testTypedef$classes$abstr() { doTest(); }
  @Test public void testTypedef$classes$class1() { doTest(); }
  @Test public void testTypedef$classes$class10() { doTest(); }
  @Test public void testTypedef$classes$class11() { doTest(); }
  @Test public void testTypedef$classes$class2() { doTest(); }
  @Test public void testTypedef$classes$class3() { doTest(); }
  @Test public void testTypedef$classes$class4() { doTest(); }
  @Test public void testTypedef$classes$class5() { doTest(); }
  @Test public void testTypedef$classes$class6() { doTest(); }
  @Test public void testTypedef$classes$class7() { doTest(); }
  @Test public void testTypedef$classes$class8() { doTest(); }
  @Test public void testTypedef$classes$class9() { doTest(); }
  @Test public void testTypedef$classes$errors$classerr1() { doTest(); }
  @Test public void testTypedef$classes$errors$classerr2() { doTest(); }
  @Test public void testTypedef$classes$errors$classerr3() { doTest(); }
  @Test public void testTypedef$classes$errors$class_error() { doTest(); }
  @Test public void testTypedef$constructors$construct12() { doTest(); }
  @Test public void testTypedef$constructors$constructor1() { doTest(); }
  @Test public void testTypedef$constructors$constructor10() { doTest(); }
  @Test public void testTypedef$constructors$constructor11() { doTest(); }
  @Test public void testTypedef$constructors$constructor13() { doTest(); }
  @Test public void testTypedef$constructors$constructor14() { doTest(); }
  @Test public void testTypedef$constructors$constructor15() { doTest(); }
  @Test public void testTypedef$constructors$constructor2() { doTest(); }
  @Test public void testTypedef$constructors$constructor3() { doTest(); }
  @Test public void testTypedef$constructors$constructor4() { doTest(); }
  @Test public void testTypedef$constructors$constructor5() { doTest(); }
  @Test public void testTypedef$constructors$constructor6() { doTest(); }
  @Test public void testTypedef$constructors$constructor7() { doTest(); }
  @Test public void testTypedef$constructors$constructor8() { doTest(); }
  @Test public void testTypedef$constructors$constructor9() { doTest(); }
  @Test public void testTypedef$enums$enum1() { doTest(); }
  @Test public void testTypedef$enums$enum10() { doTest(); }
  @Test public void testTypedef$enums$enum11() { doTest(); }
  @Test public void testTypedef$enums$enum12() { doTest(); }
  @Test public void testTypedef$enums$enum13() { doTest(); }
  @Test public void testTypedef$enums$enum2() { doTest(); }
  @Test public void testTypedef$enums$enum3() { doTest(); }
  @Test public void testTypedef$enums$enum4() { doTest(); }
  @Test public void testTypedef$enums$enum5() { doTest(); }
  @Test public void testTypedef$enums$enum6() { doTest(); }
  @Test public void testTypedef$enums$enum7() { doTest(); }
  @Test public void testTypedef$enums$enum8() { doTest(); }
  @Test public void testTypedef$enums$enum9() { doTest(); }
  @Test public void testTypedef$interfaces$errors$interfaceerr1() { doTest(); }
  @Test public void testTypedef$interfaces$errors$interfaceerr2() { doTest(); }
  @Test public void testTypedef$interfaces$errors$interfaceerr3() { doTest(); }
  @Test public void testTypedef$interfaces$interface1() { doTest(); }
  @Test public void testTypedef$interfaces$interface2() { doTest(); }
  @Test public void testTypedef$interfaces$interface3() { doTest(); }
  @Test public void testTypedef$interfaces$interface4() { doTest(); }
  @Test public void testTypedef$interfaces$interface5() { doTest(); }
  @Test public void testTypedef$interfaces$member3() { doTest(); }
  @Test public void testTypedef$interfaces$members$member1() { doTest(); }
  @Test public void testTypedef$interfaces$members$member2() { doTest(); }
  @Test public void testTypedef$interfaces$members$member3() { doTest(); }
  @Test public void testTypedef$interfaces$members$member4() { doTest(); }
  @Test public void testTypedef$interfaces$members$member5() { doTest(); }
  @Test public void testTypedef$interfaces$members$member6() { doTest(); }
  @Test public void testTypedef$interfaces$members$memeber7() { doTest(); }
  @Test public void testTypedef$traits$trait1() { doTest(); }
  @Test public void testTypedef$methods$method2() { doTest(); }
  @Test public void testTypedef$methods$method3() { doTest(); }
  @Test public void testTypedef$methods$method4() { doTest(); }

  @Test public void testUse$use1() { doTest(); }

  @Test public void testVardef$vardef1() { doTest(); }
  @Test public void testVardef$vardef2() { doTest(); }
  @Test public void testVardef$vardef3() { doTest(); }
  @Test public void testVardef$vardef4() { doTest(); }
  @Test public void testVardef$vardeferr() { doTest(); }
  @Test public void testVardef$vardeferrsingle4() { doTest(); }
  @Test public void testVardef$newlineAfterModifiers() { doTest(); }

  @Test public void testWith$with1() { doTest(); }
  @Test public void testWith$with2() { doTest(); }

  @Test public void testAfterAs() { doTest(); }
  @Test public void testUnnamedField() { doTest(); }
  @Test public void testIfRecovery() { doTest(); }
  @Test public void testSemicolonsOnDifferentLines() { doTest(); }
  @Test public void testRecoverySameLine() { doTest(); }
  @Test public void testRecoveryNewLine() { doTest(); }
  @Test public void testRecoveryMissingSeparator() { doTest(); }
}
