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

import com.intellij.openapi.vcs.ex.Range;
import com.intellij.util.diff.FilesTooBigForDiffException;

/**
 * author: lesya
 */
public class RollbackTest extends BaseLineStatusTrackerTestCase{

  public void testUpToDateContent1() throws FilesTooBigForDiffException {
    createDocument("\n1\n2\n3\n4\n5\n6\n7");
    deleteString(0, 4);
    assertEquals("1\n2", myTracker.getVcsContent(getFirstRange()).toString());
  }

  public void testUpToDateContent2() throws FilesTooBigForDiffException {
    createDocument("\n1\n2\n3\n4\n5\n6\n7");
    deleteString(3, 5);
    assertEquals("2", myTracker.getVcsContent(getFirstRange()).toString());
  }

  public void testRollbackInserted1() throws FilesTooBigForDiffException {
    String initialContent = "1\n2\n3\n4";
    createDocument(initialContent);
    insertString(7, "\n5\n6");
    compareRanges();
    rollbackFirstChange(Range.INSERTED);
    assertEquals(initialContent, myDocument.getText());
    compareRanges();
    insertString(7, "\5\n6\n7\n");
    compareRanges();
    rollbackFirstChange(Range.MODIFIED);
    compareRanges();
  }

  public void testRollbackInserted2() throws FilesTooBigForDiffException {

    doTestRollback("1\n2\n3\n4", () -> insertString(0, "\n0\n"), Range.INSERTED);
  }

  public void testRollbackInserted3() throws FilesTooBigForDiffException {
    doTestRollback("1\n2\n3\n4\n5\n6", () -> insertString(5, "\n0\n"), Range.INSERTED);
  }

  public void testRollbackInserted4() throws FilesTooBigForDiffException {
    doTestRollback("1\n2\n3\n4\n5", () -> insertString(myDocument.getTextLength(), "\n"), Range.INSERTED);
  }

  public void testRollbackModified4() throws FilesTooBigForDiffException {
    doTestRollback("1\n2\n3\n4", () -> insertString(0, "\n0\n0"), Range.MODIFIED);

  }

  public void testRollbackModified1() throws FilesTooBigForDiffException {
    doTestRollback("1\n2\n3\n4\n5\n6\n7", () -> deleteString(0, 3), Range.MODIFIED);
  }

  public void testRollbackDeleted2() throws FilesTooBigForDiffException {
    doTestRollback("1\n2\n3\n4\n5\n6\n7", () -> deleteString(0, 4), Range.DELETED);
  }

  public void testRollbackDeleted3() throws FilesTooBigForDiffException {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", () -> deleteString(0, 3), Range.DELETED);
  }

  public void testRollbackDeleted4() throws FilesTooBigForDiffException {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", () -> deleteString(0, 4), Range.DELETED);
  }

  public void testRollbackModified5() throws FilesTooBigForDiffException {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", () -> deleteString(2, 5), Range.MODIFIED);
  }

  public void testRollbackDeleted6() throws FilesTooBigForDiffException {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", () -> deleteString(3, 5), Range.DELETED);
  }

  public void testRollbackModified7() throws FilesTooBigForDiffException {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", () -> deleteString(3, 6), Range.MODIFIED);
  }

  public void testRollbackDeleted8() throws FilesTooBigForDiffException {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", () -> deleteString(3, 7), Range.DELETED);
  }

  public void testRollbackDeleted9() throws FilesTooBigForDiffException {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", () -> deleteString(5, 13), Range.DELETED);
  }

  public void testRollbackEmptyLastLineDeletion() {
    String text1 = "1\n2\n3\n\n";
    String text2 = "1\n2\n3\n";
    createDocument(text2, text1);
    rollback(myTracker.getRanges().get(0));

    assertEquals(myDocument.getText(), text1);
    assertEmpty(myTracker.getRanges());
  }

  public void testSRC27943() throws FilesTooBigForDiffException {
    String initialContent = "<%@ taglib uri=\"/WEB-INF/sigpath.tld\" prefix=\"sigpath\" %>\n" +
                       "<%@ taglib uri=\"/WEB-INF/struts-html.tld\" prefix=\"html\" %>\n" +
                       "<%@ taglib uri=\"/WEB-INF/struts-bean.tld\" prefix=\"bean\" %>\n" +
                       "<%@ taglib uri=\"/WEB-INF/string.tld\" prefix=\"str\" %>\n" +
                       "<%@ taglib uri=\"/WEB-INF/regexp.tld\" prefix=\"rx\" %>";

    String newContent = "<%@ taglib uri=\"/tag_lib/sigpath.tld\" prefix=\"sigpath\" %>\n" +
                       "<%@ taglib uri=\"/tag_lib/struts-html.tld\" prefix=\"html\" %>\n" +
                       "<%@ taglib uri=\"/tag_lib/struts-bean.tld\" prefix=\"bean\" %>\n" +
                       "<%@ taglib uri=\"/tag_lib/string.tld\" prefix=\"str\" %>\n" +
                       "<%@ taglib uri=\"/tag_lib/regexp.tld\" prefix=\"rx\" %>";

    createDocument(initialContent);
    replaceString(0, initialContent.length(), newContent);
    compareRanges();
    rollbackFirstChange(Range.MODIFIED);
    assertEquals(initialContent, myDocument.getText());
    compareRanges();
  }

  public void testRollbackModified10() throws FilesTooBigForDiffException {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", () -> deleteString(6, 13), Range.MODIFIED);
  }

  public void testEmptyDocumentBug() throws Throwable {
    createDocument("");

    insertString(0, "adsf");
    rollbackFirstChange(Range.MODIFIED);
    compareRanges();
  }

  private void doTestRollback(String initialContent, Runnable modifyAction, byte expectedRangeType) throws FilesTooBigForDiffException {
    createDocument(initialContent);
    modifyAction.run();
    compareRanges();
    rollbackFirstChange(expectedRangeType);
    assertEquals(initialContent, myDocument.getText());
    compareRanges();
  }

  private void rollbackFirstChange(byte expectedRangeType) {
    final Range range = getFirstRange();
    assertEquals(expectedRangeType, range.getType());
    rollback(range);
  }

  private Range getFirstRange() {
    assertEquals(1, myTracker.getRanges().size());
    return myTracker.getRanges().get(0);
  }
}
