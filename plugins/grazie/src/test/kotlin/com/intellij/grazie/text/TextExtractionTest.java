package com.intellij.grazie.text;

import com.intellij.grazie.ide.language.java.JavaTextExtractor;
import com.intellij.grazie.ide.language.markdown.MarkdownTextExtractor;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.StringLiteralManipulator;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.containers.ContainerUtil;
import kotlin.text.StringsKt;
import one.util.streamex.IntStreamEx;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.plugins.markdown.lang.MarkdownFileType;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraph;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Set;

import static com.intellij.grazie.text.TextContentTest.unknownOffsets;

public class TextExtractionTest extends BasePlatformTestCase {
  public void testFindPsiAtLastOffset() {
    assertInstanceOf(extractText("a.txt", "foo", 1).findPsiElementAt(3), PsiPlainText.class);
  }

  public void testMarkdownInlineLink() {
    TextContent extracted = extractText("a.md", "* list [item](http://x) with a local link", 3);
    assertEquals("list item with a local link", unknownOffsets(extracted));
    Assertions.assertArrayEquals(new int[]{"list ".length(), "list item".length()}, extracted.markupOffsets());
    int prefix = "* ".length();
    assertEquals(prefix + "list [".length(), extracted.textOffsetToFile("list ".length()));
    assertEquals(prefix + "list [item".length(), extracted.textOffsetToFile("list item".length()));
  }

  public void testMarkdownUrlLink() {
    TextContent extracted = extractText("a.md", "go to [http://localhost](http://localhost) and validate", 3);
    assertEquals("go to http://localhost and validate", unknownOffsets(extracted));
  }

  public void testMarkdownImage() {
    TextContent extracted = extractText("a.md", "[Before ![AltText](http://www.google.com.au/images/nav_logo7.png) after](http://google.com.au/)", 3);
    assertEquals("Before  after", unknownOffsets(extracted));
    Assertions.assertArrayEquals(new int[]{0, extracted.length()}, extracted.markupOffsets());
  }

  public void testMarkdownShortRefLink() {
    TextContent extracted = extractText("a.md", "go to [link] and validate\n[link] : http://localhost", 3);
    assertEquals("go to link and validate\nlink : http://localhost", unknownOffsets(extracted));
    Assertions.assertArrayEquals(new int[]{6, 10, 24, 28}, extracted.markupOffsets());
  }

  public void testMarkdownIndent() {
    TextContent extracted = extractText("a.md", "* first line \n  second line", 3);
    assertEquals("first line\nsecond line", unknownOffsets(extracted));
  }

  public void testMarkdownStyles() {
    TextContent text = extractText("a.md", "**bold** *italic* ~~strikethrough~~", 3);
    assertEquals("bold italic strikethrough", unknownOffsets(text));
    Assertions.assertArrayEquals(new int[]{0, 4, 5, 11, 12, 25}, text.markupOffsets());
  }

  public void testHtmlNbsp() {
    assertEquals("hello world", unknownOffsets(extractText("a.md", "hello&nbsp;world", 3)));
    assertEquals("hello world", unknownOffsets(extractText("a.html", "hello&nbsp;world", 3)));
  }
  public void testMarkdownInlineCode() {
    TextContent extracted = extractText("a.md", "you can use a number of predefined fields (e.g. `EventFields.InputEvent`)", 0);
    assertEquals("you can use a number of predefined fields (e.g. |)", unknownOffsets(extracted));
  }

  public void testMergeAdjacentJavaComments() {
    String text = extractText("a.java", "//Hello. I are a very humble\n//persons.\n\nclass C {}", 4).toString();
    assertTrue(text, text.matches("Hello\\. I are a very humble\\spersons\\."));

    assertEquals("First line.\nThird line.", extractText("a.java",
                                                         """
                                                           // First line.
                                                           //\s
                                                           //   Third line.
                                                           """
      , 4).toString());

    text = "//1\n//2\n//3\n//4";
    PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText("a.java", JavaFileType.INSTANCE, text);
    TextContent tc = TextExtractor.findTextAt(file, text.indexOf("1"), TextContent.TextDomain.ALL);
    assertEquals("1\n2\n3\n4", tc.toString());
    assertEquals(tc, TextExtractor.findTextAt(file, text.indexOf("3"), TextContent.TextDomain.ALL));
  }

  public void testIgnorePropertyCommentStarts() {
    assertEquals("Hello World #42!", extractText("a.properties", "#\t Hello World #42!", 4).toString());
  }

  public void testProcessPropertyMessageFormat() {
    String text = "a=Hello World ''{0}''!";
    assertEquals("Hello World '|'!", unknownOffsets(extractText("a.properties", text, text.length())));
  }

