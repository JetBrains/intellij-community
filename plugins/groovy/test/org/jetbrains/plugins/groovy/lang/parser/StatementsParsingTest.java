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

  @Test
  public void testBlocks$clos1() { parsing.get().doTest(); }
  @Test
  public void testBlocks$clos2() { parsing.get().doTest(); }
  @Test
  public void testBlocks$clos3() { parsing.get().doTest(); }
  @Test
  public void testBlocks$clos4() { parsing.get().doTest(); }
  @Test
  public void testBlocks$clos5() { parsing.get().doTest(); }
  @Test
  public void testBlocks$clos6() { parsing.get().doTest(); }

  @Test
  public void testBlocks$nested1() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested2() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested3() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested4() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested5() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested6() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested7() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested8() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested9() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested10() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested11() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested12() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested13() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested14() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested15() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested16() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested17() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested18() { parsing.get().doTest(); }
  public void _testBlocks$nested19() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested20() { parsing.get().doTest(); }
  public void _testBlocks$nested21() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested22() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested23() { parsing.get().doTest(); }
  @Test
  public void testBlocks$nested24() { parsing.get().doTest(); }

  @Test
  public void testBlocks$closureWithCall() { parsing.get().doTest(); }

  @Test
  public void testBlocks$form() { parsing.get().doTest(); }

  @Test
  public void testBlocks$labeledClosure() { parsing.get().doTest(); }

  @Test
  public void testBranch$assert1() { parsing.get().doTest(); }
  @Test
  public void testBranch$assert2() { parsing.get().doTest(); }
  @Test
  public void testBranch$assert3() { parsing.get().doTest(); }
  @Test
  public void testBranch$assert4() { parsing.get().doTest(); }

  @Test
  public void testBranch$break1() { parsing.get().doTest(); }
  @Test
  public void testBranch$break2() { parsing.get().doTest(); }

  @Test
  public void testBranch$ret1() { parsing.get().doTest(); }
  @Test
  public void testBranch$ret2() { parsing.get().doTest(); }
  @Test
  public void testBranch$ret3() { parsing.get().doTest(); }

  @Test
  public void testBranch$thr1() { parsing.get().doTest(); }
  @Test
  public void testBranch$thr2() { parsing.get().doTest(); }

  @Test
  public void testClass_initializers$class_init1() { parsing.get().doTest(); }
  @Test
  public void testClass_initializers$stat_block() { parsing.get().doTest(); }

  @Test
  public void testDeclaration$decl1() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$decl2() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$decl3() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$decl4() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$decl5() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$decl6() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$decl7() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$decl8() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$decl9() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$decl10() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$decl11() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$decl12() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$decl13() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$exprStatement() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$conditional1() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$conditional2() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$conditional3() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$dollar() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$groovyMain() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$GRVY1451() { parsing.get().doTest("declaration/GRVY-1451.test"); }
  @Test
  public void testDeclaration$methodCallAsMultiDecl() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$meth_err13() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$meth_err14() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$nl_trows() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$var1() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$var2() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$var3() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$var4() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$var5() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$var6() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$var7() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$var8() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$var9() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$var10() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$val1() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$val2() { parsing.get().doTest(); }
  @Test
  public void testDeclaration$multilineTuple() { parsing.get().doTest(); }

  @Test
  public void testFor$for1() { parsing.get().doTest(); }
  @Test
  public void testFor$for2() { parsing.get().doTest(); }
  @Test
  public void testFor$for3() { parsing.get().doTest(); }
  @Test
  public void testFor$for4() { parsing.get().doTest(); }
  @Test
  public void testFor$for5() { parsing.get().doTest(); }
  @Test
  public void testFor$for6() { parsing.get().doTest(); }
  @Test
  public void testFor$for7() { parsing.get().doTest(); }
  @Test
  public void testFor$for8() { parsing.get().doTest(); }
  @Test
  public void testFor$for9() { parsing.get().doTest(); }
  @Test
  public void testFor$for11() { parsing.get().doTest(); }
  @Test
  public void testFor$for12() { parsing.get().doTest(); }
  @Test
  public void testFor$for13() { parsing.get().doTest(); }
  @Test
  public void testFor$for14() { parsing.get().doTest(); }
  @Test
  public void testFor$for15() { parsing.get().doTest(); }
  @Test
  public void testFor$for16() { parsing.get().doTest(); }
  @Test
  public void testFor$for17() { parsing.get().doTest(); }
  @Test
  public void testFor$for18() { parsing.get().doTest(); }
  @Test
  public void testFor$for19() { parsing.get().doTest(); }
  @Test
  public void testFor$for20() { parsing.get().doTest(); }
  @Test
  public void testFor$for21() { parsing.get().doTest(); }
  @Test
  public void testFor$for22() { parsing.get().doTest(); }
  @Test
  public void testFor$for23() { parsing.get().doTest(); }
  @Test
  public void testFor$idenfierAfterLParen() { parsing.get().doTest(); }
  @Test
  public void testFor$keywordOnly() { parsing.get().doTest(); }
  @Test
  public void testFor$lParen() { parsing.get().doTest(); }
  @Test
  public void testFor$identifierAfterParameter() { parsing.get().doTest(); }
  @Test
  public void testFor$multiDeclaration() { parsing.get().doTest(); }
  @Test
  public void testFor$multiDeclaration2() { parsing.get().doTest(); }
  @Test
  public void testFor$multiDeclaration3() { parsing.get().doTest(); }
  @Test
  public void testFor$multiDeclaration4() { parsing.get().doTest(); }
  @Test
  public void testFor$tupleDeclaration() { parsing.get().doTest(); }
  @Test
  public void testFor$tupleDeclarationCommaAfterVar() { parsing.get().doTest(); }
  @Test
  public void testFor$tupleDeclarationNoInitializer() { parsing.get().doTest(); }
  @Test
  public void testFor$tupleDeclarationNoInitializerValue() { parsing.get().doTest(); }
  @Test
  public void testFor$tupleDeclarationSingleVariable() { parsing.get().doTest(); }
  @Test
  public void testFor$multilineClause() { parsing.get().doTest(); }

  @Test
  public void testIfstmt$if1() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$if2() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$if3() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$if4() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$if5() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$if6() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$if7() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$applicaitonCondition() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$keywordOnly() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$keywordLParen() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$noCondition() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$noRParen() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$noThen() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$noThenNL() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$noElse() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$noThenWithElse() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$nls() { parsing.get().doTest(); }
  @Test
  public void testIfstmt$separatorsBeforeElse() { parsing.get().doTest(); }

  @Test
  public void testImports$imp0() { parsing.get().doTest(); }
  @Test
  public void testImports$imp1() { parsing.get().doTest(); }
  @Test
  public void testImports$imp2() { parsing.get().doTest(); }
  @Test
  public void testImports$imp3() { parsing.get().doTest(); }
  @Test
  public void testImports$imp4() { parsing.get().doTest(); }
  @Test
  public void testImports$imp5() { parsing.get().doTest(); }
  @Test
  public void testImports$imp6() { parsing.get().doTest(); }
  @Test
  public void testImports$imp7() { parsing.get().doTest(); }
  @Test
  public void testImports$imp8() { parsing.get().doTest(); }
  @Test
  public void testImports$module0() { parsing.get().doTest(); }
  @Test
  public void testImports$module1() { parsing.get().doTest(); }
  @Test
  public void testImports$module2() { parsing.get().doTest(); }
  @Test
  public void testImports$typeArguments() { parsing.get().doTest(); }

  @Test
  public void testKing_regex$king1() { parsing.get().doTest(); }
  @Test
  public void testKing_regex$king2() { parsing.get().doTest(); }
  @Test
  public void testKing_regex$king3() { parsing.get().doTest(); }
  @Test
  public void testKing_regex$king4() { parsing.get().doTest(); }

  @Test
  public void testLabeled$label1() { parsing.get().doTest(); }
  @Test
  public void testLabeled$label2() { parsing.get().doTest(); }
  @Test
  public void testLabeled$label3() { parsing.get().doTest(); }

  @Test
  public void testLoop$while1() { parsing.get().doTest(); }
  @Test
  public void testLoop$while2() { parsing.get().doTest(); }
  @Test
  public void testLoop$while3() { parsing.get().doTest(); }
  @Test
  public void testLoop$while4() { parsing.get().doTest(); }
  @Test
  public void testLoop$while6() { parsing.get().doTest(); }
  @Test
  public void testLoop$while7() { parsing.get().doTest(); }
  @Test
  public void testLoop$whileEmptyBody() { parsing.get().doTest(); }
  @Test
  public void testLoop$dowhile0() { parsing.get().doTest(); }
  @Test
  public void testLoop$dowhile1() { parsing.get().doTest(); }
  @Test
  public void testLoop$dowhile2() { parsing.get().doTest(); }
  @Test
  public void testLoop$dowhile3() { parsing.get().doTest(); }
  @Test
  public void testLoop$dowhile4() { parsing.get().doTest(); }
  @Test
  public void testLoop$dowhile5() { parsing.get().doTest(); }
  @Test
  public void testLoop$dowhile6() { parsing.get().doTest(); }
  @Test
  public void testLoop$dowhile7() { parsing.get().doTest(); }

  @Test
  public void testMethods$method1() { parsing.get().doTest(); }
  @Test
  public void testMethods$method2() { parsing.get().doTest(); }
  @Test
  public void testMethods$method3() { parsing.get().doTest(); }
  @Test
  public void testMethods$method4() { parsing.get().doTest(); }
  @Test
  public void testMethods$method5() { parsing.get().doTest(); }
  @Test
  public void testMethods$method6() { parsing.get().doTest(); }
  @Test
  public void testMethods$vararg() { parsing.get().doTest(); }

  @Test
  public void testMultiple_assign$grvy2086() { parsing.get().doTest("multiple_assign/grvy-2086.test"); }
  @Test
  public void testMultiple_assign$mult_assign() { parsing.get().doTest(); }
  @Test
  public void testMultiple_assign$mult_def() { parsing.get().doTest(); }
  @Test
  public void testMultiple_assign$without_assign() { parsing.get().doTest(); }
  @Test
  public void testMultiple_assign$lineBreakAfterLeftParen() { parsing.get().doTest(); }
  @Test
  public void testMultiple_assign$lineBreakBeforeComma() { parsing.get().doTest(); }
  @Test
  public void testMultiple_assign$lineBreakAfterComma() { parsing.get().doTest(); }
  @Test
  public void testMultiple_assign$lineBreakBeforeRightParen() { parsing.get().doTest(); }
  @Test
  public void testMultiple_assign$lineBreakBeforeAssignment() { parsing.get().doTest(); }
  @Test
  public void testMultiple_assign$lineBreakAfterAssignment() { parsing.get().doTest(); }

  @Test
  public void testSwitch$laforge1() { parsing.get().doTest(); }
  @Test
  public void testSwitch$swit1() { parsing.get().doTest(); }
  @Test
  public void testSwitch$swit2() { parsing.get().doTest(); }
  @Test
  public void testSwitch$swit3() { parsing.get().doTest(); }
  @Test
  public void testSwitch$swit4() { parsing.get().doTest(); }
  @Test
  public void testSwitch$swit5() { parsing.get().doTest(); }
  @Test
  public void testSwitch$swit6() { parsing.get().doTest(); }
  @Test
  public void testSwitch$swit7() { parsing.get().doTest(); }
  @Test
  public void testSwitch$swit8() { parsing.get().doTest(); }
  @Test
  public void testSwitch$identifierWithin() { parsing.get().doTest(); }

  @Test
  public void testSyn$syn1() { parsing.get().doTest(); }
  @Test
  public void testSyn$newLineBeforeBlock() { parsing.get().doTest(); }

  @Test
  public void testTop_methods$method1() { parsing.get().doTest(); }
  @Test
  public void testTop_methods$method2() { parsing.get().doTest(); }
  @Test
  public void testTop_methods$method3() { parsing.get().doTest(); }
  @Test
  public void testTop_methods$method4() { parsing.get().doTest(); }

  @Test
  public void testTry_catch$try1() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$try2() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$try3() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$try4() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$try5() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$try6() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$try7() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$newLineBeforeCatchBlock() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$newLineBeforeFinallyBlock() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$tryResources() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$tryResourcesEmpty() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$tryResourcesNoRparen() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$tryResourcesNoRparenAfterResource() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$tryResourcesNLAfterResource() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$tryResourcesSeparators() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$tryResourcesTyped() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$tryResourcesMultiline() { parsing.get().doTest(); }
  @Test
  public void testTry_catch$multicatchMultiline() { parsing.get().doTest(); }

  @Test
  public void testTuples$doubleParens() { parsing.get().doTest(); }
  @Test
  public void testTuples$methCallNotTuple() { parsing.get().doTest(); }
  @Test
  public void testTuples$nestedTupleUnsupp() { parsing.get().doTest(); }
  @Test
  public void testTuples$tupleInClass() { parsing.get().doTest(); }
  @Test
  public void testTuples$tupleInScript() { parsing.get().doTest(); }
  @Test
  public void testTuples$tupleNotInit() { parsing.get().doTest(); }
  @Test
  public void testTuples$tupleNotInit2() { parsing.get().doTest(); }
  @Test
  public void testTuples$tupleOneVarInLine() { parsing.get().doTest(); }
  @Test
  public void testTuples$tupleTypeErr() { parsing.get().doTest(); }
  @Test
  public void testTuples$tupleWithoutDef() { parsing.get().doTest(); }
  @Test
  public void testTuples$tupleWithoutVariables() { parsing.get().doTest(); }
  @Test
  public void testTuples$differentModifiers() { parsing.get().doTest(); }

  @Test
  public void testTypedef$classes$abstr() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$class1() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$class10() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$class11() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$class2() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$class3() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$class4() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$class5() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$class6() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$class7() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$class8() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$class9() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$errors$classerr1() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$errors$classerr2() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$errors$classerr3() { parsing.get().doTest(); }
  @Test
  public void testTypedef$classes$errors$class_error() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$construct12() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor1() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor10() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor11() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor13() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor14() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor15() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor2() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor3() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor4() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor5() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor6() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor7() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor8() { parsing.get().doTest(); }
  @Test
  public void testTypedef$constructors$constructor9() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum1() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum10() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum11() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum12() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum13() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum2() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum3() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum4() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum5() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum6() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum7() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum8() { parsing.get().doTest(); }
  @Test
  public void testTypedef$enums$enum9() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$errors$interfaceerr1() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$errors$interfaceerr2() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$errors$interfaceerr3() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$interface1() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$interface2() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$interface3() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$interface4() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$interface5() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$member3() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$members$member1() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$members$member2() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$members$member3() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$members$member4() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$members$member5() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$members$member6() { parsing.get().doTest(); }
  @Test
  public void testTypedef$interfaces$members$memeber7() { parsing.get().doTest(); }
  @Test
  public void testTypedef$traits$trait1() { parsing.get().doTest(); }
  @Test
  public void testTypedef$methods$method2() { parsing.get().doTest(); }
  @Test
  public void testTypedef$methods$method3() { parsing.get().doTest(); }
  @Test
  public void testTypedef$methods$method4() { parsing.get().doTest(); }

  @Test
  public void testUse$use1() { parsing.get().doTest(); }

  @Test
  public void testVardef$vardef1() { parsing.get().doTest(); }
  @Test
  public void testVardef$vardef2() { parsing.get().doTest(); }
  @Test
  public void testVardef$vardef3() { parsing.get().doTest(); }
  @Test
  public void testVardef$vardef4() { parsing.get().doTest(); }
  @Test
  public void testVardef$vardeferr() { parsing.get().doTest(); }
  @Test
  public void testVardef$vardeferrsingle4() { parsing.get().doTest(); }
  @Test
  public void testVardef$newlineAfterModifiers() { parsing.get().doTest(); }

  @Test
  public void testWith$with1() { parsing.get().doTest(); }
  @Test
  public void testWith$with2() { parsing.get().doTest(); }

  @Test
  public void testAfterAs() { parsing.get().doTest(); }

  @Test
  public void testUnnamedField() { parsing.get().doTest(); }

  @Test
  public void testIfRecovery() { parsing.get().doTest(); }

  @Test
  public void testSemicolonsOnDifferentLines() { parsing.get().doTest(); }

  @Test
  public void testRecoverySameLine() { parsing.get().doTest(); }

  @Test
  public void testRecoveryNewLine() { parsing.get().doTest(); }

  @Test
  public void testRecoveryMissingSeparator() { parsing.get().doTest(); }
}
