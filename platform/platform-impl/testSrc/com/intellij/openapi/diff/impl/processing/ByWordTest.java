package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.highlighting.FragmentEquality;
import com.intellij.openapi.diff.impl.highlighting.FragmentStringConvertion;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Assertion;
import com.intellij.util.StringConvertion;
import com.intellij.util.diff.FilesTooBigForDiffException;
import gnu.trove.Equality;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

public class ByWordTest extends TestCase {
  private final Assertion CHECK = new Assertion(new FragmentStringConvertion());

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CHECK.setEquality(new FragmentEquality());
  }

  public void test1() throws FilesTooBigForDiffException {
    DiffPolicy byWord = new ByWord(ComparisonPolicy.DEFAULT);
    DiffFragment[] fragments = byWord.buildFragments("abc def, 123", "ab def, 12");
    CHECK.compareAll(new DiffFragment[]{new DiffFragment("abc", "ab"),
                                        new DiffFragment(" def, ", " def, "),
                                        new DiffFragment("123", "12")}, fragments);
  }

  public void test2() throws FilesTooBigForDiffException {
    DiffPolicy byWord = new ByWord(ComparisonPolicy.DEFAULT);
    DiffFragment[] fragments = byWord.buildFragments(" a[xy]+1", ",a[]+1");
    CHECK.compareAll(new DiffFragment[]{new DiffFragment(" ", null),
                                        new DiffFragment(null, ","),
                                        new DiffFragment("a[", "a["),
                                        new DiffFragment("xy", null),
                                        new DiffFragment("]+1", "]+1")}, fragments);
  }

  public void test3() throws FilesTooBigForDiffException {
    DiffPolicy byWord = new ByWord(ComparisonPolicy.DEFAULT);
    DiffFragment[] fragments = byWord.buildFragments("0987\n  a.g();\n", "yyyy\n");
    CHECK.compareAll(new DiffFragment[]{new DiffFragment("0987\n  a.g();\n", "yyyy\n")}, fragments);
  }

  public void test4() throws FilesTooBigForDiffException {
    DiffPolicy byWord = new ByWord(ComparisonPolicy.DEFAULT);
    DiffFragment[] fragments = byWord.buildFragments("  abc\n2222\n", "    x = abc\nzzzz\n");
    CHECK.compareAll(new DiffFragment[]{
      new DiffFragment(null, "  "), new DiffFragment("  ", "  "), new DiffFragment(null, "x"), new DiffFragment(null, " ="),
      new DiffFragment(null, " "),
      new DiffFragment("abc\n", "abc\n"),
      new DiffFragment("2222", "zzzz"),
      new DiffFragment("\n", "\n")}, fragments);
  }

  public void testIdea58505() throws FilesTooBigForDiffException {
    DiffPolicy byWord = new ByWord(ComparisonPolicy.DEFAULT);
    DiffFragment[] fragments = byWord.buildFragments("   if (eventMerger!=null && !dataSelection.getValueIsAdjusting()) {",
                                                     "   if (eventMerger!=null && (dataSelection==null || !dataSelection.getValueIsAdjusting())) {");
    CHECK.compareAll(new DiffFragment[] {
      new DiffFragment("   if (eventMerger!=null && ", "   if (eventMerger!=null && "),
      new DiffFragment("!", "("),
      new DiffFragment("dataSelection", "dataSelection"),
      new DiffFragment(null, "=="),
      new DiffFragment(null, "null || !dataSelection"),
      new DiffFragment(".getValueIsAdjusting())", ".getValueIsAdjusting())"),
      new DiffFragment(null, ")"),
      new DiffFragment(" {", " {")
    }, fragments);
  }

  public void testIdea58505Trim() throws FilesTooBigForDiffException {
    DiffPolicy byWord = new ByWord(ComparisonPolicy.TRIM_SPACE);
    DiffFragment[] fragments = byWord.buildFragments("   if (eventMerger!=null && !dataSelection.getValueIsAdjusting()) {",
                                                     "   if (eventMerger!=null && (dataSelection==null || !dataSelection.getValueIsAdjusting())) {");
    CHECK.compareAll(new DiffFragment[] {
      new DiffFragment("   if (eventMerger!=null && ", "   if (eventMerger!=null && "),
      new DiffFragment("!", "("),
      new DiffFragment("dataSelection", "dataSelection"),
      new DiffFragment(null, "=="),
      new DiffFragment(null, "null || !dataSelection"),
      new DiffFragment(".getValueIsAdjusting())", ".getValueIsAdjusting())"),
      new DiffFragment(null, ")"),
      new DiffFragment(" {", " {")
    }, fragments);
  }

  public void testIdea56428() throws FilesTooBigForDiffException {
    DiffPolicy byWord = new ByWord(ComparisonPolicy.DEFAULT);
    DiffFragment[] fragments = byWord.buildFragments("messageInsertStatement = connection.prepareStatement(\"INSERT INTO AUDIT (AUDIT_TYPE_ID, STATUS, SERVER_ID, INSTANCE_ID, REQUEST_ID) VALUES (?, ?, ?, ?, ?)\");\n",
                                                     "messageInsertStatement = connection.prepareStatement(\"INSERT INTO AUDIT (AUDIT_TYPE_ID, CREATION_TIMESTAMP, STATUS, SERVER_ID, INSTANCE_ID, REQUEST_ID) VALUES (?, ?, ?, ?, ?, ?)\");\n");
    CHECK.compareAll(new DiffFragment[] {
      new DiffFragment("messageInsertStatement = connection.prepareStatement(\"INSERT INTO AUDIT (AUDIT_TYPE_ID, ", "messageInsertStatement = connection.prepareStatement(\"INSERT INTO AUDIT (AUDIT_TYPE_ID, "),
      new DiffFragment(null, "CREATION_TIMESTAMP"),
      new DiffFragment(null, ","),
      new DiffFragment(null, " "),
      new DiffFragment("STATUS, SERVER_ID, INSTANCE_ID, REQUEST_ID) VALUES (?, ?, ?, ?, ?", "STATUS, SERVER_ID, INSTANCE_ID, REQUEST_ID) VALUES (?, ?, ?, ?, ?"),
      new DiffFragment(null, ", ?"),
      new DiffFragment(")\");\n", ")\");\n"),
    }, fragments);
  }

  public void testExtractWords() {
    String text = "a b, c.d\n\n  x\n y";
    Word[] words = ByWord.buildWords(text, ComparisonPolicy.DEFAULT);
    CHECK.setEquality(new Equality() {
      @Override
      public boolean equals(Object o1, Object o2) {
        Word word1 = (Word)o1;
        Word word2 = (Word)o2;
        return word1.getStart() == word2.getStart() && word1.getEnd() == word2.getEnd();
      }
    });
    CHECK.setStringConvertion(StringConvertion.DEFAULT);
    CHECK.compareAll(new Word[]{new Formatting(text, new TextRange(0, 0)),
                                new Word(text, new TextRange(0, 1)),
                                new Word(text, new TextRange(2, 3)),
                                new Word(text, new TextRange(5, 6)),
                                new Word(text, new TextRange(7, 8)),
                                new Formatting(text, new TextRange(8, 12)),
                                new Word(text, new TextRange(12, 13)),
                                new Formatting(text, new TextRange(13, 15)),
                                new Word(text, new TextRange(15, 16))}, words);
    text = " b c";
    words = ByWord.buildWords(text, ComparisonPolicy.DEFAULT);
    CHECK.compareAll(new Word[]{new Formatting(text, new TextRange(0, 1)),
                                new Word(text, new TextRange(1, 2)),
                                new Word(text, new TextRange(3, 4))}, words);
  }

  public void testLeadingFormatting() throws FilesTooBigForDiffException {
    DiffPolicy byWord = new ByWord(ComparisonPolicy.DEFAULT);
    DiffFragment[] fragments = byWord.buildFragments(" abc\n 123", " 123");
    CHECK.compareAll(new DiffFragment[]{new DiffFragment(" abc\n", null),
                                        new DiffFragment(" 123", " 123")},
                     UniteSameType.INSTANCE.correct(fragments));
  }

  public void testRestyleNewLines() throws FilesTooBigForDiffException {
    DiffPolicy byWord = new ByWord(ComparisonPolicy.DEFAULT);
    DiffFragment[] fragments = byWord.buildFragments("f(a, b);", "f(a,\n  b);");
    CHECK.compareAll(new DiffFragment[]{new DiffFragment("f(a,", "f(a,"),
                                        new DiffFragment(" ", "\n  "),
                                        new DiffFragment("b);", "b);")},
                     UniteSameType.INSTANCE.correct(fragments));
  }

  public void testIgnoreSpaces() throws FilesTooBigForDiffException {
    ByWord byWord = new ByWord(ComparisonPolicy.IGNORE_SPACE);
    DiffFragment[] fragments = byWord.buildFragments(" o.f(a)", "o. f( b)");
    CHECK.compareAll(new DiffFragment[]{DiffFragment.unchanged(" o.f(", "o. f( "),
                                        new DiffFragment("a", "b"),
                                        DiffFragment.unchanged(")", ")")},
                     UniteSameType.INSTANCE.correct(fragments));
  }

  public void testIgnoreLeadingAndTrailing() throws FilesTooBigForDiffException {
    ByWord byWord = new ByWord(ComparisonPolicy.TRIM_SPACE);
    checkEqual(byWord.buildFragments(" text", "text"));
    checkEqual(byWord.buildFragments("text ", "text"));
    checkEqual(byWord.buildFragments(" text \n", "text\n"));
    //DiffFragment[] fragments = byWord.buildFragments(" 123 ", "xyz");
    //CHECK.compareAll(new DiffFragment[]{DiffFragment.unchanged(" ", ""),
    //                                    new DiffFragment("123", "xyz"),
    //                                    DiffFragment.unchanged(" ", "")},
    //                 fragments);
  }

  private void checkEqual(DiffFragment[] fragments) {
    try {
      assertEquals(1, fragments.length);
      assertTrue(fragments[0].isEqual());
    } catch(AssertionFailedError e) {
      CHECK.enumerate(fragments);
      throw e;
    }
  }
}