  public void testBrokenPropertyMessageFormat() {
    assertEquals("a|", unknownOffsets(extractText("a.properties", "a=a {0, choice, 1#1 code fragment|2#{0,number} code fragments", 4)));
  }

  public void testExcludePropertyHtml() {
    List<TextContent> texts = extractTexts("a.properties", "a=<html>Hello <p/><i>World</i></html>", 8, PsiElement.class);
    assertEquals(List.of("Hello", "World"), ContainerUtil.map(texts, TextContentTest::unknownOffsets));
  }

  public void testMultiLineCommentInProperties() {
    assertEquals("line1\nline2", unknownOffsets(extractText("a.properties", "# line1\n! line2", 4)));
  }

  public void test_multiline_value_in_properties_does_not_include_leading_space() {
    assertEquals("line1line2", unknownOffsets(extractText("a.properties", "a=line1\\\n \t line2", 4)));
  }

  public void testJavadoc() {
    String docText = """
      /**
      * Hello {@link #foo},
      * here's an asterisk: *
      * and some {@code code}.
      * tags1 <unknownTag>this<unknownTag>is</unknownTag>unknown</unknownTag >
      * tags2 <unknown1>one<unknown2>unknown<unknown1>unknown</unknown2> two<p/> three<unknown1/> four</unknown1>
      * {@link #unknown} is unknown.
      * @param foo the text without the parameter name
      * @return the offset of {@link #bar} in something
      * @throws Exception when something happens
       */""";
    assertEquals(
      List.of("Hello |,\nhere's an asterisk: *\nand some |.\ntags1 |\ntags2 |one| two", "three| four|\n|is unknown."),
      ContainerUtil.map(extractTexts("a.java", docText, 6, PsiDocComment.class), TextContentTest::unknownOffsets));

    TextContent text = extractText("a.java", docText, docText.indexOf("the offset"));
    assertEquals("the offset of  in something", text.toString());

    text = extractText("a.java", docText, docText.indexOf("without"));
    assertEquals("the text without the parameter name", text.toString());

    text = extractText("a.java", docText, docText.indexOf("when something"));
    assertEquals("when something happens", text.toString());
  }

  public void testSnippet() {
    String docText = """
      /**
      * Before snippet
      * {@snippet id="snippetId" lang="java" :
      *   int foo = 1; // comment 1
      *   // comment 2
      *   class Foo {} // @highlight substring="Foo"
      * }
      * After snippet
       */""";
    TextContent text = extractText("a.java", docText, 6);
    assertEquals("Before snippet\n\nAfter snippet", text.toString());
  }

  public void testJavaLiteral() {
    assertEquals("abc def", extractText("a.java", "class C { String s = \" abc def \"; }", 27).toString());
  }

  public void testJavaTextBlock() {
    String text = """
      class C {   String s = ""\"
          abc \\
          \\
          def
            ghi
          ""\"; }""";
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
    assertNull(TextExtractor.findTextAt(file, text.indexOf("def"), TextContent.TextDomain.ALL));
  }

  public void testSplitPlainTextByParagraphsForMoreGranularChecking() {
    PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText(
      "a.txt", PlainTextFileType.INSTANCE, " First paragraph  \n  \n \t Second paragraph\n\n\n");
    assertEquals("First paragraph", TextExtractor.findTextAt(file, 2, TextContent.TextDomain.ALL).toString());
    assertEquals("Second paragraph", TextExtractor.findTextAt(file, 40, TextContent.TextDomain.ALL).toString());
  }

  public void testXmlHtml() {
    checkHtmlXml(true);

    Registry.get("grazie.html.concatenate.inline.tag.contents").setValue(false, getTestRootDisposable());
    myFixture.getPsiManager().dropPsiCaches();
    checkHtmlXml(false);
  }

  public void testLargeXmlPerformance() {
    String text = "<!DOCTYPE rules [\n" +
                  IntStreamEx.range(0, 1000).mapToObj(i -> "<!ENTITY pnct" + i + " \"x\">\n").joining() +
                  "]>\n" +
                  "<rules> content </rules><caret>";
    int offset1 = text.indexOf("content");
    int offset2 = text.indexOf("\n<!ENTITY");
    PsiFile file = myFixture.configureByText("a.xml", text);

    Benchmark
      .newBenchmark("text extraction", () -> {
        assertEquals("content", TextExtractor.findTextAt(file, offset1, TextContent.TextDomain.ALL).toString());
        assertNull(TextExtractor.findTextAt(file, offset2, TextContent.TextDomain.ALL));
      })
      .setup(() -> {
        myFixture.type(' ');
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments(); // drop file caches
      })
      .start();
  }

