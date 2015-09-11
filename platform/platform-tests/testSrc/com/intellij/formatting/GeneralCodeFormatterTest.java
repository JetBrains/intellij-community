package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

@NonNls
public class GeneralCodeFormatterTest extends LightPlatformTestCase {
  private int myRightMargin = 120;

  public void testDefaultContinuationIndent() throws Exception {
    doTest("defaultContinuationIndent", "aaa bbb ccc\nddd eee fff", "aaa bbb ccc\n" +
                                                                    "        ddd eee fff");
  }

  public void testContinuationIndent() throws Exception {
    doTest("4", "a\nb\nc", "a\n" +
                           "        b\n" +
                           "        c");
  }

  public void testContinuationIndent2() throws Exception{
    doTest("5", "a\nb\nc", "a\n" +
                           "    b\n" +
                           "    c");
  }

  public void testParentContinuationIndent() throws Exception {
    doTest("continuationIndent", "a\nb\nc\nd\ne\nf",
           "a\n" +
           "        b\n" +
           "        c\n" +
           "                d\n" +
           "                e\n" +
           "                        f");
  }

  public void test1() throws Exception {
    doTest("1", "aaa bbb\nccc ddd\neee\nfff",
           "aaa bbb\n" +
           "    ccc ddd\n" +
           "     eee\n" +
           "     fff");
  }

  public void test2() throws Exception {
    doTest("2", "aaa bbb\nccc ddd\neee\nfff", "aaa bbb\n" +
                                              "    ccc ddd\n" +
                                              "    eee\n" +
                                              "    fff");
  }
  
  public void testNestedIndents() throws IOException, JDOMException {
    String before = "xxx\n" +
                    "yyy";
    String after = "xxx\n" +
                   "                        yyy";
    doTest("nestedIndents", before, after);
  }

  public void testLastLineIndent() throws Exception{
    final String initialText = "a\n";
    final TestFormattingModel model = new TestFormattingModel(initialText);

    model.setRootBlock(new FormattingModelXmlReader(model).readTestBlock("lineIndent"));
    final CommonCodeStyleSettings.IndentOptions indentOptions = new CommonCodeStyleSettings.IndentOptions();
    indentOptions.CONTINUATION_INDENT_SIZE = 8;
    indentOptions.INDENT_SIZE = 4;
    indentOptions.LABEL_INDENT_SIZE = 1;
    final CodeStyleSettings settings = new CodeStyleSettings(false);
    settings.setDefaultRightMargin(myRightMargin);
    try {
      FormatterEx.getInstanceEx().adjustLineIndent(model, settings, indentOptions, initialText.length() - 1, new TextRange(0, initialText.length()));
    }
    catch (IncorrectOperationException e) {
      fail();
    }

    assertEquals("a\n    ", FormatterImpl.getText(model));

  }

  public void test22() throws Exception {
    doTest("2", "aaa bbb ccc ddd eee\nfff", "aaa bbb ccc ddd eee\n" +
                                            "    fff");

  }

  public void testSpaceProperties() throws Exception {
    doTest("3", 
           "aaa bbb ccc\n" +
           "ddd eee fff",
           "aaa  bbb  ccc\n" +
           "    ddd  eee\n" +
           "\n" +
           "     fff"
    );
  }

  public void testSimpleWrapping() throws Exception {
    doTest("wrapAlways", "aaa bbb ccc ddd eee fff", "aaa\n" +
                                                    "bbb\n" +
                                                    " ccc\n" +
                                                    " ddd\n" +
                                                    " eee\n" +
                                                    "         fff");
    myRightMargin = 16;
    doTest("wrapAsNeeded", "aaa bbb ccc ddd eee fff", "aaa  bbb  ccc\n" +
                                                      " ddd  eee  fff");
  }

  public void testWrapping() throws Exception {
    myRightMargin = 10;
    doTest("chopDownIfLong", "aaa bbb ccc ddd eee fff", "aaa\n" +
                                                        "        bbb\n" +
                                                        "         ccc\n" +
                                                        " ddd\n" +
                                                        " eee\n" +
                                                        "         fff");
    myRightMargin = 20;
    doTest("wrapAtMiddle", "aaa bbb ccc ddd eee fff", "aaa  bbb  ccc\n" +
                                                      " ddd  eee  fff");

  }

  public void testMultipleWrap() throws Exception {
    myRightMargin = 2;
    doTest("multipleWrap", "abc", "ab\n        c");
  }

  public void testDifferentWraps() throws Exception {
    myRightMargin = 10;
    doTest("differentWraps", "(ab)(cd)(ef)", "(ab)(cd)\n" +
                                             "(ef)");

  }

  public void testNestedCalls2() throws Exception{
    doTest("nestedCalls",
           "1 a2 b3\nc",
           "1 a2 b3\n" +
           "        c");

  }
  public void testRemoveAllSpaces() throws Exception{
    doTest("removeAllSpaces",
           "0 1\n2\n3\t4 5\n6 7 8\t9 ",
           "0123456789 ");
  }

  private void doTest(final String formattingModelName,
                      final String initial,
                      final String expected) throws IOException, JDOMException {
    final TestFormattingModel model = new TestFormattingModel(initial);

    model.setRootBlock(new FormattingModelXmlReader(model).readTestBlock(formattingModelName));
    final CommonCodeStyleSettings.IndentOptions indentOptions = new CommonCodeStyleSettings.IndentOptions();
    indentOptions.CONTINUATION_INDENT_SIZE = 8;
    indentOptions.INDENT_SIZE = 4;
    indentOptions.LABEL_INDENT_SIZE = 1;
    final CodeStyleSettings settings = new CodeStyleSettings(false);
    settings.setDefaultRightMargin(myRightMargin);
    try {
      FormatterEx.getInstanceEx().format(model, settings, indentOptions, indentOptions, null);
    }
    catch (IncorrectOperationException e) {
      fail();
    }

    assertEquals(expected, FormatterImpl.getText(model));
  }


}
