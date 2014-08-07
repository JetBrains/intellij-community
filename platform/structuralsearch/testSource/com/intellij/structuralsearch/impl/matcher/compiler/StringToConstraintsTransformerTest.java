package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.UnsupportedPatternException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Bas Leijdekkers
 */
public class StringToConstraintsTransformerTest {

  private MatchOptions myOptions;

  @Before
  public void setUp() throws Exception {
    myOptions = new MatchOptions();
  }

  @Test(expected = MalformedPatternException.class)
  public void testCharacterExpectedAfterQuote() {
    test("' asdf");
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

  @Test(expected = UnsupportedPatternException.class)
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

  @Test
  public void testMethodReference() {
    test("'_a::'_b");
    assertEquals("$a$::$b$", myOptions.getSearchPattern());
  }

  private void test(String pattern) {
    myOptions.setSearchPattern(pattern);
    StringToConstraintsTransformer.transformOldPattern(myOptions);
  }
}