  public void testLargeXmlWithUnclosedDoctypePerformance() {
    String text = "<!DOCTYPE rules [\n<!ENTITY some \"x\">\n<rules> " +
                  IntStreamEx.range(0, 10_000).mapToObj(i -> "<tag> content" + i + "</tag>\n").joining() +
                  " </rules>";
    PsiFile file = myFixture.configureByText("a.xml", text);

    Benchmark
      .newBenchmark("text extraction", () -> {
        for (PsiElement element : SyntaxTraverser.psiTraverser(file)) {
          TextExtractor.findTextsAt(element, TextContent.TextDomain.ALL);
        }
      })
      .setup(() -> {
        myFixture.type(' ');
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments(); // drop file caches
      })
      .start();
  }

  private void checkHtmlXml(boolean inlineTagsSupported) {
    {
      TextContent text = extractText("a.html", "<b>abc</b>", 4);
      assertEquals(inlineTagsSupported ? "abc" : "|abc|", unknownOffsets(text));
      Assertions.assertArrayEquals(inlineTagsSupported ? new int[]{0, 3} : new int[0], text.markupOffsets());
    }
    assertEquals("|abc|", unknownOffsets(extractText("a.xml", "<b>abc</b>", 4)));

    {
      String text = "<b><![CDATA[\n   characters with markup\n]]>and without it</b>";
      assertEquals("characters with markup", unknownOffsets(extractText("a.xml", text, 22)));
      assertEquals("and without it|", unknownOffsets(extractText("a.xml", text, 45)));
    }

    assertEquals("abcd efg", unknownOffsets(extractText("a.xml", "<tag attr=\"abcd efg\"/>", 14)));
    assertEquals("comment", extractText("a.xml", "<!-- comment -->", 10).toString());

    assertEquals("top-level text", unknownOffsets(extractText("a.html", "top-level text", 2)));

    //nothing in HTML <code> tag
    assertNull(extractText("a.html", "<code>abc</code>def", 7));
    assertEquals("|def", unknownOffsets(extractText("a.html", "<code>abc</code>def", 18)));
    assertEquals("|abc|", unknownOffsets(extractText("a.xml", "<code>abc</code>", 6)));

    // exclude entities
    assertEquals(inlineTagsSupported ? "A | B\tC" : "|A | B|\t|C|",
                 unknownOffsets(extractText("a.html", "<b>A &amp; B &#9; C</b>", 4)));

    if (inlineTagsSupported) {
      String longHtml = "<body><a>Hello</a> <b>world</b><code>without code</code>!<div/>Another text.</body>";

      TextContent text = extractText("a.html", longHtml, 9);
      assertEquals("Hello world|", unknownOffsets(text));
      Assertions.assertArrayEquals(new int[]{0, 5, 6}, text.markupOffsets());

      assertEquals("|Another text.", unknownOffsets(extractText("a.html", longHtml, 70)));

      text = extractText("a.html", "<div>Hello <span>world</span>!</div>", 20);
      assertEquals("|Hello world!|", unknownOffsets(text));
      Assertions.assertArrayEquals(new int[]{6, 11}, text.markupOffsets());

      assertEquals("|Hello\nworld!|", unknownOffsets(extractText("a.html", "<div>\n  Hello\n  world!\n</div>", 20)));
      assertEquals("|def", unknownOffsets(extractText("a.html", "<div>abc</div>def", 16)));

      assertOrderedEquals(extractText("a.html", "<div>Hello world!</div>", 10).getRangesInFile(), new TextRange(5, 17));
    }
  }

  public void testBuildingPerformance_concatenation() {
    String text = "<a/>b".repeat(10_000);
    String expected = "b".repeat(10_000);
    PsiFile file = myFixture.configureByText("a.xml", text);
    TextContentBuilder builder = TextContentBuilder.FromPsi.excluding(e -> e instanceof XmlTag);
    Benchmark.newBenchmark("TextContent building with concatenation", () -> {
      assertEquals(expected, builder.build(file, TextContent.TextDomain.PLAIN_TEXT).toString());
    }).start();
  }

  public void testBuildingPerformance_removingIndents() {
    String text = "  b\n".repeat(10_000);
    String expected = "b\n".repeat(10_000).trim();
    PsiFile file = myFixture.configureByText("a.java", "/*\n" + text + "*/");
    PsiComment comment = assertInstanceOf(file.findElementAt(10), PsiComment.class);
    TextContentBuilder builder = TextContentBuilder.FromPsi.removingIndents(" ");
    Benchmark.newBenchmark("TextContent building with indent removing", () -> {
      assertEquals(expected, builder.build(comment, TextContent.TextDomain.COMMENTS).toString());
    }).start();
  }

  public void testBuildingPerformance_removingHtml() {
    String text = "b<unknownTag>x</unknownTag>".repeat(10_000);
    String expected = "b".repeat(10_000);
    PsiFile file = myFixture.configureByText("a.java", "/**\n" + text + "*/");
    PsiDocComment comment = PsiTreeUtil.findElementOfClassAtOffset(file, 10, PsiDocComment.class, false);
    TextExtractor extractor = new JavaTextExtractor();
    Benchmark.newBenchmark("TextContent building with HTML removal", () -> {
      assertEquals(expected, assertOneElement(extractor.buildTextContents(comment, TextContent.TextDomain.ALL)).toString());
    }).start();
  }

