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

class LineStatusTrackerModifyDocumentTest : BaseLineStatusTrackerTestCase() {
  fun testSimpleInsert() {
    test("1234_2345_3456") {
      "12".insertAfter("a")
      assertRanges(Range(0, 1, 0, 1))
      compareRanges()
    }
  }

  fun testUndo() {
    test("1234_2345_3456") {
      "1234_23".insertAfter("a")
      assertRanges(Range(1, 2, 1, 2))
      compareRanges()

      "a".delete()
      compareRanges()
    }
  }

  fun testLineEndBeforeModification() {
    test("1234_2345_3456") {
      "1234_2".insertAfter("a")
      assertRanges(Range(1, 2, 1, 2))
      compareRanges()

      "1234_".insertAfter("_")
      assertRanges(Range(1, 3, 1, 2))
      compareRanges()
    }
  }

  fun testLineEndBeforeModification2() {
    test("1234_2345_3456") {
      "1234_2".insertAfter("a")
      compareRanges()

      "1234".insertAfter("_")
      compareRanges()
    }
  }

  fun testInsertDoubleEnterAtEnd() {
    test("1") {
      "1".insertAfter("_")
      compareRanges()

      "1_".insertAfter("_")
      compareRanges()
    }
  }

  fun testSimpleInsertAndWholeReplace() {
    test("1234_2345_3456") {
      "12".insertAfter("a")
      compareRanges()

      replaceWholeText(" ")
      compareRanges()
    }
  }

  fun testSimpleInsert2() {
    test("1_2_3_4_5") {
      "2_".insertAfter("a")
      compareRanges()
    }
  }

  fun testSimpleInsertToEmpty() {
    test("") {
      insertAtStart("a")
      compareRanges()
    }
  }

  fun testDoubleSimpleInsert() {
    test("1234_2345_3456") {
      "12".insertAfter("a")
      compareRanges()

      "12".insertAfter("a")
      compareRanges()
    }
  }

  fun testInsertEnter() {
    test("1234_2345_3456") {
      "12".insertAfter("_")
      compareRanges()
    }
  }

  fun testSimpleInsertAndEnterToEmpty() {
    test("") {
      insertAtStart("a")
      compareRanges()

      "a".insertAfter("_")
      compareRanges()
    }
  }

  fun testInsertEnterAtEnter() {
    test("1234_2345_3456") {
      "1234".insertAfter("_")
      compareRanges()
    }
  }


  fun testInsertEnterAtEnterAndSimpleInsert() {
    test("1234_2345_3456") {
      "1234".insertAfter("_")
      compareRanges()

      "1234_".insertAfter("a")
      compareRanges()
    }
  }

  fun testInsertDoubleEnterAtEnters() {
    test("1234_2345_3456") {
      "1234".insertAfter("_")
      compareRanges()

      "2345".insertAfter("_")
      compareRanges()
    }
  }

  fun testInsertEnterAndSpaceAfterEnter() {
    test("12345_12345_12345") {
      (1 th "12345").insertAfter("_ ")
      compareRanges()
    }
  }

  fun testInsertEnterAndDeleteEnter1() {
    test("12345_12345_12345") {
      (1 th "12345").insertAfter("_")
      compareRanges()

      (1 th "_").delete()
      compareRanges()
    }
  }

  fun testInsertEnterAndDeleteEnter2() {
    test("12345_12345_12345") {
      (1 th "12345").insertAfter("_")
      compareRanges()

      (2 th "_").delete()
      compareRanges()
    }
  }

  fun testSimpleDelete() {
    test("1234_2345_3456") {
      (1 th "3").delete()
      compareRanges()
    }
  }

  fun testDeleteLine() {
    test("1234_2345_3456") {
      "1234_".delete()
      compareRanges()
    }
  }

  fun testDoubleDelete() {
    test("1234_2345_3456") {
      (1 th "3").delete()
      compareRanges()

      (1 th "4").delete()
      compareRanges()
    }
  }

  fun testDeleteEnter() {
    test("12345_23456_34567") {
      (1 th "_").delete()
      compareRanges()
    }
  }

  fun testDeleteDoubleEnter() {
    test("12345__23456_34567") {
      (1 th "_").delete()
      compareRanges()
    }
  }

  fun testDoubleInsertToClass() {
    test("class A{__}") {
      "class A{_".insertAfter("a")
      compareRanges()

      "class A{_a".insertAfter("a")
      compareRanges()
    }
  }

