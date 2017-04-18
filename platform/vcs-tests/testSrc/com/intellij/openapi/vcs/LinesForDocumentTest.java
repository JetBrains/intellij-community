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
package com.intellij.openapi.vcs;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.testFramework.PlatformTestCase;

import java.util.Arrays;

/**
 * author: lesya
 */


public class LinesForDocumentTest extends PlatformTestCase {
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
