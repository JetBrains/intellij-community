package com.intellij.psi;

import com.intellij.mock.MockDocument;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.psi.impl.TextBlock;
import junit.framework.TestCase;

public class TextBlockTest extends TestCase {
  private TextBlock myTextBlock;
  private MockDocument myDocument;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDocument = new MockDocument();
    myTextBlock = new TextBlock();
  }

  public void testIsEmpty_AfterCreate() throws Exception {
    assertTrue(myTextBlock.isEmpty());
  }

  public void testTextInserted_Once() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "", "xxx", 1, false));
    assertTrue(!myTextBlock.isEmpty());
    assertEquals(10, myTextBlock.getStartOffset());
    assertEquals(13, myTextBlock.getTextEndOffset());
    assertEquals(10, myTextBlock.getPsiEndOffset());
  }

  public void testReset() throws Exception {
    assertTrue(myTextBlock.isEmpty());
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "", "xxx", 1, false));
    assertTrue(!myTextBlock.isEmpty());
    myTextBlock.clear();
    assertTrue(myTextBlock.isEmpty());
  }

  public void testTextInserted_SecondNonAdjFragmentsAfter() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "", "xxx", 1, false));
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 20, "", "xxx", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(10, myTextBlock.getStartOffset());
    assertEquals(23, myTextBlock.getTextEndOffset());
    assertEquals(17, myTextBlock.getPsiEndOffset());
  }

  public void testTextInserted_SecondNonAdjFragmentsBefore() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "", "xxx", 1, false));
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 5, "", "xxx", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(5, myTextBlock.getStartOffset());
    assertEquals(16, myTextBlock.getTextEndOffset());
    assertEquals(10, myTextBlock.getPsiEndOffset());
  }

  public void testTextInserted_SecondAdjFragmentsAfter() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "", "xxx", 1, false));
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 13, "", "xxx", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(10, myTextBlock.getStartOffset());
    assertEquals(16, myTextBlock.getTextEndOffset());
    assertEquals(10, myTextBlock.getPsiEndOffset());
  }

  public void testTextInserted_SecondAdjFragmentsBefore() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "", "xxx", 1, false));
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "", "xxx", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(10, myTextBlock.getStartOffset());
    assertEquals(16, myTextBlock.getTextEndOffset());
    assertEquals(10, myTextBlock.getPsiEndOffset());
  }

  public void testTextDeleted_Once() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "xxx", "", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(10, myTextBlock.getStartOffset());
    assertEquals(10, myTextBlock.getTextEndOffset());
    assertEquals(13, myTextBlock.getPsiEndOffset());
  }

  public void testTextDeleted_SecondNonAdjFragmentAfter() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "xxx", "", 1, false));
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 17, "xxx", "", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(10, myTextBlock.getStartOffset());
    assertEquals(17, myTextBlock.getTextEndOffset());
    assertEquals(23, myTextBlock.getPsiEndOffset());
  }

  public void testTextDeleted_SecondNonAdjFragmentBefore() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "xxx", "", 1, false));
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 5, "xxx", "", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(5, myTextBlock.getStartOffset());
    assertEquals(7, myTextBlock.getTextEndOffset());
    assertEquals(13, myTextBlock.getPsiEndOffset());
  }

  public void testTextDeleted_SecondAdjFragment() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "xxx", "", 1, false));
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "xxx", "", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(10, myTextBlock.getStartOffset());
    assertEquals(10, myTextBlock.getTextEndOffset());
    assertEquals(16, myTextBlock.getPsiEndOffset());
  }

  public void testTextChanged_ChangedToTheSameSize_Once1() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "xxx", "xxx", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(10, myTextBlock.getStartOffset());
    assertEquals(13, myTextBlock.getTextEndOffset());
    assertEquals(13, myTextBlock.getPsiEndOffset());
  }

  public void testTextChanged_ChangedToTheSameSize_Once2() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 0, "xxx", "yyy", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(0, myTextBlock.getStartOffset());
    assertEquals(3, myTextBlock.getTextEndOffset());
    assertEquals(3, myTextBlock.getPsiEndOffset());
  }

  public void testTextChanged_ChangedToTheSameSize_SecondNonAdjFragmentAfter() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "xxx", "xxx", 1, false));
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 20, "xxx", "xxx", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(10, myTextBlock.getStartOffset());
    assertEquals(23, myTextBlock.getTextEndOffset());
    assertEquals(23, myTextBlock.getPsiEndOffset());
  }

  public void testTextChanged_ChangedToTheSameSize_SecondNonAdjFragmentBefore() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "xxx", "xxx", 1, false));
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 5, "xxx", "xxx", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(5, myTextBlock.getStartOffset());
    assertEquals(13, myTextBlock.getTextEndOffset());
    assertEquals(13, myTextBlock.getPsiEndOffset());
  }

  public void testTextChanged_ChangedToTheSameSize_SecondNonFragmentAfter() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "xxx", "xxx", 1, false));
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 13, "xxx", "xxx", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(10, myTextBlock.getStartOffset());
    assertEquals(16, myTextBlock.getTextEndOffset());
    assertEquals(16, myTextBlock.getPsiEndOffset());
  }

  public void testTextChanged_ChangedToTheSameSize_SecondNonFragmentBefore() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "xxx", "xxx", 1, false));
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 7, "xxx", "xxx", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(7, myTextBlock.getStartOffset());
    assertEquals(13, myTextBlock.getTextEndOffset());
    assertEquals(13, myTextBlock.getPsiEndOffset());
  }

  public void testTextChanged_ChangedToLessSize() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "xxx", "xx", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(10, myTextBlock.getStartOffset());
    assertEquals(12, myTextBlock.getTextEndOffset());
    assertEquals(13, myTextBlock.getPsiEndOffset());
  }

  public void testTextChanged_ChangedToGreaterSize() throws Exception {
    myTextBlock.documentChanged(new DocumentEventImpl(myDocument, 10, "xxx", "xxxx", 1, false));

    assertTrue(!myTextBlock.isEmpty());
    assertEquals(10, myTextBlock.getStartOffset());
    assertEquals(14, myTextBlock.getTextEndOffset());
    assertEquals(13, myTextBlock.getPsiEndOffset());
  }
}
