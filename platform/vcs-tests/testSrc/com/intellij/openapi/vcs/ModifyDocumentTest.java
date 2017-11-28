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

import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.util.diff.FilesTooBigForDiffException;

import java.util.Arrays;
import java.util.BitSet;


/**
 * author: lesya
 */
public class ModifyDocumentTest extends BaseLineStatusTrackerTestCase {

  public void testInit() {
    DiffManager instance = DiffManager.getInstance();
    assertNotNull(instance);
    MarkupEditorFilterFactory.createNotFilter(instance.getDiffEditorFilter());
    MarkupEditorFilterFactory.createIsNotDiffFilter();
  }

  public void testSimpleInsert() throws Throwable {
    createDocument("1234\n2345\n3456");
    insertString(2, "a");
    compareRanges();
  }

  public void testUndo() throws Throwable {
    createDocument("1234\n2345\n3456");
    insertString(7, "a");
    compareRanges();
    deleteString(7, 8);
    compareRanges();
  }

  public void testLineEndBeforeModification() throws Throwable {
    createDocument("1234\n2345\n3456");
    insertString(6, "a");
    compareRanges();
    insertString(5, "\n");
    compareRanges();
  }

  public void testLineEndBeforeModification2() throws Throwable {
    createDocument("1234\n2345\n3456");
    insertString(6, "a");
    compareRanges();
    insertString(4, "\n");
    compareRanges();
  }

  public void testInsertDoubleEnterAtEnd() throws Throwable {
    createDocument("1");
    insertString(1, "\n");
    compareRanges();
    insertString(2, "\n");
    compareRanges();
  }

  public void testSimpleInsertAndWholeReplace() throws Throwable {
    createDocument("1234\n2345\n3456");
    insertString(2, "a");
    compareRanges();
    replaceString(0, myDocument.getTextLength(), " ");
    compareRanges();
  }

  public void testSimpleInsert2() throws Throwable {
    createDocument("1\n2\n3\n4\n5");
    insertString(4, "a");
    compareRanges();
  }

  public void testSimpleInsertToEmpty() throws Throwable {
    createDocument("");
    insertString(0, "a");
    compareRanges();
  }

  public void testDoubleSimpleInsert() throws Throwable {
    createDocument("1234\n2345\n3456");
    insertString(2, "a");
    compareRanges();
    insertString(2, "a");
    compareRanges();
  }

  public void testInsertEnter() throws Throwable {
    createDocument("1234\n2345\n3456");
    insertString(2, "\n");
    compareRanges();
  }

  public void testSimpleInsertAndEnterToEmpty() throws Throwable {
    createDocument("");
    insertString(0, "a");
    compareRanges();
    insertString(1, "\n");
    compareRanges();
  }

  public void testInsertEnterAtEnter() throws Throwable {
    createDocument("1234\n2345\n3456");
    insertString(4, "\n");
    compareRanges();
  }

  public void testInsertEnterAtEnterAndSimpleInsert() throws Throwable {
    createDocument("1234\n2345\n3456");
    insertString(4, "\n");
    compareRanges();
    insertString(5, "a");
    compareRanges();
  }

  public void testInsertDoubleEnterAtEnters() throws Throwable {
    createDocument("1234\n2345\n3456");
    insertString(4, "\n");
    compareRanges();
    insertString(10, "\n");
    compareRanges();

  }

  public void testInsertEnterAndSpaceAfterEnter() throws Throwable {
    createDocument("12345\n12345\n12345");
    insertString(5, "\n ");
    compareRanges();
  }

  public void testInsertEnterAndDeleteEnter1() throws Throwable {
    createDocument("12345\n12345\n12345");
    insertString(5, "\n");
    compareRanges();
    deleteString(5, 6);
    compareRanges();
  }

  public void testInsertEnterAndDeleteEnter2() throws Throwable {
    createDocument("12345\n12345\n12345");
    insertString(5, "\n");
    compareRanges();
    deleteString(6, 7);
    compareRanges();
  }

  public void testSimpleDelete() throws Throwable {
    createDocument("1234\n2345\n3456");
    deleteString(2, 3);
    compareRanges();
  }

  public void testDeleteLine() throws Throwable {
    createDocument("1234\n2345\n3456");
    deleteString(0, 5);
    compareRanges();
  }

  public void testDoubleDelete() throws Throwable {
    createDocument("1234\n2345\n3456");
    deleteString(2, 3);
    deleteString(2, 3);
    compareRanges();
  }

  public void testDeleteEnter() throws Throwable {
    createDocument("12345\n23456\n34567");
    deleteString(5, 6);
    compareRanges();
  }

  public void testDeleteDoubleEnter() throws Throwable {
    createDocument("12345\n\n23456\n34567");
    deleteString(5, 6);
    compareRanges();
  }

