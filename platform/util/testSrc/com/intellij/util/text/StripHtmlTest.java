// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StripHtmlTest {

  @Test
  public void testBasicCasesWithNewLine() {
    testStripHtmlNewLine("foo<br \n \r>baz", "foo\nbaz");
    testStripHtmlNewLine("foo<br \n \r/>baz", "foo\nbaz");
    testStripHtmlNewLine("foo<br \n \r/ >baz", "foo\nbaz");
    testStripHtmlNewLine("foo<BR \n \r/ >baz", "foo\nbaz");
    testStripHtmlNewLine("foo< \n bar \n  \r >baz", "foobaz");
    testStripHtmlNewLine("foo< \n bar \n  \r />baz", "foobaz");
    testStripHtmlNewLine("foo< \n bar \n  \r / >baz", "foobaz");
  }

  @Test
  public void testSimpleHtmlStripping() {
    testStripHtml(
      "<html><body><h1>Hello</h1> <p>World</p></body></html>",
      "Hello World"
    );
  }

  @Test
  public void testHtmlWithLineBreaks() {
    testStripHtml(
      "<html><body><h1>Hello</h1><br><p>World</p></body></html>",
      "\n\n",
      "Hello\n\nWorld"
    );
  }

  @Test
  public void testHtmlWithNestedTags() {
    testStripHtml(
      "<div><p><span>Nested</span> Tags</p></div>",
      "Nested Tags"
    );
  }

  @Test
  public void testHtmlWithEmptyTags() {
    testStripHtml(
      "<div><p></p><span></span></div>",
      ""
    );
  }

  @Test
  public void testMultilineHtmlWithBreaks() {
    testStripHtml(
      "<html>\n<body>\n<h1>Hello</h1>\n<br>\n<p>World</p>\n</body>\n</html>",
      "\n\n",
      "\n\nHello\n\n\n\nWorld\n\n"
    );
  }

  @Test
  public void testMultilineHtmlWithoutBreaks() {
    testStripHtml(
      "<html>\n<body>\n<h1>Hello</h1>\n<p>World</p>\n</body>\n</html>",
      "\n\nHello\nWorld\n\n"
    );
  }

  @Test
  public void testMultilineHtmlWithMultipleBreaksInContent() {
    testStripHtml(
      "<html>\n<body>\n<p>Line 1</p>\n<br>\n<br>\n<p>Line 2</p>\n</body>\n</html>",
      "\n\n",
      "\n\nLine 1\n\n\n\n\n\n\nLine 2\n\n"
    );
  }

  @Test
  public void testMultilineHtmlWithComplexTags() {
    testStripHtml(
      """
        <div>
          <p>
            <span>Complex</span>
          </p>
          Tags
        </div>""",
      """
        
         \s
            Complex
         \s
          Tags
        """
    );
  }

  @Test
  public void testHtmlWithAttributes() {
    testStripHtml(
      "<a href=\"https://example.com\">Click here</a>",
      "Click here"
    );
  }

  @Test
  public void testHtmlWithMultipleBreaks() {
    testStripHtml(
      "<p>First line</p><br><br><p>Second line</p>",
      "\n\n",
      "First line\n\n\n\nSecond line"
    );
  }

  @Test
  public void testHtmlWithoutTags() {
    testStripHtml(
      "Plain text only",
      "Plain text only"
    );
  }

  @Test
  public void testHtmlWithStylesSkipsTagContents() {
    testStripHtml(
      "<html><head><style>body {color: red;}</style></head><body>Hello World</body></html>",
      "body {color: red;}Hello World"
    );
  }

  @Test
  public void testHtmlWithMultipleLineBreaksConverted() {
    testStripHtml(
      "<p>One</p><br><p>Two</p><br><p>Three</p>",
      "\\$\\$\\$",
      "One$$$Two$$$Three"
    );
  }

  @Test
  public void testHtmlWithUnicodeCharacters() {
    testStripHtml(
      "<p>\uD83D\uDE0A Hello</p><p>World ❤</p>",
      "\n\n",
      "\uD83D\uDE0A HelloWorld ❤"
    );
  }

  @Test
  public void testEmptyHtmlString() {
    testStripHtml(
      "",
      ""
    );
  }

  @Test
  public void testLongTag() {
    testStripHtml(
      ("<p " + "b".repeat(10000) + ">a</p>").repeat(20),
      "a".repeat(20)
    );
  }

  private static void testStripHtml(String html, String expected) {
    testStripHtml(html, null, expected); 
  }

  private static void testStripHtmlNewLine(String html, String expected) {
    assertEquals(expected, StringUtil.stripHtml(html, "\n"));
  }
  
  private static void testStripHtml(String html, String breaks, String expected) {
    assertEquals(expected, StringUtil.stripHtml(html, breaks));
  }
}
