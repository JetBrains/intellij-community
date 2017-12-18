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
package com.intellij.openapi.vcs

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.vcs.ex.Range

class RollbackTest : BaseLineStatusTrackerTestCase() {

  fun testUpToDateContent1() {
    createDocument("\n1\n2\n3\n4\n5\n6\n7")
    deleteString(0, 4)
    assertEquals("1\n2", getVcsContent(getFirstRange()).toString())
  }

  fun testUpToDateContent2() {
    createDocument("\n1\n2\n3\n4\n5\n6\n7")
    deleteString(3, 5)
    assertEquals("2", getVcsContent(getFirstRange()).toString())
  }

  fun testRollbackInserted1() {
    val initialContent = "1\n2\n3\n4"
    createDocument(initialContent)
    insertString(7, "\n5\n6")
    compareRanges()
    rollbackFirstChange(Range.INSERTED)
    assertTextContentIs(initialContent)
    compareRanges()
    insertString(7, "\u0005\n6\n7\n")
    compareRanges()
    rollbackFirstChange(Range.MODIFIED)
    compareRanges()
  }

  fun testRollbackInserted2() {

    doTestRollback("1\n2\n3\n4", { insertString(0, "\n0\n") }, Range.INSERTED)
  }

  fun testRollbackInserted3() {
    doTestRollback("1\n2\n3\n4\n5\n6", { insertString(5, "\n0\n") }, Range.INSERTED)
  }

  fun testRollbackInserted4() {
    doTestRollback("1\n2\n3\n4\n5", { insertString(myDocument.textLength, "\n") }, Range.INSERTED)
  }

  fun testRollbackModified4() {
    doTestRollback("1\n2\n3\n4", { insertString(0, "\n0\n0") }, Range.MODIFIED)

  }

  fun testRollbackModified1() {
    doTestRollback("1\n2\n3\n4\n5\n6\n7", { deleteString(0, 3) }, Range.MODIFIED)
  }

  fun testRollbackDeleted2() {
    doTestRollback("1\n2\n3\n4\n5\n6\n7", { deleteString(0, 4) }, Range.DELETED)
  }

  fun testRollbackDeleted3() {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", { deleteString(0, 3) }, Range.DELETED)
  }

  fun testRollbackDeleted4() {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", { deleteString(0, 4) }, Range.DELETED)
  }

  fun testRollbackModified5() {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", { deleteString(2, 5) }, Range.MODIFIED)
  }

  fun testRollbackDeleted6() {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", { deleteString(3, 5) }, Range.DELETED)
  }

  fun testRollbackModified7() {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", { deleteString(3, 6) }, Range.MODIFIED)
  }

  fun testRollbackDeleted8() {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", { deleteString(3, 7) }, Range.DELETED)
  }

  fun testRollbackDeleted9() {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", { deleteString(5, 13) }, Range.DELETED)
  }

  fun testRollbackEmptyLastLineDeletion() {
    val text1 = "1\n2\n3\n\n"
    val text2 = "1\n2\n3\n"
    createDocument(text2, text1)
    rollback(myTracker.getRanges()!![0])

    assertTextContentIs(text1)
    assertEmpty(myTracker.getRanges()!!)
  }

  fun testSRC27943() {
    val initialContent = "<%@ taglib uri=\"/WEB-INF/sigpath.tld\" prefix=\"sigpath\" %>\n" +
                         "<%@ taglib uri=\"/WEB-INF/struts-html.tld\" prefix=\"html\" %>\n" +
                         "<%@ taglib uri=\"/WEB-INF/struts-bean.tld\" prefix=\"bean\" %>\n" +
                         "<%@ taglib uri=\"/WEB-INF/string.tld\" prefix=\"str\" %>\n" +
                         "<%@ taglib uri=\"/WEB-INF/regexp.tld\" prefix=\"rx\" %>"

    val newContent = "<%@ taglib uri=\"/tag_lib/sigpath.tld\" prefix=\"sigpath\" %>\n" +
                     "<%@ taglib uri=\"/tag_lib/struts-html.tld\" prefix=\"html\" %>\n" +
                     "<%@ taglib uri=\"/tag_lib/struts-bean.tld\" prefix=\"bean\" %>\n" +
                     "<%@ taglib uri=\"/tag_lib/string.tld\" prefix=\"str\" %>\n" +
                     "<%@ taglib uri=\"/tag_lib/regexp.tld\" prefix=\"rx\" %>"

    createDocument(initialContent)
    replaceString(0, initialContent.length, newContent)
    compareRanges()
    rollbackFirstChange(Range.MODIFIED)
    assertTextContentIs(initialContent)
    compareRanges()
  }

  fun testRollbackModified10() {
    doTestRollback("\n1\n2\n3\n4\n5\n6\n7", { deleteString(6, 13) }, Range.MODIFIED)
  }

  fun testEmptyDocumentBug() {
    createDocument("")

    insertString(0, "adsf")
    rollbackFirstChange(Range.MODIFIED)
    compareRanges()
  }

  private fun doTestRollback(initialContent: String, modifyAction: () -> Unit, expectedRangeType: Byte) {
    createDocument(initialContent)
    modifyAction()
    compareRanges()
    rollbackFirstChange(expectedRangeType)
    assertTextContentIs(initialContent)
    compareRanges()
  }
  private fun rollbackFirstChange(expectedRangeType: Byte) {
    val range = getFirstRange()
    assertEquals(expectedRangeType, range.type)
    rollback(range)
  }

  private fun getFirstRange(): Range {
    assertEquals(1, myTracker.getRanges()!!.size)
    return myTracker.getRanges()!![0]
  }

  private fun getVcsContent(range: Range): CharSequence {
    return DiffUtil.getLinesContent(myTracker.vcsDocument, range.vcsLine1, range.vcsLine2)
  }
}
