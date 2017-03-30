package com.intellij.openapi.diff;

import com.intellij.openapi.editor.Document;
import com.intellij.testFramework.IdeaTestCase;

import java.io.IOException;
import java.util.Arrays;

public class SimpleContentTest extends IdeaTestCase {
  public void testEqualsAndDifferent() throws IOException {
    SimpleContent content1 = new SimpleContent("a\nb");
    SimpleContent content2 = new SimpleContent("a\nb");
    assertTrue(Arrays.equals(content1.getBytes(), content2.getBytes()));
    assertEquals(content1.getText(), content2.getText());
    assertEquals(content1.getDocument().getText(), content2.getDocument().getText());

    content1 = new SimpleContent("a\nb");
    content2 = new SimpleContent("a\r\nb");
    assertFalse(Arrays.equals(content1.getBytes(), content2.getBytes()));
    assertFalse(content1.getText().equals(content2.getText()));
    assertTrue(content1.getDocument().getText().equals(content2.getDocument().getText()));

    content1 = new SimpleContent("a\nb");
    content2 = new SimpleContent("a\nc");
    assertFalse(Arrays.equals(content1.getBytes(), content2.getBytes()));
    assertFalse(content1.getText().equals(content2.getText()));
    assertFalse(content1.getDocument().getText().equals(content2.getDocument().getText()));

    SimpleContent content = new SimpleContent("a\nb\r\nc");
    assertTrue(Arrays.equals("a\nb\r\nc".getBytes(), content.getBytes()));
  }

  public void testModifyContent() throws IOException {
    SimpleContent content = new SimpleContent("abc\r\ndef");
    content.setReadOnly(false);
    String originalText = content.getText();
    byte[] originalBytes = content.getBytes();
    Document document = content.getDocument();
    BaseDiffTestCase.replaceString2(myProject, document, 0, 3, "123");

    String newText = "123\r\ndef";
    assertEquals(newText, content.getText());
    assertEquals("123\ndef", content.getDocument().getText());
    assertTrue(Arrays.equals(newText.getBytes(), content.getBytes()));

    BaseDiffTestCase.replaceString2(myProject, document, 0, 3, "abc");
    assertEquals("abc\ndef", document.getText());
    assertEquals(originalText, content.getText());
    assertEquals(originalBytes, content.getBytes());
  }
}
