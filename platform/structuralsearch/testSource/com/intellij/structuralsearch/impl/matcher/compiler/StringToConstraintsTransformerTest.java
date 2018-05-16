// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

/**
 * @author Bas Leijdekkers
 */
public class StringToConstraintsTransformerTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private MatchOptions myOptions;

  @Before
  public void setUp() {
    myOptions = new MatchOptions();
  }

  @Test
  public void testCharacterExpectedAfterQuote() {
    expectException("' asdf", "Character expected after single quote");
  }

  @Test
  public void testCharacterExpectedAfterQuote2() {
    expectException("'", "Character expected after single quote");
  }

  @Test
  public void testUnexpectedEndOfPattern() {
    expectException("'_a{", "Digit expected");
  }

  @Test
  public void testDigitExpected() {
    expectException("'a{a", "Digit expected");
  }

  @Test
  public void testDigitExpected2() {
    expectException("'a{1,a}", "Digit, '}' or ',' expected");
  }

  @Test
  public void testCountedOccurs() {
    test("'_a{3,}'_b{4} '_c{,5}");
    MatchVariableConstraint constraint = myOptions.getVariableConstraint("a");
    assertEquals(3, constraint.getMinCount());
    assertEquals(Integer.MAX_VALUE, constraint.getMaxCount());
    constraint = myOptions.getVariableConstraint("b");
    assertEquals(4, constraint.getMinCount());
    assertEquals(4, constraint.getMaxCount());
    constraint = myOptions.getVariableConstraint("c");
    assertEquals(0, constraint.getMinCount());
    assertEquals(5, constraint.getMaxCount());
  }

  @Test
  public void testEmptyQuantifier1() {
    expectException("'_a{}", "Empty quantifier");
  }

  @Test
  public void testEmptyQuantifier2() {
    expectException("'_a{,}", "Empty quantifier");
  }

  @Test
  public void testOverflow() {
    expectException("'a{2147483648}", "Value overflow");
  }

  @Test
  public void testMissingBrace() {
    expectException("'a{1,3", "Digit or '}' expected");
  }

  @Test
  public void testNoOptions() {
    expectException("'a:", "Constraint expected after ':'");
  }

  @Test
  public void testColon() {
    test("for('_t 'a : '_b) {}");
    assertEquals("for($t$ $a$ : $b$) {}", myOptions.getSearchPattern());
  }

  @Test
  public void testNoOptions2() {
    expectException("'a:+", "Constraint expected after '+'");
  }

  @Test
  public void testUnclosedCondition() {
    expectException("'a:[", "']' expected");
  }

  @Test
  public void testClosedCondition() {
    expectException("'a:[]", "Constraint expected after '['");
  }

  @Test
  public void testEmptyNegated() {
    expectException("'a:[!]", "Constraint expected after '!'");
  }

  @Test
  public void testCondition() {
    expectException("'a:[aap()]", "Constraint 'aap' not recognized");
  }

  @Test
  public void testIncompleteCondition() {
    expectException("'a:[regex(]", "Argument expected on 'regex' constraint");
  }

  @Test
  public void testIncompleteCondition2() {
    expectException("'a:[regex()]", "Argument expected on 'regex' constraint");
  }

  @Test
  public void testIncompleteMultipleCondition() {
    expectException("'a:[regex( a ) &&]", "Constraint expected after '&&'");
  }

  @Test
  public void testInvalidRegularExpression() {
    expectException("'a:x!(", "Invalid regular expression: Unclosed group near index 3\n" +
                              "x!(\n" +
                              "   ^");
  }

  @Test
  public void testRepeatingConstraints() {
    expectException("'a*:foo 'a+:[regex( bla )]", "Constraints are only allowed on the first reference of a variable");
  }

  @Test
  public void testRepeatingConstraints2() {
    expectException("'a:foo 'a*", "Constraints are only allowed on the first reference of a variable");
  }

  @Test
  public void testMethodReference() {
    test("'_a::'_b");
    assertEquals("$a$::$b$", myOptions.getSearchPattern());
  }

  @Test
  public void testCompleteMatchConditions() {
    test("[within( \"if('_a) { 'st*; }\" )]1+1");
    assertEquals("1+1", myOptions.getSearchPattern());
    final MatchVariableConstraint constraint = myOptions.getVariableConstraint(Configuration.CONTEXT_VAR_NAME);
    assertEquals("\"if('_a) { 'st*; }\"", constraint.getWithinConstraint());
  }

  @Test
  public void testBadWithin() {
    expectException("'_type 'a:[within( \"if ('_a) { '_st*; }\" )] = '_b;", "Constraint 'within' is only applicable to Complete Match");
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testInvertShortcut() {
    test("class '_X:!*A {}");
    assertEquals("class $X$ {}", myOptions.getSearchPattern());
    final MatchVariableConstraint constraint = myOptions.getVariableConstraint("X");
    assertTrue(constraint.isInvertRegExp());
    assertTrue(constraint.isWithinHierarchy());
    assertEquals("A", constraint.getRegExp());
  }

  @Test
  public void testAmpersandsExpected() {
    expectException("'a:[regex(a) regex(b)]", "'&&' expected");
  }

  @Test
  public void testUnexpectedAmpersands() {
    expectException("'a:[&&regex(a)]", "Unexpected '&'");
  }

  @Test
  public void testUnbalancedSpacesSurroundingContent() {
    expectException("'a:[regex(  .* ) ]", "'  )' expected");
  }

  @Test
  public void testInvalidRegex() {
    expectException("'T:{ ;", "Invalid regular expression: Illegal repetition\n" +
                              "{");
  }

  @Test
  public void testNoSpacesSurroundingRegexNeeded() {
    test("'t:[regex(a)]");
    final MatchVariableConstraint constraint = myOptions.getVariableConstraint("t");
    assertEquals("a", constraint.getRegExp());
  }

  @Test
  public void testMultipleTargets() {
    expectException("try { 'Statements+; } catch('_ '_) { 'HandlerStatements+; }", "Only one target allowed");
  }

  @Test
  public void testSameTargetMultipleTimes() {
    test("'a = 'a;");
  }

  private void expectException(String criteria, String exceptionMessage) {
    thrown.expect(MalformedPatternException.class);
    thrown.expectMessage(equalTo(exceptionMessage));
    test(criteria);
  }

  private void test(String criteria) {
    StringToConstraintsTransformer.transformCriteria(criteria, myOptions);
  }
}
