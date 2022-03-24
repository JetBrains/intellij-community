// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class StringToConstraintsTransformerTest extends LightPlatformTestCase {

  private MatchOptions myOptions;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myOptions = new MatchOptions();
  }

  public void testCharacterExpectedAfterQuote() {
    expectException("' asdf", "Character expected after single quote");
  }

  public void testCharacterExpectedAfterQuote2() {
    expectException("'", "Character expected after single quote");
  }

  public void testUnexpectedEndOfPattern() {
    expectException("'_a{", "Digit expected");
  }

  public void testDigitExpected() {
    expectException("'a{a", "Digit expected");
  }

  public void testDigitExpected2() {
    expectException("'a{1,a}", "Digit, '}' or ',' expected");
  }

  public void testCountedOccurs() {
    test("'_a{3,}'_b{4} '_c{,5}");
    final MatchVariableConstraint a = myOptions.getVariableConstraint("a");
    assertEquals(3, a.getMinCount());
    assertEquals(Integer.MAX_VALUE, a.getMaxCount());
    final MatchVariableConstraint b = myOptions.getVariableConstraint("b");
    assertEquals(4, b.getMinCount());
    assertEquals(4, b.getMaxCount());
    final MatchVariableConstraint c = myOptions.getVariableConstraint("c");
    assertEquals(0, c.getMinCount());
    assertEquals(5, c.getMaxCount());
  }

  public void testEmptyQuantifier1() {
    expectException("'_a{}", "Empty quantifier");
  }

  public void testEmptyQuantifier2() {
    expectException("'_a{,}", "Empty quantifier");
  }

  public void testOverflow() {
    expectException("'a{2147483648}", "Value overflow");
  }

  public void testMissingBrace() {
    expectException("'a{1,3", "Digit or '}' expected");
  }

  public void testNoOptions() {
    expectException("'a:", "Constraint expected after ':'");
  }

  public void testColon() {
    test("for('_t 'a : '_b) {}");
    assertEquals("for($t$ $a$ : $b$) {}", myOptions.getSearchPattern());
  }

  public void testNoOptions2() {
    expectException("'a:+", "Constraint expected after '+'");
  }

  public void testUnclosedCondition() {
    expectException("'a:[", "']' expected");
  }

  public void testClosedCondition() {
    expectException("'a:[]", "Constraint expected after '['");
  }

  public void testEmptyNegated() {
    expectException("'a:[!]", "Constraint expected after '!'");
  }

  public void testCondition() {
    expectException("'a:[aap()]", "Constraint 'aap' not recognized");
  }

  public void testIncompleteCondition() {
    expectException("'a:[regex(]", "Argument expected on 'regex' constraint");
  }

  public void testIncompleteCondition2() {
    expectException("'a:[regex()]", "Argument expected on 'regex' constraint");
  }

  public void testIncompleteMultipleCondition() {
    expectException("'a:[regex( a ) &&]", "Constraint expected after '&&'");
  }

  public void testInvalidRegularExpression() {
    try {
      test("'a:x!(");
    } catch (MalformedPatternException e) {
      assertTrue(e.getMessage().startsWith(String.format("Invalid regular expression: Unclosed group near index 3%nx!(")));
    }
  }

  public void testRepeatingConstraints() {
    expectException("'a*:foo 'a+:[regex( bla )]", "Constraints are only allowed on the first reference of a variable");
  }

  public void testRepeatingConstraints2() {
    expectException("'a:foo 'a*", "Constraints are only allowed on the first reference of a variable");
  }

  public void testMethodReference() {
    test("'_a::'_b");
    assertEquals("$a$::$b$", myOptions.getSearchPattern());
  }

  public void testCompleteMatchConditions() {
    test("[within( \"if('_a) { 'st*; }\" )]1+1");
    assertEquals("1+1", myOptions.getSearchPattern());
    final MatchVariableConstraint constraint = myOptions.getVariableConstraint(Configuration.CONTEXT_VAR_NAME);
    assertEquals("\"if('_a) { 'st*; }\"", constraint.getWithinConstraint());
  }

  public void testBadWithin() {
    expectException("'_type 'a:[within( \"if ('_a) { '_st*; }\" )] = '_b;", "Constraint 'within' is only applicable to Complete Match");
  }

  public void testNestedPatterns() {
    test("[within( \"if ('_a:[regex( .*e.* )]) { '_st*; }\" )]'_type 'a = '_b;");
    assertEquals("$type$ $a$ = $b$;", myOptions.getSearchPattern());
    final MatchVariableConstraint constraint = myOptions.getVariableConstraint(Configuration.CONTEXT_VAR_NAME);
    assertEquals("\"if ('_a:[regex( .*e.* )]) { '_st*; }\"", constraint.getWithinConstraint());

    test("[!within( \"<'_tag:[regex( ul|ol )] />\" )]<li />");
    assertEquals("<li />", myOptions.getSearchPattern());
    final MatchVariableConstraint constraint2 = myOptions.getVariableConstraint(Configuration.CONTEXT_VAR_NAME);
    assertEquals("\"<'_tag:[regex( ul|ol )] />\"", constraint2.getWithinConstraint());
    assertTrue(constraint2.isInvertWithinConstraint());

    test("[within( \"if ('_a:[regex( \".*\" )]) { '_st*; }\" )]'_type 'a = '_b;");
    assertEquals("$type$ $a$ = $b$;", myOptions.getSearchPattern());
    final MatchVariableConstraint constraint3 = myOptions.getVariableConstraint(Configuration.CONTEXT_VAR_NAME);
    assertEquals("\"if ('_a:[regex( \".*\" )]) { '_st*; }\"", constraint3.getWithinConstraint());
  }

  public void testEscaping() {
    test("\\[within( \"if ('_a) { '_st*; }\" )]");
    assertEquals("[within( \"if ($a$) { $st$; }\" )]", myOptions.getSearchPattern());
    test("\\[");
    assertEquals("[", myOptions.getSearchPattern());
    test("\\'aaa");
    assertEquals("'aaa", myOptions.getSearchPattern());
    test("'a\\:");
    assertEquals("$a$:", myOptions.getSearchPattern());
  }

  public void testQuotes() {
    test("''");
    assertEquals("'", myOptions.getSearchPattern());
    test("'''c");
    assertEquals("'$c$", myOptions.getSearchPattern());
    test("'a''b");
    assertEquals("'a'$b$", myOptions.getSearchPattern());
    test("'aa'_bb");
    assertEquals("$aa$$bb$", myOptions.getSearchPattern());
    test("'\\n''z");
    assertEquals("'\\n'$z$", myOptions.getSearchPattern());
    test("'\\u0123''a");
    assertEquals("'\\u0123'$a$", myOptions.getSearchPattern());
  }

  public void testComplexRegexes() {
    test("'_t:[regex( *Object\\[\\] ) ] '_t2");
    assertEquals("$t$ $t2$", myOptions.getSearchPattern());
    final MatchVariableConstraint constraint = myOptions.getVariableConstraint("t");
    assertTrue(constraint.isWithinHierarchy());
    assertEquals("Object\\[\\]", constraint.getRegExp());

    test("// 'Comment:[regex( .*(?:comment).* )]");
    assertEquals("// $Comment$", myOptions.getSearchPattern());
    final MatchVariableConstraint constraint1 = myOptions.getVariableConstraint("Comment");
    assertEquals(".*(?:comment).*", constraint1.getRegExp());
  }

  public void testInvert() {
    test("'a:[!regexw(a)&&formal(*List)]");
    assertEquals("$a$", myOptions.getSearchPattern());
    final MatchVariableConstraint constraint = myOptions.getVariableConstraint("a");
    assertTrue(constraint.isWholeWordsOnly());
    assertEquals("a", constraint.getRegExp());
    assertTrue(constraint.isInvertRegExp());
    assertTrue(constraint.isFormalArgTypeWithinHierarchy());
    assertFalse(constraint.isInvertFormalType());
    assertEquals("List", constraint.getNameOfFormalArgType());
  }

  public void testInvertShortcut() {
    test("class '_X:!*A {}");
    assertEquals("class $X$ {}", myOptions.getSearchPattern());
    final MatchVariableConstraint constraint = myOptions.getVariableConstraint("X");
    assertTrue(constraint.isInvertRegExp());
    assertTrue(constraint.isWithinHierarchy());
    assertEquals("A", constraint.getRegExp());
  }

  public void testAmpersandsExpected() {
    expectException("'a:[regex(a) regex(b)]", "'&&' expected");
  }

  public void testUnexpectedAmpersands() {
    expectException("'a:[&&regex(a)]", "Unexpected '&'");
  }

  public void testUnbalancedSpacesSurroundingContent() {
    expectException("'a:[regex(  .* ) ]", "'  )' expected");
  }

  public void testInvalidRegex() {
    expectException("'T:{ ;", "Invalid regular expression: Illegal repetition");
  }

  public void testNoSpacesSurroundingRegexNeeded() {
    test("'t:[regex(a)]");
    final MatchVariableConstraint constraint = myOptions.getVariableConstraint("t");
    assertEquals("a", constraint.getRegExp());
  }

  public void testMultipleTargets() {
    expectException("try { 'Statements+; } catch('_ '_) { 'HandlerStatements+; }", "Only one target allowed");
  }

  public void testSameTargetMultipleTimes() {
    test("'a = 'a;");
  }

  public void testPresenceOfContext() {
    test("a");
    assertNotNull(myOptions.getVariableConstraint(Configuration.CONTEXT_VAR_NAME));
  }

  public void testBrackets() {
    test("'_x:[exprtype( java\\.lang\\.String\\[\\]\\[\\] )]");
    final MatchVariableConstraint constraint = myOptions.getVariableConstraint("x");
    assertEquals("java\\.lang\\.String\\[\\]\\[\\]", constraint.getNameOfExprType());
  }

  public void testAdditionalConstraint() {
    test("'_x:[_custom( test )]");
    final MatchVariableConstraint x = myOptions.getVariableConstraint("x");
    assertEquals("test", x.getAdditionalConstraint("custom"));
  }

  private void expectException(@NotNull String criteria, @NotNull String exceptionMessage) {
    try {
      test(criteria);
    } catch (MalformedPatternException e) {
      assertTrue(e.getMessage().startsWith(exceptionMessage));
    }
  }

  private void test(@NotNull String criteria) {
    StringToConstraintsTransformer.transformCriteria(criteria, myOptions);
  }
}
