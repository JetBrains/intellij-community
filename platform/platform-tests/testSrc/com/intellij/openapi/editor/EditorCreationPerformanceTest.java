/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;

public class EditorCreationPerformanceTest extends AbstractEditorTest {
  public void testOpeningEditorWithManyLines() {
    Document document = EditorFactory.getInstance().createDocument(StringUtil.repeat(LOREM_IPSUM + '\n', 15000));

    PlatformTestUtil.startPerformanceTest("Editor creation", 130, () -> {
      Editor editor = EditorFactory.getInstance().createEditor(document);
      try {
        LOG.debug(String.valueOf(editor.getContentComponent().getPreferredSize()));
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor);
      }
    }).assertTiming();
  }

  public void testOpeningEditorWithLongLine() {
    Document document = EditorFactory.getInstance().createDocument(StringUtil.repeat(LOREM_IPSUM, 30000));

    PlatformTestUtil.startPerformanceTest("Editor creation", 2000, () -> {
      Editor editor = EditorFactory.getInstance().createEditor(document);
      try {
        EditorTestUtil.setEditorVisibleSize(editor, 100, 100);
        LOG.debug(String.valueOf(editor.getContentComponent().getPreferredSize()));
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor);
      }
    }).assertTiming();
  }
}
