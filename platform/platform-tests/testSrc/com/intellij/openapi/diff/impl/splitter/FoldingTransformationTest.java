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
package com.intellij.openapi.diff.impl.splitter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;


public class FoldingTransformationTest extends FoldingTestCase {
  private Editor myEditor;
  private Document myDocument;
  private Transformation myTransformation;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myEditor = createEditor();
    myDocument = myEditor.getDocument();
  }

  @Override
  protected void tearDown() throws Exception {
    myEditor = null;
    myDocument = null;
    myTransformation = null;
    super.tearDown();
  }

  private void createTransformation() {
    myTransformation = new FoldingTransformation(myEditor);
  }

  public void testNoFolding() {
    createTransformation();
    Interval interval = DividerPolygon.getVisibleInterval(myEditor);
    for (int i = interval.getStart(); i < Math.min(myDocument.getLineCount(), interval.getEnd()); i++) {
      assertEquals(i * getLineHeight(), myTransformation.transform(i));
    }
  }

  private int getLineHeight() {
    return myEditor.getLineHeight();
  }

  public void testFolderRegion() {
    addFolding(myEditor, 3, 7);
    createTransformation();
    int start = myTransformation.transform(3);
    int lineHeight = getLineHeight();
    assertEquals(3 * lineHeight, start);
    int end = myTransformation.transform(8);
    assertEquals(4 * lineHeight, end);
    int middle = myTransformation.transform(5);
    assertEquals((double)(end + start) / 2, middle, 0.5);
    int below = myTransformation.transform(4);
    assertTrue(below < middle);
    int above = myTransformation.transform(6);
    assertTrue(above > middle);
    assertTrue(above < end);
    assertTrue(below > start);
    assertEquals(start - lineHeight, myTransformation.transform(2));
    assertEquals(end + lineHeight, myTransformation.transform(9));
  }
}
