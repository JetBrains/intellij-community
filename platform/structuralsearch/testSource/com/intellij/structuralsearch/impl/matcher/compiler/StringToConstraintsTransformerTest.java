/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.UnsupportedPatternException;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Bas Leijdekkers
 */
public class StringToConstraintsTransformerTest {

  private MatchOptions myOptions;

  @Before
  public void setUp() {
    myOptions = new MatchOptions();
  }

  @Test(expected = MalformedPatternException.class)
  public void testCharacterExpectedAfterQuote() {
    test("' asdf");
  }

  @Test(expected = MalformedPatternException.class)
  public void testCharacterExpectedAfterQuote2() {
    test("'");
  }

  @Test(expected = MalformedPatternException.class)
  public void testUnexpectedEndOfPattern() {
    test("'_a{");
  }

  @Test(expected = MalformedPatternException.class)
  public void testDigitExpected() {
    test("'a{a");
  }

  @Test(expected = MalformedPatternException.class)
  public void testDigitExpected2() {
    test("'a{1,a}");
  }

  @Test
  public void testZeroOccurs() {
    test("'a{,}");
    final MatchVariableConstraint constraint = myOptions.getVariableConstraint("a");
    assertEquals(0, constraint.getMinCount());
    assertEquals(0, constraint.getMaxCount());
  }

  @Test(expected = MalformedPatternException.class)
  public void testOverflow() {
    test("'a{2147483648}");
  }

  @Test(expected = MalformedPatternException.class)
  public void testMissingBrace() {
    test("'a{1,3");
  }

  @Test(expected = MalformedPatternException.class)
  public void testNoOptions() {
    test("'a:");
  }

  @Test
  public void testColon() {
    test("for('_t 'a : '_b) {}");
    assertEquals("for($t$ $a$ : $b$) {}", myOptions.getSearchPattern());
  }

  @Test(expected = MalformedPatternException.class)
  public void testNoOptions2() {
    test("'a:+");
  }

  @Test(expected = MalformedPatternException.class)
  public void testUnclosedCondition() {
    test("'a:[");
  }

  @Test(expected = MalformedPatternException.class)
  public void testClosedCondition() {
    test("'a:[]");
  }

  @Test(expected = MalformedPatternException.class)
  public void testEmptyNegated() {
    test("'a:[!]");
  }

  @Test(expected = UnsupportedPatternException.class)
  public void testCondition() {
    test("'a:[aap()]");
  }

  @Test(expected = MalformedPatternException.class)
  public void testIncompleteCondition() {
    test("'a:[regex(]");
  }

  @Test(expected = MalformedPatternException.class)
  public void testIncompleteCondition2() {
    test("'a:[regex()]");
  }

  @Test(expected = MalformedPatternException.class)
  public void testIncompleteMultipleCondition() {
    test("'a:[regex( a ) &&]");
  }

  @Test(expected = MalformedPatternException.class)
  public void testInvalidRegularExpression() {
    test("'a:x!(");
  }

  @Test(expected = MalformedPatternException.class)
  public void testRepeatingConstraints() {
    test("'a*:foo 'a+:[regex( bla )]");
  }

  @Test(expected = MalformedPatternException.class)
  public void testRepeatingConstraints2() {
    test("'a:foo 'a*");
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

  @Test(expected = MalformedPatternException.class)
  public void testBadWithin() {
    test("'_type 'a:[within( \"if ('_a) { '_st*; }\" )] = '_b;");
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

  @Test(expected = MalformedPatternException.class)
  public void testAmpersandsExpected() {
    test("'a:[regex(a) regex(b)]");
  }

  @Test(expected = MalformedPatternException.class)
  public void testUnexpectedAmpersands() {
    test("'a:[&&regex(a)]");
  }

  @Test(expected = MalformedPatternException.class)
  public void testUnbalancedSpacesSurroundingContent() {
    test("'a:[regex(  .* ) ]");
  }

  @Test(expected = MalformedPatternException.class)
  public void testInvalidRegex() {
    test("'T:{ ;");
  }

  @Test
  public void testNoSpacesSurroundingRegexNeeded() {
    test("'t:[regex(a)]");
    final MatchVariableConstraint constraint = myOptions.getVariableConstraint("t");
    assertEquals("a", constraint.getRegExp());
  }

  private void test(String criteria) {
    StringToConstraintsTransformer.transformCriteria(criteria, myOptions);
  }
}
