// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.LineSeparator;
import com.intellij.util.TripleFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jdom.Verifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.Test;

import java.nio.CharBuffer;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import static com.intellij.openapi.util.text.StringUtil.ELLIPSIS;
import static com.intellij.openapi.util.text.StringUtil.removeEllipsisSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

/**
 * @author Eugene Zhuravlev
 */
public class StringUtilTest {
  private final char myDecimalSeparator = new DecimalFormat("0.##").getDecimalFormatSymbols().getDecimalSeparator();

  @Test
  public void testStripHtml() {
    BiConsumer<String, String> sh = (html, stripped) -> assertEquals(stripped, StringUtil.stripHtml(html, "\n"));
    sh.accept("foo<br \n \r>baz", "foo\nbaz");
    sh.accept("foo<br \n \r/>baz", "foo\nbaz");
    sh.accept("foo<br \n \r/ >baz", "foo\nbaz");
    sh.accept("foo<BR \n \r/ >baz", "foo\nbaz");
    sh.accept("foo< \n bar \n  \r >baz", "foobaz");
    sh.accept("foo< \n bar \n  \r />baz", "foobaz");
    sh.accept("foo< \n bar \n  \r / >baz", "foobaz");
  }

  @Test
  public void testTrimLeadingChar() {
    doTestTrimLeading("", "");
    doTestTrimLeading("", " ");
    doTestTrimLeading("", "    ");
    doTestTrimLeading("a  ", "a  ");
    doTestTrimLeading("a  ", "  a  ");
  }

  @Test
  public void testTrimTrailingChar() {
    doTestTrimTrailing("", "");
    doTestTrimTrailing("", " ");
    doTestTrimTrailing("", "    ");
    doTestTrimTrailing("  a", "  a");
    doTestTrimTrailing("  a", "  a  ");
  }

  private static void doTestTrimLeading(@NotNull String expected, @NotNull String string) {
    assertEquals(expected, StringUtil.trimLeading(string));
    assertEquals(expected, StringUtil.trimLeading(string, ' '));
    assertEquals(expected, StringUtil.trimLeading(new StringBuilder(string), ' ').toString());
  }

  private static void doTestTrimTrailing(@NotNull String expected, @NotNull String string) {
    assertEquals(expected, StringUtil.trimTrailing(string));
    assertEquals(expected, StringUtil.trimTrailing(string, ' '));
    assertEquals(expected, StringUtil.trimTrailing(new StringBuilder(string), ' ').toString());
  }

  @Test
  public void doTestTrimCharSequence() {
    assertEquals("", StringUtil.trim((CharSequence)"").toString());
    assertEquals("", StringUtil.trim((CharSequence)" ").toString());
    assertEquals("", StringUtil.trim((CharSequence)" \n\t\r").toString());
    assertEquals("a", StringUtil.trim((CharSequence)"a").toString());
    assertEquals("a", StringUtil.trim((CharSequence)" a").toString());
    assertEquals("bc", StringUtil.trim((CharSequence)"bc ").toString());
    assertEquals("b a c", StringUtil.trim((CharSequence)" b a c   ").toString());
  }

  @Test
  public void testToUpperCase() {
    assertEquals('/', StringUtil.toUpperCase('/'));
    assertEquals(':', StringUtil.toUpperCase(':'));
    assertEquals('A', StringUtil.toUpperCase('a'));
    assertEquals('A', StringUtil.toUpperCase('A'));
    assertEquals('K', StringUtil.toUpperCase('k'));
    assertEquals('K', StringUtil.toUpperCase('K'));

    assertEquals('\u2567', StringUtil.toUpperCase(Character.toLowerCase('\u2567')));
  }

  @Test
  public void testToUpperCaseGeneric() {
    for (char ch = 0; ch < Character.MAX_VALUE; ch++) {
      char upperCaseCh = Character.toUpperCase(ch);
      assertEquals(
        "Optimized StringUtil.toUpperCase(" + ch + ") must be == Character.toUpperCase(ch)[=" + upperCaseCh + "]",
        upperCaseCh,
        StringUtil.toUpperCase(ch)
      );
    }
  }

  @Test
  public void testToLowerCase() {
    assertEquals('/', StringUtil.toLowerCase('/'));
    assertEquals(':', StringUtil.toLowerCase(':'));
    assertEquals('a', StringUtil.toLowerCase('a'));
    assertEquals('a', StringUtil.toLowerCase('A'));
    assertEquals('k', StringUtil.toLowerCase('k'));
    assertEquals('k', StringUtil.toLowerCase('K'));

    assertEquals('\u2567', StringUtil.toUpperCase(Character.toLowerCase('\u2567')));
  }