  //
  public void testDoubleInsertToClass() throws Throwable {
    createDocument("class A{\n\n}");
    insertString(9, "a");
    compareRanges();
    insertString(10, "a");
    compareRanges();
  }

  public void testInsertSymbolAndEnterToClass() throws Throwable {
    createDocument("class A{\n\n}");
    insertString(9, "a");
    compareRanges();
    insertString(10, "\n");
    compareRanges();
  }

  public void testMultiLineReplace2() throws Throwable {
    createDocument("012a\n012b\n012c");
    replaceString(4, 9, "\nx\ny\nz");
    compareRanges();
  }

  public void testChangedLines1() throws Throwable {
    createDocument("class A{\nx\na\nb\nc\n}", "class A{\n1\nx\n2\n}");
    compareRanges();
  }

  public void testMultiLineReplace1() throws Throwable {
    createDocument("012a\n012b\n012c\n012d\n012e");
    replaceString(5, 6, "a");
    compareRanges();
    replaceString(4, 14, "\nx");
    compareRanges();
  }

  public void testInsertAndModify() throws FilesTooBigForDiffException {
    createDocument("a\nb\nc\nd");
    insertString(3, "\n");
    compareRanges();
    insertString(4, "\n");
    compareRanges();
    insertString(6, " ");
    compareRanges();
  }

  public void testRangesShouldMerge() throws FilesTooBigForDiffException {
    createDocument("1\n2\n3\n4");
    insertString(1, "1");
    compareRanges();
    insertString(6, "3");
    compareRanges();
    insertString(3, "2");
    compareRanges();
  }

  public void testShiftRangesAfterChange() throws FilesTooBigForDiffException {
    createDocument("1\n2\n3\n4");
    insertString(7, "4");
    compareRanges();
    insertString(0, "\n");
    compareRanges();
    insertString(0, "\n");
    compareRanges();
    insertString(0, "\n");
    compareRanges();
  }

  public void testInsertBeforeChange() throws FilesTooBigForDiffException {
    createDocument("   11\n   3 \n   44\n   55\n   6\n   7\n   88\n   ", "   1\n   2\n   3 \n   4\n   5\n   6\n   7\n   8\n   ");
    insertString(9, "3");
    compareRanges();
    assertEquals("   11\n   33 \n   44\n   55\n   6\n   7\n   88\n   ", myDocument.getText());
    insertString(9, "aaa\nbbbbbbbb\ncccc\ndddd");
    compareRanges();
  }


  public void testUndoDeletion() throws FilesTooBigForDiffException {
    createDocument("1\n2\n3\n4\n5\n6\n7\n");
    deleteString(4, 6);
    assertEquals("1\n2\n4\n5\n6\n7\n", myDocument.getText());
    compareRanges();
    insertString(4, "3\n");
    compareRanges();
  }

  public void testUndoDeletion2() throws FilesTooBigForDiffException {
    createDocument("1\n2\n3\n4\n5\n6\n7\n");
    deleteString(3, 5);
    assertEquals("1\n2\n4\n5\n6\n7\n", myDocument.getText());
    compareRanges();
    insertString(4, "\n3");
    compareRanges();
  }

  public void testSRC17123() throws FilesTooBigForDiffException {
    createDocument("package package;\n" + "\n" + "public class Class3 {\n" + "    public int i1;\n" + "    public int i2;\n" +
                   "    public int i3;\n" + "    public int i4;\n" + "\n" + "    public static void main(String[] args) {\n" + "\n" +
                   "    }\n" + "}");
    deleteString(39, 58);
    compareRanges();
    assertEquals("package package;\n" + "\n" + "public class Class3 {\n" + "    public int i2;\n" + "    public int i3;\n" +
                 "    public int i4;\n" + "\n" + "    public static void main(String[] args) {\n" + "\n" + "    }\n" + "}",
                 myDocument.getText());

    deleteString(39, myDocument.getTextLength());
    compareRanges();
    deleteString(myDocument.getTextLength() - 1, myDocument.getTextLength());
  }

  public void testUnexpetedDeletedRange() throws FilesTooBigForDiffException {
    createDocument("    public class\n    bbb\n");
    insertString(17, "    \n");
    assertEquals("    public class\n    \n    bbb\n", myDocument.getText());
    compareRanges();
    deleteString(17, 21);
    assertEquals("    public class\n\n    bbb\n", myDocument.getText());
    compareRanges();
    insertString(18, "    \n");
    assertEquals("    public class\n\n    \n    bbb\n", myDocument.getText());
    compareRanges();
    deleteString(18, 22);
    assertEquals("    public class\n\n\n    bbb\n", myDocument.getText());
    compareRanges();
    deleteString(4, 10);
    assertEquals("     class\n\n\n    bbb\n", myDocument.getText());
    compareRanges();
    insertString(4, "p");
    assertEquals("    p class\n\n\n    bbb\n", myDocument.getText());
    compareRanges();
    insertString(5, "r");
    assertEquals("    pr class\n\n\n    bbb\n", myDocument.getText());
    compareRanges();
    insertString(6, "i");
    assertEquals("    pri class\n\n\n    bbb\n", myDocument.getText());
    compareRanges();
  }