  fun testInsertSymbolAndEnterToClass() {
    test("class A{__}") {
      "class A{_".insertAfter("a")
      compareRanges()

      "class A{_a".insertAfter("_")
      compareRanges()
    }
  }

  fun testMultiLineReplace2() {
    test("012a_012b_012c") {
      "_012b".replace("_x_y_z")
      compareRanges()
    }
  }

  fun testChangedLines1() {
    test("class A{_x_a_b_c_}",
         "class A{_1_x_2_}") {
      compareRanges()
    }
  }

  fun testMultiLineReplace1() {
    test("012a_012b_012c_012d_012e") {
      ("0" `in` "012b").replace("a")
      compareRanges()

      "_a12b_012c".replace("_x")
      compareRanges()
    }
  }

  fun testInsertAndModify() {
    test("a_b_c_d") {
      "a_b".insertAfter("_")
      compareRanges()

      "a_b_".insertAfter("_")
      compareRanges()

      "a_b___".insertAfter(" ")
      compareRanges()
    }
  }

  fun testRangesShouldMerge() {
    test("1_2_3_4") {
      "1".insertAfter("1")
      compareRanges()

      "3".insertAfter("3")
      compareRanges()

      "11_".insertAfter("2")
      compareRanges()
    }
  }

  fun testShiftRangesAfterChange() {
    test("1_2_3_4") {
      "4".insertAfter("4")
      compareRanges()

      insertAtStart("_")
      compareRanges()

      insertAtStart("_")
      compareRanges()

      insertAtStart("_")
      compareRanges()
    }
  }

  fun testInsertBeforeChange() {
    test("   11_   3 _   44_   55_   6_   7_   88_   ",
         "   1_   2_   3 _   4_   5_   6_   7_   8_   ") {
      "11_   ".insertAfter("3")
      assertTextContentIs("   11_   33 _   44_   55_   6_   7_   88_   ")
      compareRanges()

      "11_   ".insertAfter("aaa_bbbbbbbb_cccc_dddd")
      compareRanges()
    }
  }

  fun testUndoDeletion() {
    test("1_2_3_4_5_6_7_") {
      "3_".delete()
      assertTextContentIs("1_2_4_5_6_7_")
      compareRanges()

      "2_".insertAfter("3_")
      compareRanges()
    }
  }

  fun testUndoDeletion2() {
    test("1_2_3_4_5_6_7_") {
      "_3".delete()
      assertTextContentIs("1_2_4_5_6_7_")
      compareRanges()

      "2_".insertAfter("_3")
      compareRanges()
    }
  }

  fun testSRC17123() {
    test(
      "package package;_" +
      "_" +
      "public class Class3 {_" +
      "    public int i1;_" +
      "    public int i2;_" +
      "    public int i3;_" +
      "    public int i4;_" +
      "_" +
      "    public static void main(String[] args) {_" +
      "_" +
      "    }_" +
      "}"
    ) {
      "_    public int i1;".delete()
      compareRanges()

      assertTextContentIs(
        "package package;_" +
        "_" +
        "public class Class3 {_" +
        "    public int i2;_" +
        "    public int i3;_" +
        "    public int i4;_" +
        "_" +
        "    public static void main(String[] args) {_" +
        "_" +
        "    }_" +
        "}")

      ("_" +
       "    public int i2;_" +
       "    public int i3;_" +
       "    public int i4;_" +
       "_" +
       "    public static void main(String[] args) {_" +
       "_" +
       "    }_" +
       "}").delete()
      compareRanges()

      "{".delete()
      compareRanges()
    }
  }

  fun testUnexpetedDeletedRange() {
    test("    public class_    bbb_") {
      "    public class_".insertAfter("    _")
      assertTextContentIs("    public class_    _    bbb_")
      compareRanges()

      ("    " after "public class_").delete()
      assertTextContentIs("    public class__    bbb_")
      compareRanges()

      "    public class__".insertAfter("    _")
      assertTextContentIs("    public class__    _    bbb_")
      compareRanges()

      ("    " after "public class__").delete()
      assertTextContentIs("    public class___    bbb_")
      compareRanges()

      "public".delete()
      assertTextContentIs("     class___    bbb_")
      compareRanges()

      " class___".insertBefore("p")
      assertTextContentIs("    p class___    bbb_")
      compareRanges()

      "    p".insertAfter("r")
      assertTextContentIs("    pr class___    bbb_")
      compareRanges()

      "    pr".insertAfter("i")
      assertTextContentIs("    pri class___    bbb_")
      compareRanges()
    }
  }

