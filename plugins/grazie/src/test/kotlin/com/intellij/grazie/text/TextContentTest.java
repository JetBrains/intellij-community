package com.intellij.grazie.text;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.IntStreamEx;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.ImperativeCommand;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.grazie.text.TextContent.TextDomain.PLAIN_TEXT;

public class TextContentTest extends BasePlatformTestCase {
  public void testTextOffsetLean() {
    var file = myFixture.configureByText("a.txt", "aaabbbccc");
    var content = TextContent.psiFragment(PLAIN_TEXT, file).excludeRange(new TextRange(3, 6));
    assertEquals(0, content.textOffsetToFile(0));
    assertEquals(9, content.textOffsetToFile(6));
    
    assertEquals(3, content.textOffsetToFile(3, false));
    assertEquals(6, content.textOffsetToFile(3, true));
  }

  public void testJoinWithUnknown() {
    var file = myFixture.configureByText("a.txt", "aaabbbccc");
    var f1 = psiFragment(file, 0, 3);
    var f2 = psiFragment(file, 3, 6).markUnknown(new TextRange(0, 3));
    var f3 = psiFragment(file, 6, 9);

    var joined = TextContent.join(List.of(f1, f2, f3));
    assertEquals("aaaccc", joined.toString());

    assertFalse(joined.hasUnknownFragmentsIn(new TextRange(0, 2)));
    assertTrue(joined.hasUnknownFragmentsIn(new TextRange(0, 4)));
    assertTrue(joined.hasUnknownFragmentsIn(new TextRange(3, 5)));
    assertTrue(joined.hasUnknownFragmentsIn(new TextRange(3, 3)));

    assertEquals(3, joined.textOffsetToFile(3, false));
    assertEquals(6, joined.textOffsetToFile(3, true));
  }

  private static TextContent psiFragment(PsiFile file, int start, int end) {
    return TextContent.psiFragment(PLAIN_TEXT, file, new TextRange(start, end));
  }

  public void testMarkOffsetUnknown() {
    var full = psiFragment(myFixture.configureByText("a.txt", " abc"), 0, 4);
    assertFalse(full.hasUnknownFragmentsIn(new TextRange(0, 0)));

    var fullUnknown = full.markUnknown(new TextRange(0, 0));
    assertTrue(fullUnknown.hasUnknownFragmentsIn(new TextRange(0, 0)));
    assertTrue(fullUnknown.hasUnknownFragmentsIn(new TextRange(0, 1)));
    assertFalse(fullUnknown.hasUnknownFragmentsIn(new TextRange(1, 2)));

    var trimmedUnknown = fullUnknown.excludeRange(new TextRange(0, 1));
    assertTrue(trimmedUnknown.hasUnknownFragmentsIn(new TextRange(0, 0)));
    assertTrue(trimmedUnknown.hasUnknownFragmentsIn(new TextRange(0, 1)));
    assertFalse(trimmedUnknown.hasUnknownFragmentsIn(new TextRange(1, 2)));
  }

  public void testIntersectsRange() {
    TextRange fragment1 = new TextRange(0, 3);
    TextRange fragment2 = new TextRange(6, 9);
    var file = myFixture.configureByText("a.txt", "aaabbbccc");
    TextContent text = TextContent.join(List.of(psiFragment(file, 0, 3), psiFragment(file, 6, 9)));
    for (int i = 0; i < file.getTextLength(); i++) {
      for (int j = i; j < file.getTextLength(); j++) {
        TextRange range = new TextRange(i, j);
        assertEquals(text.intersectsRange(range), range.intersectsStrict(fragment1) || range.intersectsStrict(fragment2));
      }
    }
  }

  public void testJoinAdjacentFileRanges() {
    var file = myFixture.configureByText("a.txt", "abc");
    TextContent c1 = psiFragment(file, 0, 1);
    TextContent c2 = psiFragment(file, 1, 2);
    assertOrderedEquals(TextContent.join(List.of(c1, c2)).getRangesInFile(), new TextRange(0, 2));
  }

  public void testJoinWithWhitespace() {
    var file = myFixture.configureByText("a.txt", "abbbc");
    var f1 = psiFragment(file, 0, 1);
    var f2 = psiFragment(file, 4, 5).markUnknown(new TextRange(1, 1));

    var joined = TextContent.joinWithWhitespace(' ', List.of(f1, f2));
    assertNotNull(joined);
    assertEquals("a c|", unknownOffsets(joined));
    assertEquals(List.of(0, 1, 4, 5), IntStreamEx.range(4).mapToObj(joined::textOffsetToFile).toList());

    assertEquals("a |", unknownOffsets(joined.markUnknown(new TextRange(2, 3))));
    assertEquals("a |", unknownOffsets(joined.excludeRange(new TextRange(2, 3))));

    assertEquals("a|c|", unknownOffsets(joined.markUnknown(new TextRange(1, 2))));
    assertEquals("ac|", unknownOffsets(joined.excludeRange(new TextRange(1, 2))));

    assertEquals("| c|", unknownOffsets(joined.markUnknown(new TextRange(0, 1))));
    assertEquals("c|", unknownOffsets(joined.excludeRange(new TextRange(0, 1))));
    
    assertEquals(joined, TextContent.joinWithWhitespace(' ', List.of(f1, f2)));
  }

