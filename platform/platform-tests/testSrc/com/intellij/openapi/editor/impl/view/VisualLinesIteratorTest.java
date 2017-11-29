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
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.impl.EditorImpl;

public class VisualLinesIteratorTest extends AbstractEditorTest {
  public void testWrapAfterFolding() {
    initText("abcdefghij");
    addCollapsedFoldRegion(1, 8, "...");
    configureSoftWraps(4);
    
    verifySoftWrapPositions(8);
    
    VisualLinesIterator it = createIterator(0);
    assertFalse(it.atEnd());
    assertEquals(0, it.getVisualLineStartOffset());
    assertEquals(0, it.getStartFoldingIndex());
    it.advance();
    assertFalse(it.atEnd());
    assertEquals(8, it.getVisualLineStartOffset());
    assertEquals(1, it.getStartFoldingIndex());
  }
  
  public void testWrapAtFoldedLineBreak() {
    initText("abc\ndef");
    addCollapsedFoldRegion(3, 4, "...");
    configureSoftWraps(6);
    
    verifySoftWrapPositions(4);
    VisualLinesIterator it = createIterator(0);
    assertFalse(it.atEnd());
    it.advance();
    assertFalse(it.atEnd());
    it.advance();
    assertTrue(it.atEnd());
  }
  
  private static VisualLinesIterator createIterator(int startVisualLine) {
    return new VisualLinesIterator((EditorImpl)myEditor, startVisualLine);
  }
}