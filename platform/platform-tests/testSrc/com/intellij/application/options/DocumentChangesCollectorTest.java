package com.intellij.application.options;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.impl.TextChangeImpl;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 10/12/2010
 */
public class DocumentChangesCollectorTest {

  private static final String TEXT = "0123456789abcdefghijklmnopqrstuvwxyz";

  private static long ourCounter;

  private StringBuilder buffer;
  private DocumentChangesCollector myCollector;  
  private Mockery myMockery;
  private Document myDocument;
    
  @Before
  public void setUp() {
    myCollector = new DocumentChangesCollector();
    buffer = new StringBuilder(TEXT);
    myCollector.setCollectChanges(true);
    myMockery = new JUnit4Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};

    myDocument = myMockery.mock(Document.class);

    myMockery.checking(new Expectations() {{
      allowing(myDocument).getTextLength(); will(returnValue(TEXT.length()));
    }});
  }
  
  @After
  public void checkExpectations() {
    myMockery.assertIsSatisfied();
  }

  @Test
  public void emptyCollector() {
    assertNotNull(myCollector.getChanges());
  }

  @Test
  public void scatteredChanges() {
    populate(0, 0, "AB");
    populate(5, 5, "CD");
    checkResult(new TextChangeImpl("", 0, 2), new TextChangeImpl("", 5, 7));
  }

  @Test
  public void insertAndReplaceAtEnd() {
    populate(0, 0, "AB");
    populate(1, 4, "CD");
    checkResult(new TextChangeImpl("01", 0, 3));
  }

  @Test
  public void insertAndReplaceAtStart() {
    populate(2, 2, "AB");
    populate(1, 3, "CD");
    checkResult(new TextChangeImpl("1", 1, 4));
  }

  @Test
  public void removeAndReplace() {
    populate(1, 3, "");
    populate(1, 4, "ABC");
    checkResult(new TextChangeImpl("12345", 1, 4));
  }

  @Test
  public void removedChangeWithNonStrictBoundaryMatch() {
    populate(3, 5, "AB");
    populate(2, 4, "");
    checkResult(new TextChangeImpl("234", 2, 3));
  }

  @Test
  public void removedChangeWithStrictBoundaryMatch() {
    populate(3, 5, "AB");
    populate(3, 5, "");
    checkResult(new TextChangeImpl("34", 3, 3));
  }

  @Test
  public void cutChangeFromEnd() {
    populate(3, 3, "ABCD");
    populate(4, 8, "");
    checkResult(new TextChangeImpl("3", 3, 4));
  }

  @Test
  public void cutChangeFromStart() {
    populate(3, 3, "ABCD");
    populate(2, 4, "");
    checkResult(new TextChangeImpl("2", 2, 5));
  }

  @Test
  public void cutInTheMiddleOfInsertedText() {
    populate(3, 3, "ABCDEF");
    populate(5, 8, "");
    checkResult(new TextChangeImpl("", 3, 6));
  }

  @Test
  public void cutInTheMiddleOfReplacedText() {
    populate(3, 9, "ABCDEF");
    populate(5, 8, "");
    checkResult(new TextChangeImpl("345678", 3, 6));
  }

  @Test
  public void cutMultipleChanges() {
    populate(3, 3, "ABC");
    populate(8, 8, "DEF");
    populate(5, 9, "");
    checkResult(new TextChangeImpl("34", 3, 7));
  }

  @Test
  public void mergeChangeFromStart() {
    populate(3, 3, "ABC");
    populate(4, 4, "DEF");
    checkResult(new TextChangeImpl("", 3, 9));
  }

  @Test
  public void mergeChangeFromEnd() {
    populate(3, 3, "AB");
    populate(1, 4, "CDEF");
    checkResult(new TextChangeImpl("12", 1, 6));
  }

  @Test
  public void mergeMultipleChangesInOne() {
    populate(3, 3, "AB");
    populate(9, 9, "CD");
    populate(5, 9, "EFGH");
    checkResult(new TextChangeImpl("3456", 3, 11));
  }

  @Test
  public void mergeChangesSequence() {
    populate(1, 1, "AB");
    populate(3, 3, "CD");
    populate(5, 5, "EFGH");
    checkResult(new TextChangeImpl("", 1, 9));
  }

  @Test
  public void replaceAndInsertAtEnd() {
    populate(4, 8, "ABCD");
    populate(5, 5, "EFGHIJKL");
    checkResult(new TextChangeImpl("4567", 4, 16));
  }

  private void populate(int start, int end, String newText) {
    DocumentEventImpl event = new DocumentEventImpl(
      myDocument, start, buffer.substring(start, end), newText, ++ourCounter, false
    );
    buffer.replace(start, end, newText);
    myCollector.beforeDocumentChange(event);
    myCollector.documentChanged(event);
  }

  private void checkResult(TextChange ... expected) {
    List<? extends TextChange> actual = myCollector.getChanges();
    assertEquals(asList(expected), actual);
  }
}