  public static String unknownOffsets(TextContent text) {
    StringBuilder sb = new StringBuilder(text);
    int[] offsets = text.unknownOffsets();
    for (int i = offsets.length - 1; i >= 0; i--) {
      sb.insert(offsets[i], "|");
    }
    return sb.toString();
  }

  public void testUnknownAndExcludeOrderDoesNotMatter() {
    TextContent initial = TextContent.psiFragment(PLAIN_TEXT, myFixture.configureByText("a.txt", "abc"));
    TextContent c1 = initial.markUnknown(new TextRange(1, 1)).excludeRange(new TextRange(1, 2));
    TextContent c2 = initial.excludeRange(new TextRange(1, 2)).markUnknown(new TextRange(1, 1));
    assertEquals("a|c", unknownOffsets(c1));
    assertEquals("a|c", unknownOffsets(c2));
    assertEquals(c1, c2);
  }

  public void testExcludeNearUnknownOffsetPreservesIt() {
    TextContent initial = TextContent.psiFragment(PLAIN_TEXT, myFixture.configureByText("a.txt", "abc"));
    assertEquals("|b|c", unknownOffsets(
      initial.markUnknown(new TextRange(2, 2)).markUnknown(new TextRange(1, 1)).excludeRange(new TextRange(0, 1))));
  }

  public void testBatchExcludeWholeText() {
    checkBatchSequentialExclusion("a", List.of(new TextContent.Exclusion(0, 0, false), new TextContent.Exclusion(0, 1, false)));
  }

  public void testBatchRangeExclusionIsEquivalentToSequential() {
    PropertyChecker
      .checkScenarios(() -> MadTestingUtil.assertNoErrorLoggedIn(env -> {
        String text = env.generateValue(Generator.stringsOf("abc"), "Text %s");
        List<TextContent.Exclusion> ranges = generateSortedRanges(env, text);
        env.logMessage("Ranges " + ranges);

        checkBatchSequentialExclusion(text, ranges);
      }));
  }

  private void checkBatchSequentialExclusion(String text, List<TextContent.Exclusion> ranges) {
    TextContent initial = TextContent.psiFragment(PLAIN_TEXT, myFixture.configureByText("a.txt", text));
    TextContent batchExcluded = initial.excludeRanges(ranges);
    TextContent sequentiallyExcluded = excludeSequentially(ranges, initial);
    if (!sequentiallyExcluded.equals(batchExcluded)) {
      assertEquals(((TextContentImpl)sequentiallyExcluded).tokens, ((TextContentImpl)batchExcluded).tokens);
    }
  }

  private static TextContent excludeSequentially(List<TextContent.Exclusion> ranges, TextContent initial) {
    TextContent result = initial;
    for (TextContent.Exclusion exclusion : ContainerUtil.reverse(ranges)) {
      TextRange range = new TextRange(exclusion.start, exclusion.end);
      result = exclusion.kind == TextContent.ExclusionKind.unknown ? result.markUnknown(range) : result.excludeRange(range);
    }
    return result;
  }

  private static List<TextContent.Exclusion> generateSortedRanges(ImperativeCommand.Environment env, String text) {
    int rangeCount = env.generateValue(Generator.integers(0, 100), null);
    int[] offsets = new int[rangeCount * 2];
    for (int i = 0; i < offsets.length; i++) {
      offsets[i] = env.generateValue(Generator.integers(0, text.length()), null);
    }
    Arrays.sort(offsets);
    List<TextContent.Exclusion> ranges = new ArrayList<>();
    int min = 0;
    for (int i = 0; i < rangeCount; i++) {
      int start = Math.max(min, offsets[i * 2]);
      if (start > text.length()) break;

      int end = Math.max(Math.max(min, offsets[i * 2 + 1]), start);
      ranges.add(new TextContent.Exclusion(start, end, env.generateValue(Generator.booleans(), null)));
      min = end;
    }
    return ranges;
  }

  public void testUnknownWinsOverMarkup() {
    var file = myFixture.configureByText("a.txt", "ab");
    var base = psiFragment(file, 0, 2);
    for (int i = 0; i <= 2; i++) {
      var withMarkup = base.excludeRanges(List.of(new TextContent.Exclusion(i, i, TextContent.ExclusionKind.markup)));
      Assertions.assertArrayEquals(new int[]{i}, withMarkup.markupOffsets(), "Offset " + i);

      var withUnknown = withMarkup.markUnknown(TextRange.from(i, 0));
      assertTrue("Offset " + i, withUnknown.hasUnknownFragmentsIn(TextRange.from(i, 0)));
      Assertions.assertArrayEquals(new int[0], withUnknown.markupOffsets(), "Offset " + i);
    }
  }
}
