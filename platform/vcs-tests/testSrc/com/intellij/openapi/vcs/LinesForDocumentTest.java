package com.intellij.openapi.vcs;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.testFramework.LightIdeaTestCase;

import java.util.Arrays;

/**
 * author: lesya
 */


public class LinesForDocumentTest extends LightIdeaTestCase {
  public void test() {
    doTest("", new String[]{""});
    doTest(" ", new String[]{" "});
    doTest("\n", new String[]{"",
      ""});
    doTest("\na\n", new String[]{"",
      "a",
      ""});
    doTest("\na", new String[]{"",
      "a"});
    doTest("a\n\nb", new String[]{"a",
      "",
      "b"});
    doTest("ab\ncd", new String[]{"ab",
      "cd"});
    doTest("ab\ncd\n", new String[]{"ab",
      "cd",
      ""});
    doTest("\nab\ncd", new String[]{"",
      "ab",
      "cd"});
    doTest("\nab\ncd\n", new String[]{"",
      "ab",
      "cd",
      ""});
  }

  private static void doTest(String text, String[] expectedLines) {
    Document document = EditorFactory.getInstance().createDocument(text);
    assertEquals(Arrays.asList(expectedLines), DiffUtil.getLines(document));
  }
}