  public void testBuildingPerformance_removingNbsp() {
    String text = "b&nbsp;".repeat(10_000);
    String expected = StringsKt.trim("b ".repeat(10_000)).toString();
    PsiFile file = myFixture.configureByText("a.md", text);
    var psi = PsiTreeUtil.findElementOfClassAtOffset(file, 10, MarkdownParagraph.class, false);
    TextExtractor extractor = new MarkdownTextExtractor();
    Benchmark.newBenchmark("TextContent building with nbsp removal", () -> {
      assertEquals(expected, extractor.buildTextContent(psi, TextContent.TextDomain.ALL).toString());
    }).start();
  }

  public void testBuildingPerformance_longTextFragment() {
    String line = "here's some relative long text that helps make this text fragment a bit longer than it could have been otherwise";
    String text = ("\n\n\n" + line).repeat(10_000);
    String expected = (line + "\n\n\n").repeat(10_000).trim();
    PsiFile file = myFixture.configureByText("a.java", "class C { String s = \"\"\"\n" + text + "\"\"\"; }");
    var literal = PsiTreeUtil.findElementOfClassAtOffset(file, 100, PsiLiteralExpression.class, false);
    var extractor = new JavaTextExtractor();
    Benchmark.newBenchmark("TextContent building from a long text fragment", () -> {
      assertEquals(expected, assertOneElement(extractor.buildTextContents(literal, TextContent.TextDomain.ALL)).toString());
    }).start();
  }

  public void testBuildingPerformance_complexPsi() {
    String link = "{@link foo.bar.goo1.goo2.goo3.goo4.goo5.goo6.goo7#zoo(x.x1.x2.x3,y.y1.y2.y3,z.z1.z2.z3)}";
    var text = "/** @return something if " + link.repeat(10_000) + " is not too expensive */";
    PsiFile file = myFixture.configureByText("a.java", text);
    TextExtractor extractor = new JavaTextExtractor();
    PsiElement tag = PsiTreeUtil.findElementOfClassAtOffset(file, text.indexOf("something"), PsiDocTag.class, false);
    Benchmark.newBenchmark("TextContent building from complex PSI", () -> {
      for (int i = 0; i < 10; i++) {
        TextContent content = assertOneElement(extractor.buildTextContents(tag, TextContent.TextDomain.ALL));
        assertEquals("something if  is not too expensive", content.toString());
      }
    }).start();
  }


  public void testCachingWorks() {
    TextExtractor delegate = TextExtractor.EP.forLanguage(MarkdownLanguage.INSTANCE);
    var countingExtractor = new TextExtractor() {
      int count = 0;

      @Override
      protected @NotNull List<TextContent> buildTextContents(@NotNull PsiElement element,
                                                             @NotNull Set<TextContent.TextDomain> allowedDomains) {
        if (element instanceof MarkdownParagraph) {
          count++;
        }
        return delegate.buildTextContents(element, allowedDomains);
      }
    };
    TextExtractor.EP.addExplicitExtension(MarkdownLanguage.INSTANCE, countingExtractor);
    disposeOnTearDown(() -> TextExtractor.EP.removeExplicitExtension(MarkdownLanguage.INSTANCE, countingExtractor));

    PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText(
      "a.md", MarkdownFileType.INSTANCE,
      "[Before ![AltText](http://www.google.com.au/images/nav_logo7.png) after](http://google.com.au/)");
    for (int i = 0; i <= file.getTextLength(); i++) {
      TextExtractor.findTextAt(file, i, TextContent.TextDomain.ALL);
    }
    // should be invoked once, but allow some repetitions in case GC kicks in
    assertTrue(String.valueOf(countingExtractor.count), countingExtractor.count < 3);
  }

  private TextContent extractText(String fileName, String fileText, int offset) {
    return extractText(fileName, fileText, offset, getProject());
  }

  private List<TextContent> extractTexts(String fileName, String text, int offset, Class<? extends PsiElement> psi) {
    PsiFile file = createFile(fileName, text, getProject());
    return TextExtractor.findTextsAt(PsiTreeUtil.findElementOfClassAtOffset(file, offset, psi, false), TextContent.TextDomain.ALL);
  }

  private static PsiFile createFile(String fileName, String fileText, Project project) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    return PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, fileText);
  }

  public static TextContent extractText(String fileName, String fileText, int offset, Project project) {
    return TextExtractor.findTextAt(createFile(fileName, fileText, project), offset, TextContent.TextDomain.ALL);
  }
}
