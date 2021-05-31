package com.intellij.grazie.text;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.StringLiteralManipulator;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.lang.regexp.RegExpLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class TextExtractionTest extends BasePlatformTestCase {
  public void testInlineLinkTextExtraction() {
    TextContent extracted = extractText("a.md", "* list [item](http://x) with a local link", 3);
    assertEquals("list item with a local link", extracted.toString());
    int prefix = "* ".length();
    assertEquals(prefix + "list [".length(), extracted.textOffsetToFile("list ".length()));
    assertEquals(prefix + "list [item".length(), extracted.textOffsetToFile("list item".length()));
  }

  public void testMergeAdjacentJavaComments() {
    String text = extractText("a.java", "//Hello. I are a very humble\n//persons.\n\nclass C {}", 4).toString();
    assertTrue(text, text.matches("Hello\\. I are a very humble\\spersons\\."));

    assertEquals("First line. Third line.", extractText("a.java",
      "// First line.\n" +
      "// \n" +
      "//   Third line.\n"
      , 4).toString());
  }

  public void testIgnorePropertyCommentStarts() {
    assertEquals("Hello World #42!", extractText("a.properties", "#\t Hello World #42!", 4).toString());
  }

  public void testJavadoc() {
    String docText = "/**\n" +
                     "* Hello {@link #foo},\n" +
                     "* here's an asterisk: *\n" +
                     "* and some {@code code}.\n" +
                     "* {@link #unknown} is unknown.\n" +
                     "* @param foo the text without the parameter name\n" +
                     "* @return the offset of {@link #bar} in something\n" +
                     " */";
    TextContent text = extractText("a.java", docText, 6);
    assertEquals("Hello ,\nhere's an asterisk: *\nand some .\nis unknown.", text.toString());
    assertEquals("Hello |,\nhere's an asterisk: *\nand some |.\n|is unknown.", TextContentTest.unknownOffsets(text));

    text = extractText("a.java", docText, docText.indexOf("the offset"));
    assertEquals("the offset of  in something", text.toString());

    text = extractText("a.java", docText, docText.indexOf("without"));
    assertEquals("the text without the parameter name", text.toString());
  }

  public void testJavaLiteral() {
    assertEquals("abc def", extractText("a.java", "class C { String s = \" abc def \"; }", 27).toString());
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

  private TextContent extractText(String fileName, String fileText, int offset) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, fileType, fileText);
    PsiElement leaf = file.findElementAt(offset);
    return TextExtractor.findTextAt(Objects.requireNonNull(leaf), TextContent.TextDomain.ALL);
  }
}
