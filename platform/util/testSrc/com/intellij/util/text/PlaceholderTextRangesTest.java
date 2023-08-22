package com.intellij.util.text;

import com.intellij.openapi.util.TextRange;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.UsefulTestCase.assertOrderedEquals;

public class PlaceholderTextRangesTest extends TestCase {

  public void testMissingPrefixNoRange() {
    //            0123
    assertRanges("a b}");
  }

  public void testMissingSuffixNoRange() {
    //            0123
    assertRanges("a {b");
  }

  public void testMissingPrefixFollowedByValid() {
    //            012345
    assertRanges("a }{b}",
                 4, 5);
  }

  public void testOneSimpleRange() {
    //            01234
    assertRanges("a {b}", 3, 4);
  }

  public void testOneSimpleRangeUseFullTextRange() {
    //            01234
    assertRanges("a {b}",
                 "{", "}",
                 true, false,
                 2, 5);
  }

  public void testTwoSimpleRanges() {
    //            01234567890
    assertRanges("a {b} c {d}",
                 3, 4,
                 9, 10);
  }

  public void testOneSimpleRangeCustom() {
    //            0123456
    assertRanges("a [[b]]",
                 "[[", "]]",
                 false, false,
                 4, 5);
  }

  public void testTwoSimpleRangesCustom() {
    //            012345678901234
    assertRanges("a [[b]] c [[d]]",
                 "[[", "]]",
                 false, false,
                 4, 5,
                 12, 13);
  }

  public void testOneSimpleRangePrefixSuffixEqual() {
    assertRanges("@a@",
                 "@", "@",
                 false, false,
                 1, 2);
  }

  public void testEmptyRangePrefixSuffixEqual() {
    assertRanges("@@",
                 "@", "@",
                 false, false,
                 1, 1);
  }

  public void testEmptyRangePrefixSuffixStartWithSameCharacter() {
    assertRanges("###",
                 "#", "##",
                 false, false,
                 1, 1);
  }

  public void testOneSimpleRangePrefixSuffixStartWithSameCharacter() {
    assertRanges("#A##",
                 "#", "##",
                 false, false,
                 1, 2);
  }

  public void testOverlappingTokes() {
    assertRanges("!##a!##?",
                 "!##", "##?",
                 false, false,
                 3, 5);
  }

  public void testOneRepeatablePrefix() {
    assertRanges("###a))",
                 "##", "))",
                 false, false,
                 3, 4);
  }

  public void testMissingSuffixPrefixSuffixEqual() {
    assertRanges("@a",
                 "@", "@",
                 false, false);
  }

  public void testOneNestedRange() {
    //            01234567
    assertRanges("a {b{c}}",
                 5, 6,
                 3, 7);
  }

  public void testOneNestedRangeWithRepeatablePrefix() {
    assertRanges("####a))))",
                 "##", "))",
                 false, false,
                 4, 5, 2, 7);
  }

  public void testTwoNestedRanges() {
    //            01234567890
    assertRanges("a {b{c{d}}}",
                 7, 8,
                 5, 9,
                 3, 10);
  }

  public void testUnclosedOuterWithNestedRange() {
    //            0123456
    assertRanges("a {b{c}",
                 5, 6);
  }

  public void testFilterOneNestedRange() {
    //            01234567
    assertRanges("a {b{c}}",
                 "{", "}",
                 false, true,
                 3, 7);
  }

  public void testFilterTwoLevelNestedRange() {
    //            0123456789
    assertRanges("a {b{{c}}}",
                 "{", "}",
                 false, true,
                 3, 9);
  }

  public void testTwoTopLevelRangesWithNested() {
    // IDEA-246585
    assertRanges("a {b{c}p}/{d}",
                 5, 6,
                 3, 8,
                 11, 12);

    assertRanges("a {b{c}}/{d}",
                 5, 6,
                 3, 7,
                 10, 11);

    assertRanges("a {b}/{d{c}}",
                 "{", "}",
                 false, true,
                 3, 4,
                 7, 11);
  }

  public void testDeepNestedBraces() {
    assertRanges("{{{}{}}}",
                 3, 3,
                 5, 5,
                 2, 6,
                 1, 7);
  }

  private static void assertRanges(String text,
                                   int... rangeOffsets) {
    assertRanges(text, "{", "}", false, false, rangeOffsets);
  }

  private static void assertRanges(String text,
                                   String prefix, String suffix,
                                   boolean useFullTextRange,
                                   boolean filterNested,
                                   int... rangeOffsets) {
    assertEquals("start/end offsets not balanced", 0, rangeOffsets.length % 2);

    int expectedRangeCount = rangeOffsets.length / 2;
    List<TextRange> expectedRanges = new ArrayList<>(expectedRangeCount);
    for (int i = 0; i < expectedRangeCount; i++) {
      expectedRanges.add(new TextRange(rangeOffsets[i * 2], rangeOffsets[i * 2 + 1]));
    }

    Set<TextRange> ranges = PlaceholderTextRanges.getPlaceholderRanges(text, prefix, suffix, useFullTextRange, filterNested);
    assertOrderedEquals(ranges, expectedRanges);
  }
}