  public void testSrc29814() throws FilesTooBigForDiffException {
    String text = "111\n" + "222\n" + "333\n";

    createDocument(text);
    deleteString(0, text.length());
    compareRanges();
    assertEquals("", myDocument.getText());
    insertString(0, "222\n");
    compareRanges();
    deleteString(0, 4);
    compareRanges();
    insertString(0, text);
    compareRanges();
  }

  public void testDeletingTwoMethods() throws FilesTooBigForDiffException {

    String part1 = "class Foo {\n" + "  public void method1() {\n" + "    // something\n" + "  }\n" + "\n";

    String part2 = "  public void method2() {\n" + "    // something\n" + "  }\n" + "\n" + "  public void method3() {\n" +
                   "    // something\n" + "  }\n";

    String part3 = "\n" + "  public void method4() {\n" + "    // something\n" + "  }\n" + "}";

    String text = part1 + part2 + part3;

    createDocument(text);
    deleteString(part1.length(), part1.length() + part2.length());
    assertEquals(part1 + part3, myDocument.getText());
    assertEquals(Arrays.asList(new Range(5, 5, 5, 12)), myTracker.getRanges());

    deleteString(part1.length(), part1.length() + 1);
    compareRanges();
  }

  public void testBug1() throws Throwable {
    createDocument("1\n2\n3\n4\n");
    deleteString(4, 6);
    compareRanges();
    insertString(3, "X");
    compareRanges();
  }

  public void testBug2() throws Throwable {
    createDocument("1\n2\n3\n4\n5\n6\n");
    deleteString(4, 6);
    compareRanges();
    deleteString(8, 10);
    compareRanges();
    insertString(4, "3\n8\n");
    compareRanges();
  }

  public void testBug3() throws Throwable {
    createDocument("\n\n00\n556\n");

    deleteString(3, 6);
    checkCantTrim();
    deleteString(1, 4);
    checkCantTrim();
    deleteString(0, 2);
    checkCantTrim();
    insertString(0, "\n\n32\n");
    checkCantTrim();
    deleteString(1, 4);
    checkCantTrim();
  }

  public void testBug4() throws Throwable {
    createDocument("\n5\n30\n5240\n32\n46\n\n\n\n51530\n\n");

    insertString(3, "40\n1\n2");
    checkCantTrim();
    deleteString(10, 25);
    checkCantTrim();
    deleteString(1, 5);
    checkCantTrim();
    insertString(9, "30\n\n23");
    checkCantTrim();
    deleteString(2, 11);
    checkCantTrim();
  }

  public void testBug5() throws Throwable {
    createDocument("\n");

    replaceString(0, 0, "\n\n6406");
    deleteString(1, 2);
    insertString(1, "\n11\n5");
    insertString(3, "130");
    replaceString(8, 8, "3");
    replaceString(9, 14, "4\n\n56\n21\n");
    replaceString(3, 17, " 60246");
    insertString(7, "01511");
    insertString(9, "2633\n33");
    deleteString(16, 17);
    deleteString(15, 19);
    deleteString(4, 15);
    replaceString(2, 3, "\n34\n\n310\n");
    deleteString(2, 3);
    deleteString(8, 10);
    insertString(1, "051");
    checkCantTrim();
  }

  public void testTrimSpaces1() throws Throwable {
    createDocument("a \nb \nc ");
    insertString(0, "x");
    ((DocumentImpl)myDocument).stripTrailingSpaces(null, true);

    BitSet lines = new BitSet();
    lines.set(0);
    rollback(lines);

    ((DocumentImpl)myDocument).stripTrailingSpaces(null, true);
    assertEquals("a \nb \nc ", myDocument.getText());
  }

  public void testTrimSpaces2() throws Throwable {
    createDocument("a \nb \nc ");
    insertString(0, "x");
    ((DocumentImpl)myDocument).stripTrailingSpaces(null, true);

    assertEquals("xa\nb \nc ", myDocument.getText());
  }

  public void testTrimSpaces3() throws Throwable {
    createDocument("a \nb \nc ");
    insertString(6, "x");
    insertString(0, "x");
    ((DocumentImpl)myDocument).stripTrailingSpaces(null, true);

    BitSet lines = new BitSet();
    lines.set(2);
    rollback(lines);

    ((DocumentImpl)myDocument).stripTrailingSpaces(null, true);
    assertEquals("xa\nb \nc ", myDocument.getText());
  }
}
