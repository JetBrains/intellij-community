/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diff;

import com.intellij.openapi.editor.Document;
import com.intellij.testFramework.PlatformTestCase;

import java.util.Arrays;

public class SimpleContentTest extends PlatformTestCase {
  public void testEqualsAndDifferent() {
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

  public void testModifyContent() {
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
