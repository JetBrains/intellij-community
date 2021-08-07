package com.intellij.grazie.text;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import one.util.streamex.IntStreamEx;

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

  public void testJoinWithWhitespace() {
    var file = myFixture.configureByText("a.txt", "abbbc");
    var f1 = psiFragment(file, 0, 1);
    var f2 = psiFragment(file, 4, 5).markUnknown(new TextRange(1, 1));

    var joined = TextContent.joinWithWhitespace(List.of(f1, f2));
    assertNotNull(joined);
    assertEquals("a c|", unknownOffsets(joined));
    assertEquals(List.of(0, 1, 4, 5), IntStreamEx.range(4).mapToObj(joined::textOffsetToFile).toList());

    assertEquals("a |", unknownOffsets(joined.markUnknown(new TextRange(2, 3))));
    assertEquals("a |", unknownOffsets(joined.excludeRange(new TextRange(2, 3))));

    assertEquals("a|c|", unknownOffsets(joined.markUnknown(new TextRange(1, 2))));
    assertEquals("ac|", unknownOffsets(joined.excludeRange(new TextRange(1, 2))));

    assertEquals("| c|", unknownOffsets(joined.markUnknown(new TextRange(0, 1))));
    assertEquals("c|", unknownOffsets(joined.excludeRange(new TextRange(0, 1))));
  }

  public static String unknownOffsets(TextContent text) {
    StringBuilder sb = new StringBuilder(text);
    for (int i = text.length(); i >= 0; i--) {
      if (text.hasUnknownFragmentsIn(TextRange.from(i, 0))) {
        sb.insert(i, "|");
      }
    }
    return sb.toString();
  }
}