  fun testSrc29814() {
    test("111_222_333_") {
      "111_222_333_".delete()
      assertTextContentIs("")
      compareRanges()

      insertAtStart("222_")
      compareRanges()

      "222_".delete()
      compareRanges()

      insertAtStart("111_222_333_")
      compareRanges()
    }
  }

  fun testDeletingTwoMethods() {
    val part1 = "class Foo {_  public void method1() {_    // something_  }__"
    val part2 = "  public void method2() {_    // something_  }__  public void method3() {_    // something_  }_"
    val part3 = "_  public void method4() {_    // something_  }_}"

    test(part1 + part2 + part3) {
      (part2).delete()
      assertTextContentIs(part1 + part3)
      assertRanges(Range(5, 5, 5, 12))

      ("_" at !part1.length - (part1.length + 1)).delete()
      compareRanges()
    }
  }

  fun testBug1() {
    test("1_2_3_4_") {
      "3_".delete()
      compareRanges()

      "1_2".insertAfter("X")
      compareRanges()
    }
  }

  fun testBug2() {
    test("1_2_3_4_5_6_") {
      "3_".delete()
      compareRanges()

      "6_".delete()
      compareRanges()

      "1_2_".insertAfter("3_8_")
      compareRanges()
    }
  }

  fun testBug3() {
    test("__00_556_") {
      "0_5".delete()
      "_05".delete()
      "_6".delete()
      insertAtStart("__32_")
      "_32".delete()
    }
  }

  fun testBug4() {
    test("_5_30_5240_32_46____51530__") {
      "_5_".insertAfter("40_1_2")
      "0_5240_32_46___".delete()
      "5_40".delete()
      "__1_23_51".insertAfter("30__23")
      "1_23_5130".delete()
    }
  }

  fun testBug5() {
    test("_") {
      insertAtStart("__6406")
      ("_" at !1 - 2).delete()
      ("_" at !0 - 1).insertAfter("_11_5")
      "__1".insertAfter("130")
      "__11301_".insertAfter("3")
      "56406".replace("4__56_21_")
      "1301_34__56_21".replace(" 60246")
      "__1 602".insertAfter("01511")
      "__1 60201".insertAfter("2633_33")
      "5".delete()
      "3114".delete()
      "602012633_3".delete()
      ("1" at !2 - 3).replace("_34__310_")
      ("_" at !2 - 3).delete()
      "0_".delete()
      ("_" at !0 - 1).insertAfter("051")

      assertTextContentIs("_051_34__31 6__")
    }
  }

  fun testTrimSpaces1() {
    test("a _b _c ") {
      insertAtStart("x")
      stripTrailingSpaces()

      rollbackLine(0)

      stripTrailingSpaces()
      assertTextContentIs("a _b _c ")
    }
  }

  fun testTrimSpaces2() {
    test("a _b _c ") {
      insertAtStart("x")
      stripTrailingSpaces()
      assertTextContentIs("xa_b _c ")
    }
  }

  fun testTrimSpaces3() {
    test("a _b _c ") {
      "a _b _".insertAfter("x")
      insertAtStart("x")
      stripTrailingSpaces()

      rollbackLine(2)

      stripTrailingSpaces()
      assertTextContentIs("xa_b _c ")
    }
  }

  fun testInsertion1() {
    test("X_X_X_") {
      insertAtStart("X_")
      assertRanges(Range(0, 1, 0, 0))
    }
  }

  fun testInsertion2() {
    test("X_X_X_") {
      (1 th "X_").insertAfter("X_")
      assertRanges(Range(1, 2, 1, 1))
    }
  }

  fun testInsertion3() {
    test("X_X_X_") {
      (2 th "X_").insertAfter("X_")
      assertRanges(Range(2, 3, 2, 2))
    }
  }

  fun testInsertion4() {
    test("X_X_X_") {
      (3 th "X_").insertAfter("X_")
      assertRanges(Range(3, 4, 3, 3))
    }
  }

  fun testInsertion5() {
    test("Z_X_X_") {
      "Z".replace("Y")
      "Y_".insertAfter("X_")
      assertRanges(Range(0, 2, 0, 1))
    }
  }

  fun testInsertion6() {
    test("X_X_X_") {
      (1 th "X").replace("Y")
      "Y_X_".insertAfter("X_")
      assertRanges(Range(0, 1, 0, 1), Range(2, 3, 2, 2))
    }
  }