  @Test
  public void testToLowerCaseGeneric() {
    for (char ch = 0; ch < Character.MAX_VALUE; ch++) {
      char lowerCaseCh = Character.toLowerCase(ch);
      assertEquals(
        "Optimized StringUtil.toLowerCase(" + ch + ") must be == Character.toLowerCase(ch)[=" + lowerCaseCh + "]",
        lowerCaseCh,
        StringUtil.toLowerCase(ch)
      );
    }
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void testIsEmptyOrSpaces() {
    assertTrue(StringUtil.isEmptyOrSpaces(null));
    assertTrue(StringUtil.isEmptyOrSpaces(""));
    assertTrue(StringUtil.isEmptyOrSpaces("                   "));

    assertFalse(StringUtil.isEmptyOrSpaces("1"));
    assertFalse(StringUtil.isEmptyOrSpaces("         12345          "));
    assertFalse(StringUtil.isEmptyOrSpaces("test"));
  }

  @Test
  public void testSplitWithQuotes() {
    // Merge separators
    UsefulTestCase.assertSameElements(
      StringUtil.splitHonorQuotes("aaa bbb   ccc ", ' '),
      "aaa", "bbb", "ccc"
    );
    // Support different quotes
    UsefulTestCase.assertSameElements(
      StringUtil.splitHonorQuotes("'aaa' \"bbb\"", ' '),
      "'aaa'", "\"bbb\""
    );
    // Ignore separators inside quotes
    UsefulTestCase.assertSameElements(
      StringUtil.splitHonorQuotes("'a aa' \"bb b\"", ' '),
      "'a aa'", "\"bb b\""
    );
    // Ignore other quotes inside quotes
    UsefulTestCase.assertSameElements(
      StringUtil.splitHonorQuotes("'a\" aa' \"bb 'b\"", ' '),
      "'a\" aa'", "\"bb 'b\""
    );
    // Escape quotes
    UsefulTestCase.assertSameElements(
      StringUtil.splitHonorQuotes("'a \\'aa' \"bb\\\" b\"", ' '),
      "'a \\'aa'", "\"bb\\\" b\""
    );
    // Unescape escaped quotes
    UsefulTestCase.assertSameElements(
      StringUtil.splitHonorQuotes("'a aa\\\\' \"bb b\\\\\"", ' '),
      "'a aa\\\\'", "\"bb b\\\\\""
    );
    // Escape unescaped quotes
    UsefulTestCase.assertSameElements(
      StringUtil.splitHonorQuotes("'a \\\\\\'aa' \"bb \\\\\\\"b\"", ' '),
      "'a \\\\\\'aa'", "\"bb \\\\\\\"b\""
    );
  }

  @Test
  @SuppressWarnings("SpellCheckingInspection")
  public void testUnPluralize() {
    // synthetic
    assertEquals("plurals", StringUtil.unpluralize("pluralses"));
    assertEquals("Inherits", StringUtil.unpluralize("Inheritses"));
    assertEquals("s", StringUtil.unpluralize("ss"));
    assertEquals("I", StringUtil.unpluralize("Is"));
    assertNull(StringUtil.unpluralize("s"));
    assertEquals("z", StringUtil.unpluralize("zs"));
    // normal
    assertEquals("case", StringUtil.unpluralize("cases"));
    assertEquals("Index", StringUtil.unpluralize("Indices"));
    assertEquals("fix", StringUtil.unpluralize("fixes"));
    assertEquals("man", StringUtil.unpluralize("men"));
    assertEquals("leaf", StringUtil.unpluralize("leaves"));
    assertEquals("cookie", StringUtil.unpluralize("cookies"));
    assertEquals("search", StringUtil.unpluralize("searches"));
    assertEquals("process", StringUtil.unpluralize("process"));
    assertEquals("PROPERTY", StringUtil.unpluralize("PROPERTIES"));
    assertEquals("THIS", StringUtil.unpluralize("THESE"));
    assertEquals("database", StringUtil.unpluralize("databases"));
    assertEquals("basis", StringUtil.unpluralize("bases"));
  }

  @Test
  public void testPluralize() {
    assertEquals("values", StringUtil.pluralize("value"));
    assertEquals("values", StringUtil.pluralize("values"));
    assertEquals("indices", StringUtil.pluralize("index"));
    assertEquals("matrices", StringUtil.pluralize("matrix"));
    assertEquals("fixes", StringUtil.pluralize("fix"));
    assertEquals("men", StringUtil.pluralize("man"));
    assertEquals("media", StringUtil.pluralize("medium"));
    assertEquals("stashes", StringUtil.pluralize("stash"));
    assertEquals("children", StringUtil.pluralize("child"));
    assertEquals("leaves", StringUtil.pluralize("leaf"));
    assertEquals("These", StringUtil.pluralize("This"));
    assertEquals("cookies", StringUtil.pluralize("cookie"));
    assertEquals("VaLuES", StringUtil.pluralize("VaLuE"));
    assertEquals("PLANS", StringUtil.pluralize("PLAN"));
    assertEquals("stackTraceLineExes", StringUtil.pluralize("stackTraceLineEx"));
    assertEquals("schemas", StringUtil.pluralize("schema")); // anglicized version
    assertEquals("PROPERTIES", StringUtil.pluralize("PROPERTY"));
    assertEquals("THESE", StringUtil.pluralize("THIS"));
    assertEquals("databases", StringUtil.pluralize("database"));
    assertEquals("bases", StringUtil.pluralize("base"));
    assertEquals("bases", StringUtil.pluralize("basis"));
  }

  @Test
  public void testStartsWithConcatenation() {
    assertTrue(StringUtil.startsWithConcatenation("something.with.dot", "something", "."));
    assertTrue(StringUtil.startsWithConcatenation("something.with.dot", "", "something."));
    assertTrue(StringUtil.startsWithConcatenation("something.", "something", "."));
    assertTrue(StringUtil.startsWithConcatenation("something", "something", "", "", ""));
    assertFalse(StringUtil.startsWithConcatenation("something", "something", "", "", "."));
    assertFalse(StringUtil.startsWithConcatenation("some", "something", ""));
  }

  @Test
  public void testNaturalCompareTransitivity() {
    String s1 = "#";
    String s2 = "0b";
    String s3 = " 0b";
    assertTrue(StringUtil.naturalCompare(s1, s2) < 0);
    assertTrue(StringUtil.naturalCompare(s2, s3) < 0);
    assertTrue("non-transitive", StringUtil.naturalCompare(s1, s3) < 0);
  }

  @Test
  public void testNaturalCompareTransitivityProperty() {
    PropertyChecker.forAll(Generator.listsOf(Generator.stringsOf("ab01()_# ")), l -> {
      List<String> sorted = ContainerUtil.sorted(l, StringUtil::naturalCompare);
      for (int i = 0; i < sorted.size(); i++) {
        for (int j = i + 1; j < sorted.size(); j++) {
          if (StringUtil.naturalCompare(sorted.get(i), sorted.get(j)) > 0) return false;
          if (StringUtil.naturalCompare(sorted.get(j), sorted.get(i)) < 0) return false;
        }
      }
      return true;
    });
  }

  @Test
  public void testNaturalCompareStability() {
    assertTrue(StringUtil.naturalCompare("01a1", "1a01") != StringUtil.naturalCompare("1a01", "01a1"));
    assertTrue(StringUtil.naturalCompare("#01A", "# 1A") != StringUtil.naturalCompare("# 1A", "#01A"));
    assertTrue(StringUtil.naturalCompare("aA", "aa") != StringUtil.naturalCompare("aa", "aA"));
  }

  @Test
  public void testNaturalCompare() {
    var numbers = Arrays.asList("1a000001", "000001a1", "001a0001", "0001A001", "00001a01", "01a00001");
    numbers.sort(NaturalComparator.INSTANCE);
    assertThat(numbers).containsExactly("1a000001", "01a00001", "001a0001", "0001A001", "00001a01", "000001a1");

    var test = Arrays.asList("test011", "test10", "test10a", "test010");
    test.sort(NaturalComparator.INSTANCE);
    assertThat(test).containsExactly("test10", "test10a", "test010", "test011");

    var strings = Arrays.asList("Test99", "tes0", "test0", "testing", "test", "test99", "test011", "test1", "test 3", "test2",
                                "test10a", "test10", "1.2.10.5", "1.2.9.1");
    strings.sort(NaturalComparator.INSTANCE);
    assertThat(strings).containsExactly("1.2.9.1", "1.2.10.5", "tes0", "test", "test0", "test1", "test2", "test 3", "test10", "test10a",
                                        "test011", "Test99", "test99", "testing");

    var strings2 = Arrays.asList("t1", "t001", "T2", "T002", "T1", "t2");
    strings2.sort(NaturalComparator.INSTANCE);
    assertThat(strings2).containsExactly("T1", "t1", "t001", "T2", "t2", "T002");

    assertThat(StringUtil.naturalCompare("7403515080361171695", "07403515080361171694")).isPositive();
    assertThat(StringUtil.naturalCompare("_firstField", "myField1")).isNegative();

    var strings3 = Arrays.asList("C148A_InsomniaCure", "C148B_Escape", "C148C_TersePrincess", "C148D_BagOfMice", "C148E_Porcelain");
    strings3.sort(NaturalComparator.INSTANCE);
    assertThat(strings3).containsExactly("C148A_InsomniaCure", "C148B_Escape", "C148C_TersePrincess", "C148D_BagOfMice", "C148E_Porcelain");

    var l = Arrays.asList("a0002", "a0 2", "a001");
    l.sort(NaturalComparator.INSTANCE);
    assertThat(l).containsExactly("a0 2", "a001", "a0002");
  }

  @Test
  public void testFormatLinks() {
    assertEquals("<a href=\"http://a-b+c\">http://a-b+c</a>", StringUtil.formatLinks("http://a-b+c"));
  }

  @Test
  public void testCopyHeapCharBuffer() {
    String s = "abc.d";
    CharBuffer buffer = CharBuffer.allocate(s.length());
    buffer.append(s);
    buffer.rewind();

    assertNotNull(CharArrayUtil.fromSequenceWithoutCopying(buffer));
    assertNotNull(CharArrayUtil.fromSequenceWithoutCopying(buffer.subSequence(0, 5)));
    assertNull(CharArrayUtil.fromSequenceWithoutCopying(buffer.subSequence(1, 5)));
    assertNull(CharArrayUtil.fromSequenceWithoutCopying(buffer.subSequence(1, 2)));
  }

  @Test
  public void testTitleCase() {
    assertEquals("Couldn't Connect to Debugger", StringUtil.wordsToBeginFromUpperCase("Couldn't connect to debugger"));
    assertEquals("Let's Make Abbreviations Like I18n, SQL and CSS",
                 StringUtil.wordsToBeginFromUpperCase("Let's make abbreviations like I18n, SQL and CSS"));
    assertEquals("1s_t _How A&re Mn_emonics &Handled, or Aren't They",
                 StringUtil.wordsToBeginFromUpperCase("1s_t _how a&re mn_emonics &handled, or aren't they"));
    assertEquals("A Good Steak Should Not Be This Hard to Come By",
                 StringUtil.wordsToBeginFromUpperCase("a good steak should not be this hard to come by"));
    assertEquals("Twenty-One Quick-Fixes", StringUtil.wordsToBeginFromUpperCase("twenty-one quick-fixes"));
    assertEquals("It's Not a Question of If, but When",
                 StringUtil.wordsToBeginFromUpperCase("it's not a question of if, but when"));
    assertEquals("Scroll to the End. A Good Steak Should Not Be This Hard to Come By.",
                 StringUtil.wordsToBeginFromUpperCase("scroll to the end. a good steak should not be this hard to come by."));
  }

  @Test
  public void testSentenceCapitalization() {
    assertEquals("couldn't connect to debugger", StringUtil.wordsToBeginFromLowerCase("Couldn't Connect To Debugger"));
    assertEquals("let's make abbreviations like I18n, SQL and CSS s SQ sq",
                 StringUtil.wordsToBeginFromLowerCase("Let's Make Abbreviations Like I18n, SQL and CSS S SQ Sq"));
  }

  @Test
  public void testCapitalizeWords() {
    assertEquals("AspectJ (Syntax Highlighting Only)", StringUtil.capitalizeWords("AspectJ (syntax highlighting only)", true));
  }

  @Test
  public void testEscapeStringCharacters() {
    assertEquals("\\\"\\n", StringUtil.escapeStringCharacters(3, "\\\"\n", "\"", false, new StringBuilder()).toString());
    assertEquals("\\\"\\n", StringUtil.escapeStringCharacters(2, "\"\n", "\"", false, new StringBuilder()).toString());
    assertEquals("\\\\\\\"\\n", StringUtil.escapeStringCharacters(3, "\\\"\n", "\"", true, new StringBuilder()).toString());
  }

  @Test
  public void testEscapeSlashes() {
    assertEquals("\\/", StringUtil.escapeSlashes("/"));
    assertEquals("foo\\/bar\\foo\\/", StringUtil.escapeSlashes("foo/bar\\foo/"));

    assertEquals("\\\\\\\\server\\\\share\\\\extension.crx", StringUtil.escapeBackSlashes("\\\\server\\share\\extension.crx"));
  }

  @Test
  public void testEscapeQuotes() {
    assertEquals("\\\"", StringUtil.escapeQuotes("\""));
    assertEquals("foo\\\"bar'\\\"", StringUtil.escapeQuotes("foo\"bar'\""));
  }

  @Test
  public void testUnquote() {
    assertEquals("", StringUtil.unquoteString(""));
    assertEquals("\"", StringUtil.unquoteString("\""));
    assertEquals("", StringUtil.unquoteString("\"\""));
    assertEquals("\"", StringUtil.unquoteString("\"\"\""));
    assertEquals("foo", StringUtil.unquoteString("\"foo\""));
    assertEquals("\"foo", StringUtil.unquoteString("\"foo"));
    assertEquals("foo\"", StringUtil.unquoteString("foo\""));
    assertEquals("", StringUtil.unquoteString(""));
    assertEquals("'", StringUtil.unquoteString("'"));
    assertEquals("", StringUtil.unquoteString("''"));
    assertEquals("'", StringUtil.unquoteString("'''"));
    assertEquals("foo", StringUtil.unquoteString("'foo'"));
    assertEquals("'foo", StringUtil.unquoteString("'foo"));
    assertEquals("foo'", StringUtil.unquoteString("foo'"));

    assertEquals("'\"", StringUtil.unquoteString("'\""));
    assertEquals("\"'", StringUtil.unquoteString("\"'"));
    assertEquals("\"foo'", StringUtil.unquoteString("\"foo'"));
  }

  @SuppressWarnings("SSBasedInspection")
  @Test
  public void testStripQuotesAroundValue() {
    assertEquals("", StringUtil.stripQuotesAroundValue(""));
    assertEquals("", StringUtil.stripQuotesAroundValue("'"));
    assertEquals("", StringUtil.stripQuotesAroundValue("\""));
    assertEquals("", StringUtil.stripQuotesAroundValue("''"));
    assertEquals("", StringUtil.stripQuotesAroundValue("\"\""));
    assertEquals("", StringUtil.stripQuotesAroundValue("'\""));
    assertEquals("foo", StringUtil.stripQuotesAroundValue("'foo'"));
    assertEquals("foo", StringUtil.stripQuotesAroundValue("'foo"));
    assertEquals("foo", StringUtil.stripQuotesAroundValue("foo'"));
    assertEquals("f'o'o", StringUtil.stripQuotesAroundValue("'f'o'o'"));
    assertEquals("f\"o'o", StringUtil.stripQuotesAroundValue("\"f\"o'o'"));
    assertEquals("f\"o'o", StringUtil.stripQuotesAroundValue("f\"o'o"));
    assertEquals("\"'f\"o'o\"", StringUtil.stripQuotesAroundValue("\"\"'f\"o'o\"\""));
    assertEquals("''f\"o'o''", StringUtil.stripQuotesAroundValue("'''f\"o'o'''"));
    assertEquals("foo' 'bar", StringUtil.stripQuotesAroundValue("foo' 'bar"));
  }

  @Test
  public void testUnquoteWithQuotationChar() {
    assertEquals("", StringUtil.unquoteString("", '|'));
    assertEquals("|", StringUtil.unquoteString("|", '|'));
    assertEquals("", StringUtil.unquoteString("||", '|'));
    assertEquals("|", StringUtil.unquoteString("|||", '|'));
    assertEquals("foo", StringUtil.unquoteString("|foo|", '|'));
    assertEquals("|foo", StringUtil.unquoteString("|foo", '|'));
    assertEquals("foo|", StringUtil.unquoteString("foo|", '|'));
  }

  @Test
  public void testIsQuotedString() {
    assertFalse(StringUtil.isQuotedString(""));
    assertFalse(StringUtil.isQuotedString("'"));
    assertFalse(StringUtil.isQuotedString("\""));
    assertTrue(StringUtil.isQuotedString("\"\""));
    assertTrue(StringUtil.isQuotedString("''"));
    assertTrue(StringUtil.isQuotedString("'ab'"));
    assertTrue(StringUtil.isQuotedString("\"foo\""));
  }

  @Test
  public void testJoin() {
    assertEquals("", StringUtil.join(List.of(), ","));
    assertEquals("qqq", StringUtil.join(List.of("qqq"), ","));
    assertEquals("", StringUtil.join(Collections.singletonList(null), ","));
    assertEquals("a,b", StringUtil.join(List.of("a", "b"), ","));
    assertEquals("foo,,bar", StringUtil.join(List.of("foo", "", "bar"), ","));
    assertEquals("foo,,bar", StringUtil.join(new String[]{"foo", "", "bar"}, ","));
  }

  @Test
  public void testSplitByLineKeepingSeparators() {
    assertThat(StringUtil.splitByLinesKeepSeparators("")).containsExactly("");
    assertThat(StringUtil.splitByLinesKeepSeparators("aa")).containsExactly("aa");
    assertThat(StringUtil.splitByLinesKeepSeparators("\n\naa\n\nbb\ncc\n\n")).containsExactly("\n", "\n", "aa\n", "\n", "bb\n", "cc\n",
                                                                                              "\n");

    assertThat(StringUtil.splitByLinesKeepSeparators("\r\r\n\r")).containsExactly("\r", "\r\n", "\r");
    assertThat(StringUtil.splitByLinesKeepSeparators("\r\n\r\r\n")).containsExactly("\r\n", "\r", "\r\n");

    assertThat(StringUtil.splitByLinesKeepSeparators("\n\r\n\n\r\n\r\raa\rbb\r\ncc\n\rdd\n\n\r\n\r"))
      .containsExactly("\n", "\r\n", "\n", "\r\n", "\r", "\r", "aa\r", "bb\r\n", "cc\n", "\r", "dd\n", "\n", "\r\n", "\r");
  }

  @Test
  @SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
  public void testReplaceReturnReplacementIfTextEqualsToReplacedText() {
    String str = "/tmp";
    assertSame(str,
               StringUtil.replace("$PROJECT_FILE$", "$PROJECT_FILE$".toLowerCase().toUpperCase() /* ensure new String instance */, str));
  }

  @Test
  public void testReplace() {
    assertEquals("/tmp/filename", StringUtil.replace("$PROJECT_FILE$/filename", "$PROJECT_FILE$", "/tmp"));
  }

  @Test
  public void testReplaceListOfChars() {
    assertEquals("/tmp/filename",
                 StringUtil.replace("$PROJECT_FILE$/filename", List.of("$PROJECT_FILE$"), List.of("/tmp")));
    assertEquals("/someTextBefore/tmp/filename",
                 StringUtil.replace("/someTextBefore/$PROJECT_FILE$/filename", List.of("$PROJECT_FILE$"), List.of("tmp")));
  }

  @Test
  public void testReplaceReturnTheSameStringIfNothingToReplace() {
    String str = "/tmp/filename";
    assertSame(str, StringUtil.replace(str, "$PROJECT_FILE$/filename", "$PROJECT_FILE$"));
  }

  @Test
  public void testEqualsIgnoreWhitespaces() {
    assertTrue(StringUtil.equalsIgnoreWhitespaces(null, null));
    assertFalse(StringUtil.equalsIgnoreWhitespaces("", null));

    assertTrue(StringUtil.equalsIgnoreWhitespaces("", ""));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("\n\t ", ""));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("", "\t\n \n\t"));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("\t", "\n"));

    assertTrue(StringUtil.equalsIgnoreWhitespaces("x", " x"));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("x", "x "));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("x\n", "x"));

    assertTrue(StringUtil.equalsIgnoreWhitespaces("abc", "a\nb\nc\n"));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("x y x", "x y x"));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("xyx", "x y x"));

    assertFalse(StringUtil.equalsIgnoreWhitespaces("x", "\t\n "));
    assertFalse(StringUtil.equalsIgnoreWhitespaces("", " x "));
    assertFalse(StringUtil.equalsIgnoreWhitespaces("", "x "));
    assertFalse(StringUtil.equalsIgnoreWhitespaces("", " x"));
    assertFalse(StringUtil.equalsIgnoreWhitespaces("xyx", "xxx"));
    assertFalse(StringUtil.equalsIgnoreWhitespaces("xyx", "xYx"));
  }

  @Test
  public void testStringHashCodeIgnoreWhitespaces() {
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces(""), StringUtil.stringHashCodeIgnoreWhitespaces("")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("\n\t "), StringUtil.stringHashCodeIgnoreWhitespaces("")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces(""), StringUtil.stringHashCodeIgnoreWhitespaces("\t\n \n\t")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("\t"), StringUtil.stringHashCodeIgnoreWhitespaces("\n")));

    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("x"), StringUtil.stringHashCodeIgnoreWhitespaces(" x")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("x"), StringUtil.stringHashCodeIgnoreWhitespaces("x ")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("x\n"), StringUtil.stringHashCodeIgnoreWhitespaces("x")));

    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("abc"), StringUtil.stringHashCodeIgnoreWhitespaces("a\nb\nc\n")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("x y x"), StringUtil.stringHashCodeIgnoreWhitespaces("x y x")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("xyx"), StringUtil.stringHashCodeIgnoreWhitespaces("x y x")));

    assertFalse(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("x"), StringUtil.stringHashCodeIgnoreWhitespaces("\t\n ")));
    assertFalse(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces(""), StringUtil.stringHashCodeIgnoreWhitespaces(" x ")));
    assertFalse(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces(""), StringUtil.stringHashCodeIgnoreWhitespaces("x ")));
    assertFalse(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces(""), StringUtil.stringHashCodeIgnoreWhitespaces(" x")));
    assertFalse(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("xyx"), StringUtil.stringHashCodeIgnoreWhitespaces("xxx")));
    assertFalse(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("xyx"), StringUtil.stringHashCodeIgnoreWhitespaces("xYx")));
  }

  @Test
  public void testContains() {
    assertTrue(StringUtil.contains("1", "1"));
    assertFalse(StringUtil.contains("1", "12"));
    assertTrue(StringUtil.contains("12", "1"));
    assertTrue(StringUtil.contains("12", "2"));
  }

  @Test
  public void testCompareCharSequence() {
    TripleFunction<CharSequence, CharSequence, Boolean, Boolean> assertPrecedence =
      (lesser, greater, ignoreCase) -> {
        assertTrue(StringUtil.compare(lesser, greater, ignoreCase) < 0);
        assertTrue(StringUtil.compare(greater, lesser, ignoreCase) > 0);
        return true;
      };
    TripleFunction<CharSequence, CharSequence, Boolean, Boolean> assertEquality =
      (lesser, greater, ignoreCase) -> {
        assertEquals(0, StringUtil.compare(lesser, greater, ignoreCase));
        assertEquals(0, StringUtil.compare(greater, lesser, ignoreCase));
        return true;
      };

    assertPrecedence.fun("A", "b", true);
    assertPrecedence.fun("a", "aa", true);
    assertPrecedence.fun("abb", "abC", true);

    assertPrecedence.fun("A", "a", false);
    assertPrecedence.fun("Aa", "a", false);
    assertPrecedence.fun("a", "aa", false);
    assertPrecedence.fun("-", "A", false);

    assertEquality.fun("a", "A", true);
    assertEquality.fun("aa12b", "Aa12B", true);

    assertEquality.fun("aa12b", "aa12b", false);
  }

  @Test
  public void testDetectSeparators() {
    assertNull(StringUtil.detectSeparators(""));
    assertNull(StringUtil.detectSeparators("asd"));
    assertNull(StringUtil.detectSeparators("asd\t"));

    assertEquals(LineSeparator.LF, StringUtil.detectSeparators("asd\n"));
    assertEquals(LineSeparator.LF, StringUtil.detectSeparators("asd\nads\r"));
    assertEquals(LineSeparator.LF, StringUtil.detectSeparators("asd\nads\n"));

    assertEquals(LineSeparator.CR, StringUtil.detectSeparators("asd\r"));
    assertEquals(LineSeparator.CR, StringUtil.detectSeparators("asd\rads\r"));
    assertEquals(LineSeparator.CR, StringUtil.detectSeparators("asd\rads\n"));

    assertEquals(LineSeparator.CRLF, StringUtil.detectSeparators("asd\r\n"));
    assertEquals(LineSeparator.CRLF, StringUtil.detectSeparators("asd\r\nads\r"));
    assertEquals(LineSeparator.CRLF, StringUtil.detectSeparators("asd\r\nads\n"));
  }

  @Test
  public void testFindStartingLineSeparator() {
    assertNull(StringUtil.getLineSeparatorAt("", -1));
    assertNull(StringUtil.getLineSeparatorAt("", 0));
    assertNull(StringUtil.getLineSeparatorAt("", 1));
    assertNull(StringUtil.getLineSeparatorAt("\nHello", -1));
    assertNull(StringUtil.getLineSeparatorAt("\nHello", 1));
    assertNull(StringUtil.getLineSeparatorAt("\nH\rel\nlo", 6));

    assertEquals(LineSeparator.LF, StringUtil.getLineSeparatorAt("\nHello", 0));
    assertEquals(LineSeparator.LF, StringUtil.getLineSeparatorAt("\nH\rel\nlo", 5));
    assertEquals(LineSeparator.LF, StringUtil.getLineSeparatorAt("Hello\n", 5));

    assertEquals(LineSeparator.CR, StringUtil.getLineSeparatorAt("\rH\r\nelp", 0));
    assertEquals(LineSeparator.CR, StringUtil.getLineSeparatorAt("Hello\r", 5));
    assertEquals(LineSeparator.CR, StringUtil.getLineSeparatorAt("Hello\b\r", 6));

    assertEquals(LineSeparator.CRLF, StringUtil.getLineSeparatorAt("\rH\r\nelp", 2));
    assertEquals(LineSeparator.CRLF, StringUtil.getLineSeparatorAt("\r\nH\r\nelp", 0));
    assertEquals(LineSeparator.CRLF, StringUtil.getLineSeparatorAt("\r\nH\r\nelp\r\n", 8));
  }

  @Test
  public void testFormatFileSize() {
    assertFileSizeFormat("0 B", 0);
    assertFileSizeFormat("1 B", 1);
    assertFileSizeFormat("2.15 GB", Integer.MAX_VALUE);
    assertFileSizeFormat("9.22 EB", Long.MAX_VALUE);

    assertFileSizeFormat("60.1 kB", 60_100);

    assertFileSizeFormat("1.23 kB", 1_234);
    assertFileSizeFormat("12.35 kB", 12_345);
    assertFileSizeFormat("123.46 kB", 123_456);
    assertFileSizeFormat("1.23 MB", 1_234_567);
    assertFileSizeFormat("12.35 MB", 12_345_678);
    assertFileSizeFormat("123.46 MB", 123_456_789);
    assertFileSizeFormat("1.23 GB", 1_234_567_890);

    assertFileSizeFormat("999 B", 999);
    assertFileSizeFormat("1 kB", 1000);
    assertFileSizeFormat("999.99 kB", 999_994);
    assertFileSizeFormat("1 MB", 999_995);
    assertFileSizeFormat("999.99 MB", 999_994_999);
    assertFileSizeFormat("1 GB", 999_995_000);
    assertFileSizeFormat("999.99 GB", 999_994_999_999L);
    assertFileSizeFormat("1 TB", 999_995_000_000L);
  }

  @Test
  public void testFormatFileSizeFixedPrecision() {
    assertEquals("10.00 B", StringUtil.formatFileSize(10, " ", -1, true));
    assertEquals("100.00 B", StringUtil.formatFileSize(100, " ", -1, true));
    assertEquals("1.00 kB", StringUtil.formatFileSize(1_000, " ", -1, true));
    assertEquals("10.00 kB", StringUtil.formatFileSize(10_000, " ", -1, true));
    assertEquals("100.00 kB", StringUtil.formatFileSize(100_000, " ", -1, true));
    assertEquals("1.00 MB", StringUtil.formatFileSize(1_000_000, " ", -1, true));
    assertEquals("10.00 MB", StringUtil.formatFileSize(10_000_000, " ", -1, true));
    assertEquals("100.00 MB", StringUtil.formatFileSize(100_000_000, " ", -1, true));
    assertEquals("1.00 GB", StringUtil.formatFileSize(1_000_000_000, " ", -1, true));
  }

  private void assertFileSizeFormat(String expectedFormatted, long sizeBytes) {
    assertEquals(expectedFormatted.replace('.', myDecimalSeparator), StringUtil.formatFileSize(sizeBytes));
  }

  @Test
  public void testFormatDuration() {
    assertEquals("0 ms", StringUtil.formatDuration(0));
    assertEquals("1 ms", StringUtil.formatDuration(1));
    assertEquals("1 s", StringUtil.formatDuration(1000));
    assertEquals("24 d 20 h 31 m 23 s 647 ms", StringUtil.formatDuration(Integer.MAX_VALUE));
    assertEquals("82 d 17 h 24 m 43 s 647 ms", StringUtil.formatDuration(Integer.MAX_VALUE + 5000000000L));

    assertEquals("1 m 0 s 100 ms", StringUtil.formatDuration(60100));

    assertEquals("1 s 234 ms", StringUtil.formatDuration(1234));
    assertEquals("12 s 345 ms", StringUtil.formatDuration(12345));
    assertEquals("2 m 3 s 456 ms", StringUtil.formatDuration(123456));
    assertEquals("20 m 34 s 567 ms", StringUtil.formatDuration(1234567));
    assertEquals("3 h 25 m 45 s 678 ms", StringUtil.formatDuration(12345678));
    assertEquals("1 d 10 h 17 m 36 s 789 ms", StringUtil.formatDuration(123456789));
    assertEquals("14 d 6 h 56 m 7 s 890 ms", StringUtil.formatDuration(1234567890));

    assertEquals("39 d 2 h 30 m 6 s 101 ms", StringUtil.formatDuration(3378606101L));
  }

  @Test
  public void testXmlWrapInCDATA() {
    assertEquals("<![CDATA[abc]]>", XmlStringUtil.wrapInCDATA("abc"));
    assertEquals("<![CDATA[abc]]]><![CDATA[]>]]>", XmlStringUtil.wrapInCDATA("abc]]>"));
    assertEquals("<![CDATA[abc]]]><![CDATA[]>def]]>", XmlStringUtil.wrapInCDATA("abc]]>def"));
    assertEquals("<![CDATA[123<![CDATA[wow<&>]]]><![CDATA[]>]]]><![CDATA[]><![CDATA[123]]>",
                 XmlStringUtil.wrapInCDATA("123<![CDATA[wow<&>]]>]]><![CDATA[123"));
  }

  @Test
  public void testGetPackageName() {
    assertEquals("java.lang", StringUtil.getPackageName("java.lang.String"));
    assertEquals("java.util.Map", StringUtil.getPackageName("java.util.Map.Entry"));
    assertEquals("Map", StringUtil.getPackageName("Map.Entry"));
    assertEquals("", StringUtil.getPackageName("Number"));
  }

  @Test
  public void testIndexOf_1() {
    char[] chars = new char[]{'a', 'b', 'c', 'd', 'a', 'b', 'c', 'd', 'A', 'B', 'C', 'D'};
    assertEquals(2, StringUtil.indexOf(chars, 'c', 0, 12, false));
    assertEquals(2, StringUtil.indexOf(chars, 'C', 0, 12, false));
    assertEquals(10, StringUtil.indexOf(chars, 'C', 0, 12, true));
    assertEquals(2, StringUtil.indexOf(chars, 'c', -42, 99, false));
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Test
  public void testIndexOf_2() {
    assertEquals(1, StringUtil.indexOf("axaxa", 'x', 0, 5));
    assertEquals(2, StringUtil.indexOf("abcd", 'c', -42, 99));
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Test
  public void testIndexOf_3() {
    assertEquals(1, StringUtil.indexOf("axaXa", 'x', 0, 5, false));
    assertEquals(3, StringUtil.indexOf("axaXa", 'X', 0, 5, true));
    assertEquals(2, StringUtil.indexOf("abcd", 'c', -42, 99, false));
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Test
  public void testIndexOfAny() {
    assertEquals(1, StringUtil.indexOfAny("axa", "x", 0, 5));
    assertEquals(1, StringUtil.indexOfAny("axa", "zx", 0, 5));
    assertEquals(2, StringUtil.indexOfAny("abcd", "c", -42, 99));
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Test
  public void testLastIndexOf() {
    assertEquals(1, StringUtil.lastIndexOf("axaxa", 'x', 0, 2));
    assertEquals(1, StringUtil.lastIndexOf("axaxa", 'x', 0, 3));
    assertEquals(3, StringUtil.lastIndexOf("axaxa", 'x', 0, 5));
    assertEquals(2, StringUtil.lastIndexOf("abcd", 'c', -42, 99));  // #IDEA-144968
  }

  @Test
  public void testEscapingIllegalXmlChars() {
    for (String s : new String[]{"ab\n\0\r\tde", "\\abc\1\2\3\uFFFFdef"}) {
      String escapedText = XmlStringUtil.escapeIllegalXmlChars(s);
      assertNull(Verifier.checkCharacterData(escapedText));
      assertEquals(s, XmlStringUtil.unescapeIllegalXmlChars(escapedText));
    }
  }

  @Test
  @SuppressWarnings("SpellCheckingInspection")
  public void testCountChars() {
    assertEquals(0, StringUtil.countChars("abcdefgh", 'x'));
    assertEquals(1, StringUtil.countChars("abcdefgh", 'd'));
    assertEquals(5, StringUtil.countChars("abcddddefghd", 'd'));
    assertEquals(4, StringUtil.countChars("abcddddefghd", 'd', 4, false));
    assertEquals(3, StringUtil.countChars("abcddddefghd", 'd', 4, true));
    assertEquals(2, StringUtil.countChars("abcddddefghd", 'd', 4, 6, false));
    assertEquals(3, StringUtil.countChars("aaabcddddefghdaaaa", 'a', -20, 20, true));
    assertEquals(4, StringUtil.countChars("aaabcddddefghdaaaa", 'a', 20, -20, true));
  }

  @Test
  @SuppressWarnings("SpellCheckingInspection")
  public void testSubstringBeforeLast() {
    assertEquals("a", StringUtil.substringBeforeLast("abc", "b"));
    assertEquals("abab", StringUtil.substringBeforeLast("ababbccc", "b"));
    assertEquals("abc", StringUtil.substringBeforeLast("abc", ""));
    assertEquals("abc", StringUtil.substringBeforeLast("abc", "1"));
    assertEquals("", StringUtil.substringBeforeLast("", "1"));
    assertEquals("a", StringUtil.substringBeforeLast("abc", "b", false));
    assertEquals("abab", StringUtil.substringBeforeLast("ababbccc", "b", false));
    assertEquals("abc", StringUtil.substringBeforeLast("abc", "", false));
    assertEquals("abc", StringUtil.substringBeforeLast("abc", "1", false));
    assertEquals("", StringUtil.substringBeforeLast("", "1", false));
    assertEquals("ab", StringUtil.substringBeforeLast("abc", "b", true));
    assertEquals("ababb", StringUtil.substringBeforeLast("ababbccc", "b", true));
    assertEquals("abc", StringUtil.substringBeforeLast("abc", "", true));
    assertEquals("abc", StringUtil.substringBeforeLast("abc", "1", true));
    assertEquals("", StringUtil.substringBeforeLast("", "1", true));
  }

  @Test
  @SuppressWarnings("SpellCheckingInspection")
  public void testSubstringAfterLast() {
    assertEquals("c", StringUtil.substringAfterLast("abc", "b"));
    assertEquals("ccc", StringUtil.substringAfterLast("ababbccc", "b"));
    assertEquals("", StringUtil.substringAfterLast("abc", ""));
    assertNull(StringUtil.substringAfterLast("abc", "1"));
    assertNull(StringUtil.substringAfterLast("", "1"));
  }

  @Test
  public void testGetWordIndicesIn() {
    assertThat(StringUtil.getWordIndicesIn("first second")).containsExactly(new TextRange(0, 5), new TextRange(6, 12));
    assertThat(StringUtil.getWordIndicesIn(" first second")).containsExactly(new TextRange(1, 6), new TextRange(7, 13));
    assertThat(StringUtil.getWordIndicesIn(" first second    ")).containsExactly(new TextRange(1, 6), new TextRange(7, 13));
    assertThat(StringUtil.getWordIndicesIn("first:second")).containsExactly(new TextRange(0, 5), new TextRange(6, 12));
    assertThat(StringUtil.getWordIndicesIn("first-second")).containsExactly(new TextRange(0, 5), new TextRange(6, 12));
    assertThat(StringUtil.getWordIndicesIn("first-second", Set.of(' ', '_', '.'))).containsExactly(new TextRange(0, 12));
    assertThat(StringUtil.getWordIndicesIn("first-second", Set.of('-'))).containsExactly(new TextRange(0, 5), new TextRange(6, 12));
  }

  @Test
  @SuppressWarnings({"SpellCheckingInspection", "NonAsciiCharacters"})
  public void testIsLatinAlphanumeric() {
    assertTrue(StringUtil.isLatinAlphanumeric("1234567890"));
    assertTrue(StringUtil.isLatinAlphanumeric("123abc593"));
    assertTrue(StringUtil.isLatinAlphanumeric("gwengioewn"));
    assertTrue(StringUtil.isLatinAlphanumeric("FiwnFWinfs"));
    assertTrue(StringUtil.isLatinAlphanumeric("b"));
    assertTrue(StringUtil.isLatinAlphanumeric("1"));

    assertFalse(StringUtil.isLatinAlphanumeric("йцукен"));
    assertFalse(StringUtil.isLatinAlphanumeric("ЙцуTYuio"));
    assertFalse(StringUtil.isLatinAlphanumeric("йцу626кен"));
    assertFalse(StringUtil.isLatinAlphanumeric("12 12"));
    assertFalse(StringUtil.isLatinAlphanumeric("."));
    assertFalse(StringUtil.isLatinAlphanumeric("_"));
    assertFalse(StringUtil.isLatinAlphanumeric("-"));
    assertFalse(StringUtil.isLatinAlphanumeric("fhu384 "));
    assertFalse(StringUtil.isLatinAlphanumeric(""));
    assertFalse(StringUtil.isLatinAlphanumeric(null));
    assertFalse(StringUtil.isLatinAlphanumeric("'"));
  }

  @Test
  public void testIsShortNameOf() {
    assertTrue(StringUtil.isShortNameOf("a.b.c", "c"));
    assertTrue(StringUtil.isShortNameOf("foo", "foo"));
    assertFalse(StringUtil.isShortNameOf("foo", ""));
    assertFalse(StringUtil.isShortNameOf("", "foo"));
    assertFalse(StringUtil.isShortNameOf("a.b.c", "d"));
    assertFalse(StringUtil.isShortNameOf("x.y.zzz", "zz"));
    assertFalse(StringUtil.isShortNameOf("x", "a.b.x"));
  }

  @Test
  @SuppressWarnings("SpellCheckingInspection")
  public void startsWith() {
    assertTrue(StringUtil.startsWith("abcdefgh", 5, "fgh"));
    assertTrue(StringUtil.startsWith("abcdefgh", 2, "cde"));
    assertTrue(StringUtil.startsWith("abcdefgh", 0, "abc"));
    assertTrue(StringUtil.startsWith("abcdefgh", 0, "abcdefgh"));
    assertFalse(StringUtil.startsWith("abcdefgh", 5, "cde"));

    assertTrue(StringUtil.startsWith("abcdefgh", 0, ""));
    assertTrue(StringUtil.startsWith("abcdefgh", 4, ""));
    assertTrue(StringUtil.startsWith("abcdefgh", 7, ""));
    assertTrue(StringUtil.startsWith("abcdefgh", 8, ""));

    assertTrue(StringUtil.startsWith("", 0, ""));

    assertFalse(StringUtil.startsWith("ab", 0, "abcdefgh"));
    assertFalse(StringUtil.startsWith("ab", 1, "abcdefgh"));
    assertFalse(StringUtil.startsWith("ab", 2, "abcdefgh"));
  }

  @Test(expected = IllegalArgumentException.class)
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void startsWithNegativeIndex() {
    StringUtil.startsWith("whatever", -1, "");
  }

  @Test(expected = IllegalArgumentException.class)
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void startsWithIndexGreaterThanLength() {
    StringUtil.startsWith("whatever", 9, "");
  }

  @Test(expected = IllegalArgumentException.class)
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void startsWithEmptyStringNegativeIndex() {
    StringUtil.startsWith("", -1, "");
  }

  @Test(expected = IllegalArgumentException.class)
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void startsWithEmptyStringIndexGreaterThanLength() {
    StringUtil.startsWith("", 1, "");
  }

  @Test(expected = IllegalArgumentException.class)
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void startsWithLongerSuffixNegativeIndex() {
    StringUtil.startsWith("wh", -1, "whatever");
  }

  @Test(expected = IllegalArgumentException.class)
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void startsWithLongerSuffixIndexGreaterThanLength() {
    StringUtil.startsWith("wh", 3, "whatever");
  }

  @Test
  public void offsetToLineNumberCol() {
    assertEquals(LineColumn.of(0, 0), StringUtil.offsetToLineColumn("abc\nabc", 0));
    assertEquals(LineColumn.of(0, 1), StringUtil.offsetToLineColumn("abc\nabc", 1));
    assertEquals(LineColumn.of(0, 2), StringUtil.offsetToLineColumn("abc\nabc", 2));
    assertEquals(LineColumn.of(0, 3), StringUtil.offsetToLineColumn("abc\nabc", 3));
    assertEquals(LineColumn.of(1, 0), StringUtil.offsetToLineColumn("abc\nabc", 4));
    assertEquals(LineColumn.of(1, 1), StringUtil.offsetToLineColumn("abc\nabc", 5));
    assertEquals(LineColumn.of(1, 3), StringUtil.offsetToLineColumn("abc\nabc", 7));
    assertNull(StringUtil.offsetToLineColumn("abc\nabc", 8));
    assertEquals(LineColumn.of(0, 3), StringUtil.offsetToLineColumn("abc\r\nabc", 3));
    assertEquals(LineColumn.of(1, 0), StringUtil.offsetToLineColumn("abc\r\nabc", 5));
    assertEquals(LineColumn.of(2, 1), StringUtil.offsetToLineColumn("abc\n\nabc", 6));
    assertEquals(LineColumn.of(1, 1), StringUtil.offsetToLineColumn("abc\r\nabc", 6));
  }

  @Test
  public void testEnglishOrdinals() {
    assertEquals("100th", OrdinalFormat.formatEnglish(100));
    assertEquals("101st", OrdinalFormat.formatEnglish(101));
    assertEquals("111th", OrdinalFormat.formatEnglish(111));
    assertEquals("122nd", OrdinalFormat.formatEnglish(122));

    assertEquals("-3rd", OrdinalFormat.formatEnglish(-3));
    assertEquals("-9223372036854775808th", OrdinalFormat.formatEnglish(Long.MIN_VALUE));
  }

  @Test
  public void testCollapseWhiteSpace() {
    assertEquals("one two three four five", StringUtil.collapseWhiteSpace("\t one\ttwo     three\nfour five   "));
    assertEquals("one two three four five", StringUtil.collapseWhiteSpace(" one \ttwo  \t  three\n\tfour five "));
  }

  @Test
  public void testReplaceUnicodeEscapeSequences() {
    assertEquals("Z", StringUtil.replaceUnicodeEscapeSequences("\\uuu005a"));
    assertEquals("ZZ", StringUtil.replaceUnicodeEscapeSequences("\\uuu005aZ"));
    assertEquals("ZZZ", StringUtil.replaceUnicodeEscapeSequences("Z\\uuu005aZ"));
    assertEquals("Z\\\\uuu005aZ", StringUtil.replaceUnicodeEscapeSequences("Z\\\\uuu005aZ"));
    assertEquals("\\uuu005\\a\\u1\\u22\\u333", StringUtil.replaceUnicodeEscapeSequences("\\uuu005\\a\\u1\\u22\\u333"));
    assertEquals("\\uA\\u1Z", StringUtil.replaceUnicodeEscapeSequences("\\u\\u0041\\u1\\u005a"));
    assertEquals("\\u004", StringUtil.replaceUnicodeEscapeSequences("\\u004"));
    assertEquals("\\", StringUtil.replaceUnicodeEscapeSequences("\\"));
    assertEquals("\\u", StringUtil.replaceUnicodeEscapeSequences("\\u"));
    assertEquals("\\uu", StringUtil.replaceUnicodeEscapeSequences("\\uu"));
    assertEquals("\\uu1", StringUtil.replaceUnicodeEscapeSequences("\\uu1"));
  }

  @Test
  public void testStripCharFilter() {
    assertEquals("my-string", StringUtil.strip("\n   my -string ", CharFilter.NOT_WHITESPACE_FILTER));
    assertEquals("my-string", StringUtil.strip("my- string", CharFilter.NOT_WHITESPACE_FILTER));
    assertEquals("my-string", StringUtil.strip("my-string", CharFilter.NOT_WHITESPACE_FILTER));
    assertEquals("\n     ", StringUtil.strip("\n   my string ", CharFilter.WHITESPACE_FILTER));
    assertEquals("", StringUtil.strip("", CharFilter.WHITESPACE_FILTER));
    assertEquals("", StringUtil.strip("\n   my string ", ch -> false));
    assertEquals("\n   my string ", StringUtil.strip("\n   my string ", ch -> true));
  }

  @Test
  public void testTrimCharFilter() {
    assertEquals("my string", StringUtil.trim("\n   my string ", CharFilter.NOT_WHITESPACE_FILTER));
    assertEquals("my string", StringUtil.trim("my string", CharFilter.NOT_WHITESPACE_FILTER));
    assertEquals("my string", StringUtil.trim("my string\t", CharFilter.NOT_WHITESPACE_FILTER));
    assertEquals("my string", StringUtil.trim("\nmy string", CharFilter.NOT_WHITESPACE_FILTER));
    assertEquals("my-string", StringUtil.trim("my-string", CharFilter.NOT_WHITESPACE_FILTER));
    assertEquals("my-string", StringUtil.trim("my-string ", CharFilter.NOT_WHITESPACE_FILTER));
    assertEquals("\n   my string ", StringUtil.trim("\n   my string ", CharFilter.WHITESPACE_FILTER));
    assertEquals("", StringUtil.trim("", CharFilter.WHITESPACE_FILTER));
    assertEquals("", StringUtil.trim("\n   my string ", ch -> false));
    assertEquals("\n   my string ", StringUtil.trim("\n   my string ", ch -> true));
    assertEquals("\u00A0   my string", StringUtil.trim("\u00A0   my string ", CharFilter.NOT_WHITESPACE_FILTER));
  }

  @Test
  public void testEscapeToRegexp() {
    assertEquals("a\\nb", StringUtil.escapeToRegexp("a\nb"));
    assertEquals("a\\&\\%\\$b", StringUtil.escapeToRegexp("a&%$b"));
    assertEquals("\uD83D\uDE80", StringUtil.escapeToRegexp("\uD83D\uDE80"));
  }

  @Test
  public void testRemoveEllipsisSuffix() {
    assertEquals("a", removeEllipsisSuffix("a..."));
    assertEquals("a", removeEllipsisSuffix("a"));
    assertEquals("a", removeEllipsisSuffix("a" + ELLIPSIS));
    assertEquals("a...", removeEllipsisSuffix("a..." + ELLIPSIS));
  }

  @Test
  public void testEndsWith() {
    assertTrue(StringUtil.endsWith("text", 0, 4, "text"));
    assertFalse(StringUtil.endsWith("text", 4, 4, "-->"));
    UsefulTestCase.assertThrows(IllegalArgumentException.class, () -> assertFalse(StringUtil.endsWith("text", -1, 4, "t")));
    assertFalse(StringUtil.endsWith("text", "-->"));
  }

  @Test
  public void testIsJavaIdentifier() {
    assertFalse(StringUtil.isJavaIdentifier(""));
    assertTrue(StringUtil.isJavaIdentifier("x"));
    assertFalse(StringUtil.isJavaIdentifier("0"));
    assertFalse(StringUtil.isJavaIdentifier("0x"));
    assertTrue(StringUtil.isJavaIdentifier("x0"));
    assertTrue(StringUtil.isJavaIdentifier("\uD835\uDEFCA"));
    assertTrue(StringUtil.isJavaIdentifier("A\uD835\uDEFC"));
    //noinspection UnnecessaryUnicodeEscape
    assertTrue(StringUtil.isJavaIdentifier("\u03B1A"));
  }

  @SuppressWarnings("UnnecessaryUnicodeEscape")
  @Test
  public void testCharSequenceSliceIsJavaIdentifier() {
    assertFalse(StringUtil.isJavaIdentifier("", 0, 0));
    assertTrue(StringUtil.isJavaIdentifier("x", 0, 1));
    assertFalse(StringUtil.isJavaIdentifier("0", 0, 1));
    assertFalse(StringUtil.isJavaIdentifier("0x", 0, 2));
    assertTrue(StringUtil.isJavaIdentifier("foo$bar", 0, 7));
    assertTrue(StringUtil.isJavaIdentifier("x0", 0, 2));
    assertTrue(StringUtil.isJavaIdentifier("\uD835\uDEFCA", 0, 3));
    assertTrue(StringUtil.isJavaIdentifier("A\uD835\uDEFC", 0, 3));
    assertTrue(StringUtil.isJavaIdentifier("\u03B1A", 0, 2));
    assertTrue(StringUtil.isJavaIdentifier("###\u03B1A", 3, 5));
    assertTrue(StringUtil.isJavaIdentifier("\u03B1A###", 0, 2));
    assertTrue(StringUtil.isJavaIdentifier("###\u03B1A###", 3, 5));
  }

  @Test
  public void testSplit() {
    String spaceSeparator = " ";
    //noinspection rawtypes
    List split1 = StringUtil.split("test", spaceSeparator, false, false);
    List split2 = StringUtil.split(new CharSequenceSubSequence("test"), spaceSeparator, false,
                                   false);
    assertTrue(ContainerUtil.getOnlyItem(split1) instanceof String);
    assertTrue(ContainerUtil.getOnlyItem(split2) instanceof CharSequenceSubSequence);

    assertEquals(Arrays.asList(""), StringUtil.split("", spaceSeparator, false, false));
    assertEquals(Arrays.asList(), StringUtil.split("", spaceSeparator, true, true));

    assertEquals(Arrays.asList(" ", ""), StringUtil.split(" ", spaceSeparator, false, false));
    assertEquals(Arrays.asList("", ""), StringUtil.split(" ", spaceSeparator, true, false));
    assertEquals(Arrays.asList(), StringUtil.split(" ", spaceSeparator, true, true));

    assertEquals(Arrays.asList("a", "b"), StringUtil.split("a  b ", spaceSeparator, true, true));
    assertEquals(Arrays.asList("a ", " ", "b "), StringUtil.split("a  b ", spaceSeparator, false, true));
    assertEquals(Arrays.asList("a ", " ", "b ", ""), StringUtil.split("a  b ", spaceSeparator, false, false));

    assertEquals(Arrays.asList("a", "b"), StringUtil.split("a  b", spaceSeparator, true, true));
    assertEquals(Arrays.asList("a ", " ", "b"), StringUtil.split("a  b", spaceSeparator, false, true));
    assertEquals(Arrays.asList("a ", " ", "b"), StringUtil.split("a  b", spaceSeparator, false, false));

    assertEquals(Arrays.asList("test"), StringUtil.split("test", spaceSeparator, true, true));
    assertEquals(Arrays.asList("test"), StringUtil.split("test", spaceSeparator, false, true));
    assertEquals(Arrays.asList("test"), StringUtil.split("test", spaceSeparator, true, false));
    assertEquals(Arrays.asList("test"), StringUtil.split("test", spaceSeparator, false, false));

    assertEquals(Arrays.asList("a", " b"), StringUtil.split("a  b ", spaceSeparator, true, true));
    assertEquals(Arrays.asList("a", "\n\tb"), StringUtil.split("a \n\tb ", spaceSeparator, true, true));

    assertEquals(Arrays.asList("a\u00A0b"), StringUtil.split("a\u00A0b", spaceSeparator, true, true));

    assertEquals(Arrays.asList("  \n\t", " "), StringUtil.split("a  \n\ta ", "a", true, true));
  }

  @Test
  public void testSplitCharFilter() {
    CharFilter spaceSeparator = CharFilter.WHITESPACE_FILTER;
    //noinspection rawtypes
    List split1 = StringUtil.split("test", spaceSeparator, false, false);
    List split2 = StringUtil.split(new CharSequenceSubSequence("test"), spaceSeparator, false,
                                   false);
    assertTrue(ContainerUtil.getOnlyItem(split1) instanceof String);
    assertTrue(ContainerUtil.getOnlyItem(split2) instanceof CharSequenceSubSequence);

    assertEquals(Arrays.asList(""), StringUtil.split("", spaceSeparator, false, false));
    assertEquals(Arrays.asList(), StringUtil.split("", spaceSeparator, true, true));

    assertEquals(Arrays.asList(" ", ""), StringUtil.split(" ", spaceSeparator, false, false));
    assertEquals(Arrays.asList("", ""), StringUtil.split(" ", spaceSeparator, true, false));
    assertEquals(Arrays.asList(), StringUtil.split(" ", spaceSeparator, true, true));

    assertEquals(Arrays.asList("a", "b"), StringUtil.split("a  b ", spaceSeparator, true, true));
    assertEquals(Arrays.asList("a ", " ", "b "), StringUtil.split("a  b ", spaceSeparator, false, true));
    assertEquals(Arrays.asList("a ", " ", "b ", ""), StringUtil.split("a  b ", spaceSeparator, false, false));

    assertEquals(Arrays.asList("a", "b"), StringUtil.split("a  b", spaceSeparator, true, true));
    assertEquals(Arrays.asList("a ", " ", "b"), StringUtil.split("a  b", spaceSeparator, false, true));
    assertEquals(Arrays.asList("a ", " ", "b"), StringUtil.split("a  b", spaceSeparator, false, false));

    assertEquals(Arrays.asList("test"), StringUtil.split("test", spaceSeparator, true, true));
    assertEquals(Arrays.asList("test"), StringUtil.split("test", spaceSeparator, false, true));
    assertEquals(Arrays.asList("test"), StringUtil.split("test", spaceSeparator, true, false));
    assertEquals(Arrays.asList("test"), StringUtil.split("test", spaceSeparator, false, false));

    assertEquals(Arrays.asList("a", "b"), StringUtil.split("a  b ", spaceSeparator, true, true));
    assertEquals(Arrays.asList("a", "b"), StringUtil.split("a \n\tb ", spaceSeparator, true, true));

    assertEquals(Arrays.asList("a\u00A0b"), StringUtil.split("a\u00A0b", spaceSeparator, true, true));

    assertEquals(Arrays.asList("  \n\t", " "), StringUtil.split("a  \n\ta ", CharFilter.NOT_WHITESPACE_FILTER, true, true));
  }

  @Test
  @SuppressWarnings({"OctalInteger", "UnnecessaryUnicodeEscape"}) // need to test octal numbers and escapes
  public void testUnescapeAnsiStringCharacters() {
    assertEquals("'", StringUtil.unescapeAnsiStringCharacters("\\'"));
    assertEquals("\"", StringUtil.unescapeAnsiStringCharacters("\\\""));
    assertEquals("?", StringUtil.unescapeAnsiStringCharacters("\\?"));
    assertEquals("\\", StringUtil.unescapeAnsiStringCharacters("\\\\"));
    assertEquals("" + (char)0x07, StringUtil.unescapeAnsiStringCharacters("\\a"));
    assertEquals("" + (char)0x08, StringUtil.unescapeAnsiStringCharacters("\\b"));
    assertEquals("" + (char)0x0c, StringUtil.unescapeAnsiStringCharacters("\\f"));
    assertEquals("\n", StringUtil.unescapeAnsiStringCharacters("\\n"));
    assertEquals("\r", StringUtil.unescapeAnsiStringCharacters("\\r"));
    assertEquals("\t", StringUtil.unescapeAnsiStringCharacters("\\t"));
    assertEquals("" + (char)0x0b, StringUtil.unescapeAnsiStringCharacters("\\v"));

    // octal
    assertEquals("" + (char)00, StringUtil.unescapeAnsiStringCharacters("\\0"));
    assertEquals("" + (char)01, StringUtil.unescapeAnsiStringCharacters("\\1"));
    assertEquals("" + (char)012, StringUtil.unescapeAnsiStringCharacters("\\12"));
    assertEquals("" + (char)0123, StringUtil.unescapeAnsiStringCharacters("\\123"));

    // hex
    assertEquals("" + (char)0x0, StringUtil.unescapeAnsiStringCharacters("\\x0"));
    assertEquals("" + (char)0xf, StringUtil.unescapeAnsiStringCharacters("\\xf"));
    assertEquals("" + (char)0xff, StringUtil.unescapeAnsiStringCharacters("\\xff"));
    assertEquals("" + (char)0xfff, StringUtil.unescapeAnsiStringCharacters("\\xfff"));
    assertEquals("" + (char)0xffff, StringUtil.unescapeAnsiStringCharacters("\\xffff"));
    assertEquals("" + (char)0xf, StringUtil.unescapeAnsiStringCharacters("\\x0000000000000000f"));
    assertEquals("\\x110000", StringUtil.unescapeAnsiStringCharacters("\\x110000")); // invalid unicode codepoint

    // 4 digit codepoint
    assertEquals("\u1234", StringUtil.unescapeAnsiStringCharacters("\\u1234"));

    // 8 digit codepoint
    assertEquals("\u0061", StringUtil.unescapeAnsiStringCharacters("\\U00000061"));
    assertEquals("\\U00110000", StringUtil.unescapeAnsiStringCharacters("\\U00110000")); // invalid unicode codepoint
  }
}
