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