  fun testInsertion7() {
    test("X_X_X_") {
      (1 th "X").replace("Y")
      "Y_X_X_".insertAfter("X_")
      assertRanges(Range(0, 1, 0, 1), Range(3, 4, 3, 3))
    }
  }

  fun testInsertion8() {
    test("X_X_X_") {
      (3 th "X").replace("Y")
      assertRanges(Range(2, 3, 2, 3))
    }
  }

  fun testInsertion9() {
    test("X_X_X") {
      (3 th "X").replace("Y")
      assertRanges(Range(2, 3, 2, 3))
    }
  }

  fun testWhitespaceChanges() {
    test("AAAAA_ _BBBBB_CCCCC_DDDDD_EEEEE_") {
      " _BBBBB_CCCCC_DDDDD_EEEEE".replace("  BBBBB_  CCCCC_  DDDDD_  EEEEE_ ")

      assertRanges(Range(1, 6, 1, 6))
    }
  }

  fun testWhitespaceBlockMerging1() {
    test("A_B_C_D_E_") {
      "A".replace("C_D_E")
      (2 th "C_D_E_").delete()
      assertTextContentIs("C_D_E_B_")
      assertRanges(Range(0, 3, 0, 1), Range(4, 4, 2, 5))
    }
  }

  fun testWhitespaceBlockMerging2() {
    test("A_ _C_D_E_") {
      "A".replace("C_D_E")
      (2 th "C_D_E_").delete()
      assertTextContentIs("C_D_E_ _")
      assertRanges(Range(0, 0, 0, 2), Range(3, 4, 5, 5))
    }
  }

  fun testWhitespaceBlockMerging3() {
    test("A_ _ _ _ _ _CCCCC_DDDDD_EEEEE_") {
      "A".replace("CCCCC_DDDDD_EEEEE")
      (2 th "CCCCC_DDDDD_EEEEE_").delete()
      assertTextContentIs("CCCCC_DDDDD_EEEEE_ _ _ _ _ _")
      assertRanges(Range(0, 0, 0, 6), Range(3, 8, 9, 9))
    }
  }

  fun testWhitespaceBlockMerging4() {
    test("A_ _ _ _ _ _C_D_E_") {
      "A".replace("C_D_E")
      (2 th "C_D_E_").delete()
      assertTextContentIs("C_D_E_ _ _ _ _ _")
      assertRanges(Range(0, 3, 0, 1), Range(8, 8, 6, 9))
    }
  }

  fun testFreeze1() {
    test("X_X_X") {
      (2 th "X_").insertAfter("X_")

      tracker.doFrozen(Runnable {
        assertNull(tracker.getRanges())

        runCommand {
          document.setText("")
          document.setText("Y")
        }

        runCommand {
          document.setText("X\nX\nX\nX")
        }

        assertNull(tracker.getRanges())
      })

      assertRanges(Range(2, 3, 2, 2))
    }
  }

  fun testFreeze2() {
    test("X_X_X") {
      (2 th "X_").insertAfter("X_")

      tracker.doFrozen(Runnable {
        assertNull(tracker.getRanges())

        runCommand {
          document.setText("")
          document.setText("Y")
        }

        runCommand {
          document.setText(parseInput("Y_X_X_X_X"))
        }

        assertNull(tracker.getRanges())
      })

      assertRanges(Range(0, 1, 0, 0), Range(3, 4, 2, 2))
    }
  }

  fun testFreeze3() {
    test("X_X_X") {
      (2 th "X_").insertAfter("X_")

      tracker.doFrozen(Runnable {
        assertNull(tracker.getRanges())

        tracker.doFrozen(Runnable {
          runCommand {
            document.setText("")
            document.setText("Y")
          }

          assertNull(tracker.getRanges())
        })
        assertNull(tracker.getRanges())

        runCommand {
          document.setText("X\nX\nX\nX")
        }

        assertNull(tracker.getRanges())
      })

      assertRanges(Range(2, 3, 2, 2))
    }
  }

  fun testFreeze4() {
    test("X_X_X") {
      (2 th "X_").insertAfter("X_")

      tracker.doFrozen(Runnable {
        assertNull(tracker.getRanges())

        tracker.setBaseRevision(parseInput("X_X_X_Z_Z"))

        runCommand {
          document.setText(parseInput("Y_X_X_X_X_Z_Z"))
        }

        assertNull(tracker.getRanges())
      })

      assertRanges(Range(0, 1, 0, 0), Range(3, 4, 2, 2))
    }
  }

}
