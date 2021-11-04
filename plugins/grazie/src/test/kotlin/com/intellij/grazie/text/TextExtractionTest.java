package com.intellij.grazie.text;

import com.intellij.grazie.ide.language.java.JavaTextExtractor;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.StringLiteralManipulator;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.lang.regexp.RegExpLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class TextExtractionTest extends BasePlatformTestCase {
  public void testMarkdownInlineLink() {
    TextContent extracted = extractText("a.md", "* list [item](http://x) with a local link", 3);
    assertEquals("list item with a local link", extracted.toString());
    int prefix = "* ".length();
    assertEquals(prefix + "list [".length(), extracted.textOffsetToFile("list ".length()));
    assertEquals(prefix + "list [item".length(), extracted.textOffsetToFile("list item".length()));
  }

  public void testMarkdownImage() {
    TextContent extracted = extractText("a.md", "[Before ![AltText](http://www.google.com.au/images/nav_logo7.png) after](http://google.com.au/)", 3);
    assertEquals("Before  after", extracted.toString());
  }

  public void testMarkdownIndent() {
    TextContent extracted = extractText("a.md", "* first line\n  second line", 3);
    assertEquals("first line\nsecond line", TextContentTest.unknownOffsets(extracted));
  }

  public void testMarkdownInlineCode() {
    TextContent extracted = extractText("a.md", "you can use a number of predefined fields (e.g. `EventFields.InputEvent`)", 0);
    assertEquals("you can use a number of predefined fields (e.g. |)", TextContentTest.unknownOffsets(extracted));
  }

  public void testMergeAdjacentJavaComments() {
    String text = extractText("a.java", "//Hello. I are a very humble\n//persons.\n\nclass C {}", 4).toString();
    assertTrue(text, text.matches("Hello\\. I are a very humble\\spersons\\."));

    assertEquals("First line.\nThird line.", extractText("a.java",
      "// First line.\n" +
      "// \n" +
      "//   Third line.\n"
      , 4).toString());

    text = "//1\n//2\n//3\n//4";
    assertEquals("1\n2\n3\n4", extractText("a.java", text, text.indexOf("1")).toString());
    assertEquals("1\n2\n3\n4", extractText("a.java", text, text.indexOf("3")).toString());
  }

  public void testIgnorePropertyCommentStarts() {
    assertEquals("Hello World #42!", extractText("a.properties", "#\t Hello World #42!", 4).toString());
  }

  public void testProcessPropertyMessageFormat() {
    assertEquals("Hello World '|'!", TextContentTest.unknownOffsets(extractText("a.properties", "a=Hello World ''{0}''!", 4)));
  }

  public void testBrokenPropertyMessageFormat() {
    assertEquals("a |", TextContentTest.unknownOffsets(extractText("a.properties", "a=a {0, choice, 1#1 code fragment|2#{0,number} code fragments", 4)));
  }

  public void testExcludePropertyHtml() {
    assertEquals("Hello |World", TextContentTest.unknownOffsets(extractText("a.properties", "a=<html>Hello <p/>World</html>", 4)));
  }

  public void testMultiLineCommentInProperties() {
    assertEquals("line1\nline2", TextContentTest.unknownOffsets(extractText("a.properties", "# line1\n! line2", 4)));
  }

  public void testJavadoc() {
    String docText = "/**\n" +
                     "* Hello {@link #foo},\n" +
                     "* here's an asterisk: *\n" +
                     "* and some {@code code}.\n" +
                     "* tags1 <unknownTag>this<unknownTag>is</unknownTag>unknown</unknownTag >\n" +
                     "* tags2 <unknown1>one<unknown2>unknown<unknown1>unknown</unknown2> two<p/> three<unknown1/> four</unknown1>\n" +
                     "* {@link #unknown} is unknown.\n" +
                     "* @param foo the text without the parameter name\n" +
                     "* @return the offset of {@link #bar} in something\n" +
                     " */";
    TextContent text = extractText("a.java", docText, 6);
    assertEquals("Hello |,\nhere's an asterisk: *\nand some |.\ntags1 |\ntags2 |\n|is unknown.", TextContentTest.unknownOffsets(text));

    text = extractText("a.java", docText, docText.indexOf("the offset"));
    assertEquals("the offset of  in something", text.toString());

    text = extractText("a.java", docText, docText.indexOf("without"));
    assertEquals("the text without the parameter name", text.toString());
  }

  public void testJavaLiteral() {
    assertEquals("abc def", extractText("a.java", "class C { String s = \" abc def \"; }", 27).toString());
  }

  public void testJavaTextBlock() {
    String text = "class C { " +
                  "  String s = \"\"\"\n" +
                  "    abc \\\n" +
                  "    \\\n" +
                  "    def\n" +
                  "      ghi\n" +
                  "    \"\"\"; " +
                  "}";
    int offset = text.indexOf("def");
    TextContent content = extractText("a.java", text, offset);
    assertEquals("abc def\n  ghi", content.toString());
    assertEquals(offset, content.textOffsetToFile("abc ".length()));
  }

  public void testNoExtractionInInjectedFragments() {
    InjectedLanguageManager.getInstance(getProject()).registerMultiHostInjector(new MultiHostInjector() {
      @Override
      public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (context.getText().contains("xxx")) {
          registrar
            .startInjecting(RegExpLanguage.INSTANCE)
            .addPlace(null, null, (PsiLanguageInjectionHost) context, StringLiteralManipulator.getValueRange((PsiLiteralExpression) context))
            .doneInjecting();
        }
      }

      @Override
      public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(PsiLiteralExpression.class);
      }
    }, getTestRootDisposable());

    String text = "class C { String s = \" abc def xxx \"; }";
    PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText("a.java", JavaFileType.INSTANCE, text, 0, true);
    PsiElement leaf = file.findElementAt(text.indexOf("def"));
    assertNull(TextExtractor.findTextAt(Objects.requireNonNull(leaf), TextContent.TextDomain.ALL));
  }

  public void testXmlHtml() {
    assertEquals("|abc|", TextContentTest.unknownOffsets(extractText("a.html", "<b>abc</b>", 4)));
    assertEquals("abc", extractText("a.xml", "<code>abc</code>", 6).toString());
    assertEquals("|characters with markup|", TextContentTest.unknownOffsets(extractText("a.xml", "<b><![CDATA[\n   characters with markup\n]]></b>", 22)));
    assertEquals("abcd", TextContentTest.unknownOffsets(extractText("a.xml", "<tag attr=\"abcd\"/>", 14)));
    assertEquals("comment", extractText("a.xml", "<!-- comment -->", 10).toString());

    //nothing in HTML <code> tag
    assertNull(extractText("a.html", "<code>abc</code>", 7));
  }

  public void testBuildingPerformance_concatenation() {
    String text = "<a/>b".repeat(10_000);
    String expected = "b".repeat(10_000);
    PsiFile file = myFixture.configureByText("a.xml", text);
    TextContentBuilder builder = TextContentBuilder.FromPsi.excluding(e -> e instanceof XmlTag);
    PlatformTestUtil.startPerformanceTest("TextContent building with concatenation", 200, () -> {
      assertEquals(expected, builder.build(file, TextContent.TextDomain.PLAIN_TEXT).toString());
    }).assertTiming();
  }

  public void testBuildingPerformance_removingIndents() {
    String text = "  b\n".repeat(10_000);
    String expected = "b\n".repeat(10_000).trim();
    PsiFile file = myFixture.configureByText("a.java", "/*\n" + text + "*/");
    PsiComment comment = assertInstanceOf(file.findElementAt(10), PsiComment.class);
    TextContentBuilder builder = TextContentBuilder.FromPsi.removingIndents(" ");
    PlatformTestUtil.startPerformanceTest("TextContent building with indent removing", 200, () -> {
      assertEquals(expected, builder.build(comment, TextContent.TextDomain.COMMENTS).toString());
    }).assertTiming();
  }

  public void testBuildingPerformance_removingHtml() {
    String text = "b<unknownTag>x</unknownTag>".repeat(10_000);
    String expected = "b".repeat(10_000);
    PsiFile file = myFixture.configureByText("a.java", "/**\n" + text + "*/");
    PsiDocComment comment = PsiTreeUtil.findElementOfClassAtOffset(file, 10, PsiDocComment.class, false);
    TextExtractor extractor = new JavaTextExtractor();
    PlatformTestUtil.startPerformanceTest("TextContent building with HTML removal", 200, () -> {
      assertEquals(expected, extractor.buildTextContent(comment, TextContent.TextDomain.ALL).toString());
    }).assertTiming();
  }

  public void testBuildingPerformance_longTextFragment() {
    String line = "here's some relative long text that helps make this text fragment a bit longer than it could have been otherwise";
    String text = ("\n\n\n" + line).repeat(10_000);
    String expected = (line + "\n\n\n").repeat(10_000).trim();
    PsiFile file = myFixture.configureByText("a.java", "class C { String s = \"\"\"\n" + text + "\"\"\"; }");
    var literal = PsiTreeUtil.findElementOfClassAtOffset(file, 100, PsiLiteralExpression.class, false);
    var extractor = new JavaTextExtractor();
    PlatformTestUtil.startPerformanceTest("TextContent building from a long text fragment", 200, () -> {
      assertEquals(expected, extractor.buildTextContent(literal, TextContent.TextDomain.ALL).toString());
    }).assertTiming();
  }

  private TextContent extractText(String fileName, String fileText, int offset) {
    return extractText(fileName, fileText, offset, getProject());
  }

  public static TextContent extractText(String fileName, String fileText, int offset, Project project) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, fileText);
    PsiElement leaf = file.findElementAt(offset);
    return TextExtractor.findTextAt(Objects.requireNonNull(leaf), TextContent.TextDomain.ALL);
  }
}
