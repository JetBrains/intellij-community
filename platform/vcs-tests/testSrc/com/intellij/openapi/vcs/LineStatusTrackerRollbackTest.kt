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

import com.intellij.openapi.vcs.ex.Range

class LineStatusTrackerRollbackTest : BaseLineStatusTrackerTestCase() {
  fun testUpToDateContent1() {
    test("_1_2_3_4_5_6_7") {
      "_1_2".delete()

      range().assertVcsContent("1_2")
    }
  }

  fun testUpToDateContent2() {
    test("_1_2_3_4_5_6_7") {
      "2_".delete()

      range().assertVcsContent("2")
    }
  }

  fun testRollbackInserted1() {
    test("1_2_3_4") {
      "4".insertAfter("_5_6")
      compareRanges()

      range().assertType(Range.INSERTED)
      range().rollback()
      assertTextContentIs("1_2_3_4")
      compareRanges()

      "4".insertAfter("\u0005_6_7_")
      compareRanges()

      range().assertType(Range.MODIFIED)
      range().rollback()
      compareRanges()
    }
  }

  fun testRollbackInserted2() {
    doTestRollback("1_2_3_4", { insertAtStart("_0_") }, Range.INSERTED)
  }

  fun testRollbackInserted3() {
    doTestRollback("1_2_3_4_5_6", { "3".insertAfter("_0_") }, Range.INSERTED)
  }

  fun testRollbackInserted4() {
    doTestRollback("1_2_3_4_5", { "5".insertAfter("_") }, Range.INSERTED)
  }

  fun testRollbackModified4() {
    doTestRollback("1_2_3_4", { insertAtStart("_0_0") }, Range.MODIFIED)
  }

  fun testRollbackModified1() {
    doTestRollback("1_2_3_4_5_6_7", { "1_2".delete() }, Range.MODIFIED)
  }

  fun testRollbackDeleted2() {
    doTestRollback("1_2_3_4_5_6_7", { "1_2_".delete() }, Range.DELETED)
  }

  fun testRollbackDeleted3() {
    doTestRollback("_1_2_3_4_5_6_7", { "_1_".delete() }, Range.DELETED)
  }

  fun testRollbackDeleted4() {
    doTestRollback("_1_2_3_4_5_6_7", { "_1_2".delete() }, Range.DELETED)
  }

  fun testRollbackModified5() {
    doTestRollback("_1_2_3_4_5_6_7", { "_2_".delete() }, Range.MODIFIED)
  }

  fun testRollbackDeleted6() {
    doTestRollback("_1_2_3_4_5_6_7", { "2_".delete() }, Range.DELETED)
  }

  fun testRollbackModified7() {
    doTestRollback("_1_2_3_4_5_6_7", { "2_3".delete() }, Range.MODIFIED)
  }

  fun testRollbackDeleted8() {
    doTestRollback("_1_2_3_4_5_6_7", { "2_3_".delete() }, Range.DELETED)
  }

  fun testRollbackDeleted9() {
    doTestRollback("_1_2_3_4_5_6_7", { "3_4_5_6_".delete() }, Range.DELETED)
  }

  fun testRollbackEmptyLastLineDeletion() {
    val text1 = "1_2_3__"
    val text2 = "1_2_3_"
    test(text2, text1) {
      range(0).rollback()

      assertTextContentIs(text1)
      assertRangesEmpty()
    }
  }

  fun testSRC27943() {
    val initialContent = "<%@ taglib uri=\"/WEB-INF/sigpath.tld\" prefix=\"sigpath\" %>_" +
                         "<%@ taglib uri=\"/WEB-INF/struts-html.tld\" prefix=\"html\" %>_" +
                         "<%@ taglib uri=\"/WEB-INF/struts-bean.tld\" prefix=\"bean\" %>_" +
                         "<%@ taglib uri=\"/WEB-INF/string.tld\" prefix=\"str\" %>_" +
                         "<%@ taglib uri=\"/WEB-INF/regexp.tld\" prefix=\"rx\" %>"

    val newContent = "<%@ taglib uri=\"/tag_lib/sigpath.tld\" prefix=\"sigpath\" %>_" +
                     "<%@ taglib uri=\"/tag_lib/struts-html.tld\" prefix=\"html\" %>_" +
                     "<%@ taglib uri=\"/tag_lib/struts-bean.tld\" prefix=\"bean\" %>_" +
                     "<%@ taglib uri=\"/tag_lib/string.tld\" prefix=\"str\" %>_" +
                     "<%@ taglib uri=\"/tag_lib/regexp.tld\" prefix=\"rx\" %>"

    test(initialContent) {
      replaceWholeText(newContent)
      compareRanges()

      range().assertType(Range.MODIFIED)
      range().rollback()
      assertTextContentIs(initialContent)
      compareRanges()
    }
  }

  fun testRollbackModified10() {
    doTestRollback("_1_2_3_4_5_6_7", { "_4_5_6_".delete() }, Range.MODIFIED)
  }

  fun testEmptyDocumentBug() {
    test("") {
      insertAtStart("adsf")
      range().assertType(Range.MODIFIED)
      range().rollback()
      compareRanges()
    }
  }

  private fun doTestRollback(initialContent: String, modifyAction: Test.() -> Unit, expectedRangeType: Byte) {
    test(initialContent) {
      modifyAction()
      compareRanges()

      range().assertType(expectedRangeType)
      range().rollback()

      assertTextContentIs(initialContent)
      compareRanges()
    }
  }
}
