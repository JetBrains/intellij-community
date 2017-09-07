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
package com.intellij.openapi.editor.impl;

import com.intellij.lang.FileASTNode;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiToDocumentSynchronizer;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

/**
 * @author mike
 */
public class RangeMarkerTest extends LightPlatformTestCase {
  private PsiDocumentManagerImpl documentManager;
  private PsiToDocumentSynchronizer synchronizer;
  private Document document;
  private PsiFile psiFile;
  private FileASTNode fileNode;

  @Override
  protected void runTest() throws Throwable {
    if (getTestName(false).contains("NoCommand")) {
      super.runTest();
      return;
    }
    WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<Void, Throwable>)() -> {
      super.runTest();
      return null;
    });
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject());
    synchronizer = documentManager.getSynchronizer();
  }

  @Override
  protected void tearDown() throws Exception {
    documentManager = null;
    synchronizer = null;
    psiFile = null;
    fileNode = null;
    document = null;
    super.tearDown();
  }

  public void testCreation() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    assertEquals(2, marker.getStartOffset());
    assertEquals(5, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testDeleteBeforeStart() {
    RangeMarker marker = createMarker("01[234]56789");

    marker.getDocument().deleteString(0, 1);

    assertEquals(1, marker.getStartOffset());
    assertEquals(4, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testInsertIntoRange() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().insertString(4, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(8, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testInsertIntoPoint() {
    RangeMarker marker = createMarker("0123456789", 2, 2);

    marker.getDocument().insertString(2, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(2, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testDeletePoint() {
    RangeMarker marker = createMarker("0123456789", 2, 2);

    marker.getDocument().deleteString(1, 3);

    assertFalse(marker.isValid());
  }

  public void testDeleteRangeInside() {
    RangeMarker marker = createMarker("0123456789", 1, 7);

    marker.getDocument().deleteString(2, 5);

    assertTrue(marker.isValid());
  }

  public void testReplaceRangeToSingleChar() {
    RangeMarker marker = createMarker("0123456789", 1, 7);

    marker.getDocument().replaceString(2, 5, " ");

    assertTrue(marker.isValid());
  }

  public void testReplaceWholeRange() {
    RangeMarker marker = createMarker("0123456789", 1, 7);
    marker.getDocument().replaceString(1, 7, "abc");
    assertValidMarker(marker, 1, 4);
  }

  public void testUpdateInvalid() {
    RangeMarker marker = createMarker("01[]23456789");

    marker.getDocument().deleteString(1, 3);
    assertFalse(marker.isValid());

    marker.getDocument().insertString(2, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(2, marker.getEndOffset());
    assertFalse(marker.isValid());
  }

  public void testInsertAfterEnd() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().insertString(6, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(5, marker.getEndOffset());
    assertTrue(marker.isValid());
  }


  public void testDeleteEndPart() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().deleteString(4, 6);

    assertValidMarker(marker, 2, 4);
  }

  public void testDeleteStartPart() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().deleteString(0, 4);

    assertValidMarker(marker, 0, 1);
  }

  public void testReplaceStartPartInvalid() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().replaceString(0, 4, "xxxx");

    assertValidMarker(marker, 4, 5);
  }

  public void testDeleteFirstChar() {
    RangeMarker marker = createMarker("0123456789", 0, 5);

    marker.getDocument().deleteString(0, 1);

    assertValidMarker(marker, 0, 4);
  }

  public void testInsertBeforeStart() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().insertString(0, "xxx");

    assertEquals(5, marker.getStartOffset());
    assertEquals(8, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testInsertIntoStart() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().insertString(2, "xxx");

    assertValidMarker(marker, 5, 8);
  }

  public void testInsertIntoStartExpandToLeft() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.setGreedyToLeft(true);

    marker.getDocument().insertString(2, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(8, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testInsertIntoEnd() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().insertString(5, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(5, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testInsertIntoEndExpandRight() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.setGreedyToRight(true);

    marker.getDocument().insertString(5, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(8, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testNoNegative() {
    RangeMarker marker = createMarker("package safd;\n\n[import javax.swing.JPanel;]\nimport java.util.ArrayList;\n\nclass T{}");

    marker.getDocument()
      .replaceString(15, 15 + "import javax.swing.JPanel;\nimport java.util.ArrayList;".length(), "import java.util.ArrayList;");

    assertEquals(15, marker.getStartOffset());
  }

  public void testReplaceRightIncludingFirstChar() {
    String s = "12345\n \n12345";
    RangeMarker marker = createMarker(s, 6, 8);

    marker.getDocument().replaceString(0, s.length(), s.replaceAll(" ", ""));

    assertValidMarker(marker, 6, 7);
  }

  public void testDeleteRightPart() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().deleteString(4, 6);

    assertValidMarker(marker, 2, 4);
  }

  public void testDeleteRightPart2() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().deleteString(4, 5);

    assertValidMarker(marker, 2, 4);
  }

  public void testReplaceRightPartInvalid() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().replaceString(4, 6, "xxx");

    assertValidMarker(marker, 2, 4);
  }

  public void testDeleteWholeRange() {
    RangeMarker marker = createMarker("0123456789", 2, 5);
    marker.getDocument().deleteString(1, 6);
    assertFalse(marker.isValid());
  }

  public void testDeleteExactRange() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().deleteString(2, 5);
    assertValidMarker(marker, 2, 2);
  }

  public void testDeleteJustBeforeStart() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().deleteString(0, 2);
    assertValidMarker(marker, 0, 3);
  }

  public void testDeleteRightAfterEnd() {
    RangeMarker marker = createMarker("0123456789", 2, 2);

    marker.getDocument().deleteString(2, 5);
    assertValidMarker(marker, 2, 2);
  }

  public void testReplacementWithOldTextOverlap() {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().replaceString(0, 10, "0123456789");
    assertValidMarker(marker, 2, 5);
  }

  // Psi -> Document synchronization

  public void testPsi2Doc1() {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker(buffer.toString(), 2, 5);
    synchronizer.startTransaction(getProject(), document, psiFile);

    synchronizer.insertString(document, 3, "a");
    buffer.insert(3, "a");

    synchronizer.commitTransaction(document);

    assertEquals(buffer.toString(), document.getText());

    assertValidMarker(marker, 2, 6);
  }

  public void testDocSynchronizerPrefersLineBoundaryChanges() {
    String text = "import java.awt.List;\n" +
                    "[import java.util.ArrayList;\n]" +
                    "import java.util.HashMap;\n" +
                    "import java.util.Map;";
    RangeMarker marker = createMarker(text);
    synchronizer.startTransaction(getProject(), document, psiFile);

    String newText = StringUtil.replaceSubstring(document.getText(), TextRange.create(marker), "");
    synchronizer.replaceString(document, 0, document.getTextLength(), newText);

    final List<DocumentEvent> events = new ArrayList<>();
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent e) {
        events.add(e);
      }
    });
    synchronizer.commitTransaction(document);

    assertEquals(newText, document.getText());
    DocumentEvent event = assertOneElement(events);
    assertEquals("DocumentEventImpl[myOffset=22, myOldLength=28, myNewLength=0, myOldString='import java.util.ArrayList;\n', myNewString=''].", event.toString());
  }

  public void testPsi2DocReplaceAfterAdd() {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker(buffer.toString(), 2, 5);
    synchronizer.startTransaction(getProject(), document, psiFile);

    synchronizer.insertString(document, 1, "a");
    buffer.insert(1, "a");

    synchronizer.replaceString(document, 3, 4, "a");
    buffer.replace(3, 4, "a");

    synchronizer.commitTransaction(document);

    assertEquals(buffer.toString(), document.getText());

    assertValidMarker(marker, 3, 6);
  }

  public void testPsi2DocTwoReplacements() {
    RangeMarker marker = createMarker("fooFooFoo fooFooFoo", 10, 19);
    synchronizer.startTransaction(getProject(), document, psiFile);
    synchronizer.replaceString(document, 0, 9, "xxx");
    synchronizer.replaceString(document, 4, 13, "xxx");
    synchronizer.commitTransaction(document);
    assertValidMarker(marker, 4, 7);
  }

  public void testPsi2DocThreeOverlappingReplacements() {
    createMarker("abc", 0, 0);
    synchronizer.startTransaction(getProject(), document, psiFile);
    synchronizer.replaceString(document, 0, 1, "xy");
    synchronizer.replaceString(document, 3, 4, "yz");
    synchronizer.replaceString(document, 0, 5, "xxx");
    synchronizer.commitTransaction(document);
    assertEquals("xxx", document.getText());
  }

  public void testPsi2DocMergeReplaceAfterAdd() {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker(buffer.toString(), 2, 5);
    synchronizer.startTransaction(getProject(), document, psiFile);

    synchronizer.insertString(document, 1, "a");
    buffer.insert(1, "a");

    synchronizer.replaceString(document, 3, 4, "a");
    buffer.replace(3, 4, "a");

    synchronizer.replaceString(document, 3, 5, "bb");
    buffer.replace(3, 5, "bb");
    PsiToDocumentSynchronizer.DocumentChangeTransaction transaction = PlatformTestUtil.notNull(synchronizer.getTransaction(document));
    assertSize(2, transaction.getAffectedFragments().keySet());

    synchronizer.commitTransaction(document);

    assertEquals(buffer.toString(), document.getText());

    assertValidMarker(marker, 3, 6);
  }

  public void testPsi2DocMergeReplaceWithMultipleAdditions() {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker(buffer.toString(), 2, 5);
    synchronizer.startTransaction(getProject(), document, psiFile);

    synchronizer.replaceString(document, 0, 10, "0");
    buffer.replace(0, 10, "0");

    for (int i = 1; i < 10; i++) {
      synchronizer.insertString(document, i, String.valueOf(i));
      buffer.insert(i, String.valueOf(i));
    }
    PsiToDocumentSynchronizer.DocumentChangeTransaction transaction = PlatformTestUtil.notNull(synchronizer.getTransaction(document));
    assertSize(1, transaction.getAffectedFragments().keySet());

    synchronizer.commitTransaction(document);


    assertEquals(buffer.toString(), document.getText());

    assertValidMarker(marker, 2, 5);
  }

  public void testPsi2DocMergeMultipleAdditionsWithReplace() {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker(buffer.toString(), 2, 5);
    synchronizer.startTransaction(getProject(), document, psiFile);
    final PsiToDocumentSynchronizer.DocumentChangeTransaction transaction = synchronizer.getTransaction(document);
    assertNotNull(transaction);

    for (int i = 0; i < 10; i++) {
      synchronizer.insertString(document, i, String.valueOf(i));
      buffer.insert(i, String.valueOf(i));
    }

    assertSize(1, transaction.getAffectedFragments().keySet());
    synchronizer.replaceString(document, 0, 20, "0123456789");
    buffer.replace(0, 20, "0123456789");

    assertSize(1, transaction.getAffectedFragments().keySet());

    synchronizer.commitTransaction(document);

    assertEquals(buffer.toString(), document.getText());

    assertValidMarker(marker, 2, 5);
  }

  public void testPsi2DocSurround() {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker(buffer.toString(), 2, 5);
    synchronizer.startTransaction(getProject(), document, psiFile);

    synchronizer.replaceString(document, 3, 5, "3a4");
    buffer.replace(3, 5, "3a4");

    synchronizer.insertString(document, 3, "b");
    buffer.insert(3, "b");

    synchronizer.insertString(document, 7, "d");
    buffer.insert(7, "d");

    PsiToDocumentSynchronizer.DocumentChangeTransaction transaction = PlatformTestUtil.notNull(synchronizer.getTransaction(document));
    assertSize(3, transaction.getAffectedFragments().keySet());

    synchronizer.commitTransaction(document);

    assertEquals(buffer.toString(), document.getText());

    assertValidMarker(marker, 2, 7);
  }

  public void testPsi2DocForwardRangesChanges() {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker(buffer.toString(), 2, 5);
    synchronizer.startTransaction(getProject(), document, psiFile);

    synchronizer.replaceString(document, 4, 5, "3a4");
    buffer.replace(4, 5, "3a4");

    synchronizer.insertString(document, 7, "b");
    buffer.insert(7, "b");

    synchronizer.insertString(document, 1, "b");
    buffer.insert(1, "b");

    synchronizer.commitTransaction(document);

    assertEquals(buffer.toString(), document.getText());

    assertValidMarker(marker, 3, 8);
  }

  private static void assertValidMarker(@NotNull RangeMarker marker, int start, int end) {
    assertTrue(marker.isValid());
    assertEquals(start, marker.getStartOffset());
    assertEquals(end, marker.getEndOffset());
  }

  public void testNested() {
    RangeMarker marker1 = createMarker("0[12345678]9");
    Document document = marker1.getDocument();
    RangeMarker marker2 = document.createRangeMarker(2, 5);
    RangeMarker marker3 = document.createRangeMarker(3, 4);
    document.insertString(0, "x");

    assertEquals(2, marker1.getStartOffset());
    assertEquals(3, marker2.getStartOffset());
    assertEquals(4, marker3.getStartOffset());
  }

  public void testNestedAfter() {
    RangeMarker marker1 = createMarker("0[12345678]90123");
    Document document = marker1.getDocument();
    RangeMarker marker2 = document.createRangeMarker(2, 5);
    RangeMarker marker3 = document.createRangeMarker(3, 4);
    document.insertString(10, "x");

    assertEquals(1, marker1.getStartOffset());
    assertEquals(2, marker2.getStartOffset());
    assertEquals(3, marker3.getStartOffset());
  }

  public void testNested3() {
    RangeMarker marker1 = createMarker("01[23]4567890123");
    DocumentEx document = (DocumentEx)marker1.getDocument();
    RangeMarker marker2 = document.createRangeMarker(9, 11);
    RangeMarker marker3 = document.createRangeMarker(1, 12);
    marker3.dispose();

    document.deleteString(marker1.getEndOffset(), marker2.getStartOffset());
  }

  public void testBranched() {
    RangeMarker marker1 = createMarker("01234567890123456", 0, 1);
    DocumentEx document = (DocumentEx)marker1.getDocument();
    RangeMarker marker2 = document.createRangeMarker(2, 3);
    RangeMarker marker3 = document.createRangeMarker(4, 5);
    RangeMarker marker4 = document.createRangeMarker(6, 7);
    RangeMarker marker5 = document.createRangeMarker(8, 9);
    RangeMarker marker6 = document.createRangeMarker(10, 11);
    RangeMarker marker7 = document.createRangeMarker(12, 13);
    RangeMarker marker8 = document.createRangeMarker(14, 15);
    document.deleteString(1, 2);
    assertTrue(marker2.isValid());
    assertTrue(marker3.isValid());
    assertTrue(marker4.isValid());
    assertTrue(marker5.isValid());
    assertTrue(marker6.isValid());
    assertTrue(marker7.isValid());
    assertTrue(marker8.isValid());
  }

  public void testDevourMarkerWithDeletion() {
    RangeMarker marker1 = createMarker("012345[67890123456]7");
    DocumentEx document = (DocumentEx)marker1.getDocument();
    document.deleteString(1, document.getTextLength());
  }

  public void testLL() {
    RangeMarker marker1 = createMarker("012345678901234567", 5,6);
    DocumentEx document = (DocumentEx)marker1.getDocument();
    document.createRangeMarker(4, 5);
    document.createRangeMarker(6, 7);
    document.createRangeMarker(0, 4);
    document.deleteString(1, 2);

    document.createRangeMarker(0, 7);
    document.createRangeMarker(0, 7);
  }

  public void testSwap() {
    RangeMarkerEx marker1 = createMarker("012345678901234567", 5,6);
    DocumentEx document = (DocumentEx)marker1.getDocument();
    document.createRangeMarker(3, 5);
    document.createRangeMarker(6, 7);
    document.createRangeMarker(4, 4);
    marker1.dispose();
  }

  public void testX() {
    RangeMarkerEx marker1 = createMarker(StringUtil.repeatSymbol(' ', 10), 3,6);
    DocumentEx document = (DocumentEx)marker1.getDocument();
    document.createRangeMarker(2, 3);
    document.createRangeMarker(3, 8);
    document.createRangeMarker(7, 9);
    RangeMarkerEx r1 = (RangeMarkerEx)document.createRangeMarker(6, 8);

    r1.dispose();
    marker1.dispose();
  }

  public void testStickingToRight() {
    RangeMarkerImpl marker = (RangeMarkerImpl)createMarker("ab", 1, 1);
    marker.setStickingToRight(true);
    marker.getDocument().insertString(1, " ");
    assertTrue(marker.isValid());
    assertEquals(2, marker.getStartOffset());
    assertEquals(2, marker.getEndOffset());
  }

  private static List<RangeMarker> add(DocumentEx document, int... offsets) {
    List<RangeMarker> result = new ArrayList<>();
    for (int i=0; i<offsets.length; i+=2) {
      int start = offsets[i];
      int end = offsets[i+1];
      RangeMarker m = document.createRangeMarker(start, end);
      result.add(m);
    }
    return result;
  }
  private static void delete(List<RangeMarker> mm, int... indexes) {
    for (int index : indexes) {
      RangeMarker m = mm.get(index);
      m.dispose();
    }
  }
  public void testX2() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 2,9, 0,0, 7,7
      );
    delete(mm, 0);
  }
  public void testX3() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 1,9, 8,8, 8,8, 0,5, 4,5
      );
    delete(mm, 0);
  }

  public void _testRandomAddRemove() {
    int N = 100;

    for (int ti=0; ;ti++) {
      if (ti%10000 ==0) System.out.println(ti);
      DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', N));

      Random gen = new Random();
      List<Pair<RangeMarker, TextRange>> adds = new ArrayList<>();
      List<Pair<RangeMarker, TextRange>> dels = new ArrayList<>();


      try {
        for (int i = 0; i < 30; i++) {
          int x = gen.nextInt(N);
          int y = x + gen.nextInt(N - x);
          if (gen.nextBoolean()) {
            x = 0;
            y = document.getTextLength();
          }
          RangeMarkerEx r = (RangeMarkerEx)document.createRangeMarker(x, y);
          adds.add(Pair.create(r, TextRange.create(r)));
        }
        List<Pair<RangeMarker, TextRange>> candidates = new ArrayList<>(adds);
        while (!candidates.isEmpty()) {
          int size = candidates.size();
          int x = gen.nextInt(size);
          Pair<RangeMarker, TextRange> c = candidates.remove(x);
          RangeMarkerEx r = (RangeMarkerEx)c.first;
          assertEquals(size-1, candidates.size());
          dels.add(c);
          r.dispose();
        }
      }
      catch (AssertionError e) {
        String s= "adds: ";
        for (Pair<RangeMarker, TextRange> c : adds) {
          TextRange t = c.second;
          s += t.getStartOffset() + "," + t.getEndOffset() + ", ";
        }
        s += "\ndels: ";

        for (Pair<RangeMarker, TextRange> c : dels) {
          int index = adds.indexOf(c);
          assertSame(c, adds.get(index));
          s += index + ", ";
        }
        System.err.println(s);
        throw e;
      }
    }

  }

  private static void edit(DocumentEx document, int... offsets) {
    for (int i = 0; i < offsets.length; i+=3) {
      int offset = offsets[i];
      int oldlength = offsets[i+1];
      int newlength = offsets[i+2];

      document.replaceString(offset, offset + oldlength, StringUtil.repeatSymbol(' ', newlength));
    }
  }

  public void testE1() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 3,5, 0,1, 9,9
      );
    edit(document, 3,6,0);
    delete(mm, 0);
  }

  public void testE2() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 0,3, 6,9, 8,8
      );
    edit(document, 0,3,0);
    delete(mm, 0);
  }

  public void testE3() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 4,5, 6,8, 3,4, 4,9, 2,9
      );
    edit(document, 4,6,0);
    delete(mm, 0);
  }

  public void testE4() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 3,5, 5,6, 4,8, 6,9, 8,9
      );
    edit(document, 6,0,0,  3,0,2);
    delete(mm, 1,0);
  }
  public void testE5() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 9,9, 4,4, 1,7, 7,7, 4,7
      );
    edit(document, 1,5,0);
    delete(mm, 3);
  }

  public void testE6() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm = add(document, 4,8, 4,4, 4,9, 0,2, 6,8);
    edit(document, 3,2,0);
    assertNotNull(mm);
  }

  public void testE7() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm = add(document, 6,7, 0,3, 3,6, 5,9, 2,9);
    edit(document, 5,2,0);
    assertNotNull(mm);
  }

  public void testE8() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm = add(document, 5,5, 8,8, 1,3, 3,9);
    edit(document, 4,3,0);
    assertNotNull(mm);
  }

  public void testE9() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm = add(document, 4,5, 9,9, 1,2, 0,3);
    edit(document, 0,3,0);
    assertNotNull(mm);
  }

  public void testE10() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm = add(document, 9,9, 6,8, 8,8, 5,9);
    edit(document, 2,6,0,  2,0,4);
    assertNotNull(mm);
  }

  public void testE11() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm = add(document, 9,9, 7,7, 1,6, 3,7);
    delete(mm, 1);
  }
  public void testE12() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm = add(document, 3,3, 8,8, 5,5, 5,6);
    edit(document, 2,0,2);
    delete(mm, 2);
  }

  public void testE13() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm = add(document, 5,9, 9,9, 7,7, 6,8);
    edit(document, 2,1,0);
    delete(mm, 0, 2);
  }

  public void testE14() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 100));
    List<RangeMarker> mm = add(document, 6,11, 2,13, 17,17, 13,19, 2,3, 9,10, 10,11, 14,14, 1,3, 4,12, 14,15, 3,10, 14,14, 4,4, 4,8, 6,14, 8,16, 2,12, 11,19, 10,13);
    edit(document, 19,0,0,  7,3,0,  16,0,3);
    assertNotNull(mm);
  }

  public void testE15() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 100));
    List<RangeMarker> mm = add(document, 90,93, 0,9, 44,79, 4,48, 44,99, 53,64, 59,82, 12,99, 81,86, 8,40, 24,55, 32,50, 74,79, 14,94, 7,14);
    edit(document, 34,0,4,  99,0,3);
    assertNotNull(mm);
  }

  public void testE16() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 100));
    List<RangeMarker> mm = add(document, 29,63, 47,52, 72,86, 19,86, 13,55, 18,57, 92,95, 83,99, 41,80, 53,85, 10,30, 28,44, 23,32, 70,95, 14,28);
    edit(document, 67,5,0,  1,0,4);
    delete(mm, 11);
  }
  public void testE17() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 100));

    List<RangeMarker> mm = add(document, 15,85, 79,88, 90,94, 43,67, 54,89, 81,98, 1,34, 58,93, 22,23, 44,45, 63,84, 45,76, 58,87, 40,59, 5,81, 95,95, 12,61, 52,65, 80,95, 6,16, 7,67, 59,63, 91,96, 99,99, 50,96, 72,78, 78,78, 85,85, 5,51, 90,91);
    edit(document, 20,26,0,  15,0,4,  64,4,0);
    assertNotNull(mm);
  }

  public void testRandomStressEdit_NoCommand() {
    final Random gen = new Random();
    int N_TRIES = Timings.adjustAccordingToMySpeed(7000, false);
    System.out.println("N_TRIES = " + N_TRIES);
    DocumentEx document = null;
    final int N = 100;
    for (int tryn = 0; tryn < N_TRIES; tryn++) {
      ((UndoManagerImpl)UndoManager.getInstance(getProject())).flushCurrentCommandMerger();
      ((UndoManagerImpl)UndoManager.getGlobalInstance()).flushCurrentCommandMerger();
      if (document != null) {
        ((UndoManagerImpl)UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(document);
        ((UndoManagerImpl)UndoManager.getGlobalInstance()).clearUndoRedoQueueInTests(document);
      }

      if (tryn % 10000 == 0) {
        System.out.println(tryn);
      }
      document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', N));

      final DocumentEx finalDocument = document;
      new WriteCommandAction(getProject()) {
        @Override
        protected void run(@NotNull Result result) {
          List<Pair<RangeMarker, TextRange>> adds = new ArrayList<>();
          List<Pair<RangeMarker, TextRange>> dels = new ArrayList<>();
          List<Trinity<Integer, Integer, Integer>> edits = new ArrayList<>();

          try {
            for (int i = 0; i < 30; i++) {
              int x = gen.nextInt(N);
              int y = x + gen.nextInt(N - x);
              RangeMarkerEx r = (RangeMarkerEx)finalDocument.createRangeMarker(x, y);
              adds.add(Pair.create(r, TextRange.create(r)));
            }

            for (int i = 0; i < 10; i++) {
              int offset = gen.nextInt(finalDocument.getTextLength());
              if (gen.nextBoolean()) {
                int length = gen.nextInt(5);
                edits.add(Trinity.create(offset, 0, length));
                finalDocument.insertString(offset, StringUtil.repeatSymbol(' ', length));
              }
              else {
                int length = gen.nextInt(finalDocument.getTextLength() - offset);
                edits.add(Trinity.create(offset, length, 0));
                finalDocument.deleteString(offset, offset + length);
              }
            }
            List<Pair<RangeMarker, TextRange>> candidates = new ArrayList<>(adds);
            while (!candidates.isEmpty()) {
              int size = candidates.size();
              int x = gen.nextInt(size);
              Pair<RangeMarker, TextRange> c = candidates.remove(x);
              RangeMarkerEx r = (RangeMarkerEx)c.first;
              assertEquals(size - 1, candidates.size());
              dels.add(c);
              r.dispose();
            }
          }
          catch (AssertionError e) {
            String s = "adds: ";
            for (Pair<RangeMarker, TextRange> c : adds) {
              TextRange t = c.second;
              s += t.getStartOffset() + "," + t.getEndOffset() + ", ";
            }

            s += "\nedits: ";
            for (Trinity<Integer, Integer, Integer> edit : edits) {
              s += edit.first + "," + edit.second + "," + edit.third + ",  ";
            }
            s += "\ndels: ";

            for (Pair<RangeMarker, TextRange> c : dels) {
              int index = adds.indexOf(c);
              assertSame(c, adds.get(index));
              s += index + ", ";
            }
            System.err.println(s);
            throw e;
          }
        }
      }.execute();
    }
  }

  private RangeMarkerEx createMarker(String text, final int start, final int end) {
    psiFile = createFile("x.txt", text);
    fileNode = psiFile.getNode(); // the node should be loaded, otherwise PsiToDocumentSynchronizer will ignore our commands
    return createMarker(psiFile, start, end);
  }

  private RangeMarkerEx createMarker(PsiFile psiFile, final int start, final int end) {
    document = PlatformTestUtil.notNull(documentManager.getDocument(psiFile));
    return (RangeMarkerEx)document.createRangeMarker(start, end);
  }

  private RangeMarkerEx createMarker(@NonNls String string) {
    int start = string.indexOf('[');
    assertTrue(start != -1);
    string = string.replace("[", "");
    int end = string.indexOf(']');
    assertTrue(end != -1);
    string = string.replace("]", "");
    return createMarker(string, start, end);
  }

  public void testRangeMarkersAreWeakReferenced_NoCommand() {
    final Document document = EditorFactory.getInstance().createDocument("[xxxxxxxxxxxxxx]");
    Set<RangeMarker> markers = ContainerUtil.newHashSet();
    for (int i = 0; i < 10; i++) {
      markers.add(document.createRangeMarker(0, document.getTextLength()));
    }

    LeakHunter.checkLeak(document, RangeMarker.class, markers::contains);
  }

  public void testRangeMarkersAreLazyCreated() {
    final Document document = EditorFactory.getInstance().createDocument("[xxxxxxxxxxxxxx]");
    RangeMarker m1 = document.createRangeMarker(2, 4);
    RangeMarker m2 = document.createRangeMarker(2, 4);

    assertEquals(2, ((DocumentImpl)document).getRangeMarkersSize());
    assertEquals(1, ((DocumentImpl)document).getRangeMarkersNodeSize());

    RangeMarker m3 = document.createRangeMarker(2, 5);
    assertEquals(2, ((DocumentImpl)document).getRangeMarkersNodeSize());
    document.deleteString(4,5);
    assertTrue(m1.isValid());
    assertTrue(m2.isValid());
    assertTrue(m3.isValid());
    assertEquals(1, ((DocumentImpl)document).getRangeMarkersNodeSize());

    m1.setGreedyToLeft(true);
    assertTrue(m1.isValid());
    assertEquals(3, ((DocumentImpl)document).getRangeMarkersSize());
    assertEquals(2, ((DocumentImpl)document).getRangeMarkersNodeSize());

    m3.dispose();
    assertTrue(m1.isValid());
    assertTrue(m2.isValid());
    assertFalse(m3.isValid());
    assertEquals(2, ((DocumentImpl)document).getRangeMarkersSize());
    assertEquals(2, ((DocumentImpl)document).getRangeMarkersNodeSize());
  }
  public void testRangeHighlightersRecreateBug() {
    Document document = EditorFactory.getInstance().createDocument("[xxxxxxxxxxxxxx]");

    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, ourProject, true);
    for (int i=0; i<2; i++) {
      RangeMarker m = markupModel.addRangeHighlighter(1, 6, 0, null, HighlighterTargetArea.EXACT_RANGE);
      RangeMarker m2 = markupModel.addRangeHighlighter(2, 7, 0, null, HighlighterTargetArea.EXACT_RANGE);
      RangeMarker m3 = markupModel.addRangeHighlighter(1, 6, 0, null, HighlighterTargetArea.EXACT_RANGE);
      markupModel.removeAllHighlighters();
      assertFalse(m.isValid());
      assertFalse(m2.isValid());
      assertFalse(m3.isValid());
    }
  }
  public void testValidationBug() {
    Document document = EditorFactory.getInstance().createDocument("[xxxxxxxxxxxxxx]");
    final Editor editor = EditorFactory.getInstance().createEditor(document);

    try {
      final FoldRegion[] fold = new FoldRegion[1];
      editor.getFoldingModel().runBatchFoldingOperation(() -> fold[0] = editor.getFoldingModel().addFoldRegion(0, 2, "..."));
      RangeMarker marker = document.createRangeMarker(0, 2);
      document.deleteString(1,2);

      assertTrue(marker.isValid());
      assertNotNull(fold[0]);
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }
  public void testPersistent() {
    String text = "xxx\nzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz";
    Document document = EditorFactory.getInstance().createDocument(text);
    int startOffset = text.indexOf('z');
    int endOffset = text.lastIndexOf('z');
    RangeMarker marker = document.createRangeMarker(startOffset, endOffset, true);

    document.replaceString(startOffset+1, endOffset-1, "ccc");

    assertTrue(marker.isValid());
  }

  public void testPersistentMarkerDoesntImpactNormalMarkers() {
    Document doc = new DocumentImpl("text");
    RangeMarker normal = doc.createRangeMarker(1, 3);
    RangeMarker persistent = doc.createRangeMarker(1, 3, true);

    doc.replaceString(0, 4, "before\ntext\nafter");

    assertTrue(persistent.isValid());
    assertFalse(normal.isValid());
  }

  public void testMoveTextRetargetsMarkers() {
    RangeMarkerEx marker1 = createMarker("01234567890", 1, 3);
    DocumentEx document = (DocumentEx)marker1.getDocument();
    RangeMarker marker2 = document.createRangeMarker(2, 4);

    document.moveText(0, 5, 8);
    assertEquals("56701234890", document.getText());

    assertValidMarker(marker1, 4, 6);
    assertValidMarker(marker2, 5, 7);
  }

  public void testMoveText2() {
    RangeMarkerEx marker1 = createMarker(StringUtil.repeat(" ",100), 0, 0);
    DocumentEx document = (DocumentEx)marker1.getDocument();
    RangeMarker marker2 = document.createRangeMarker(49, 49);

    document.moveText(0, 1, 49);
    marker1.dispose();
    marker2.dispose();
  }

  public void testMoveTextToTheBeginningRetargetsMarkers() {
    RangeMarkerEx marker1 = createMarker("01234567890", 5, 5);
    DocumentEx document = (DocumentEx)marker1.getDocument();
    RangeMarker marker2 = document.createRangeMarker(5, 7);

    document.moveText(4, 7, 1);
    assertEquals("04561237890", document.getText());

    assertValidMarker(marker1, 2, 2);
    assertValidMarker(marker2, 2, 4);
  }

  public void testRangeHighlighterDisposeVsRemoveAllConflict() {
    Document document = EditorFactory.getInstance().createDocument("[xxxxxxxxxxxxxx]");

    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, ourProject, true);
    RangeMarker m = markupModel.addRangeHighlighter(1, 6, 0, null, HighlighterTargetArea.EXACT_RANGE);
    assertTrue(m.isValid());
    markupModel.removeAllHighlighters();
    assertFalse(m.isValid());
    assertEmpty(markupModel.getAllHighlighters());
    m.dispose();
    assertFalse(m.isValid());
  }

  public void testRangeHighlighterLinesInRangeForLongLinePerformance() {
    final int N = 50000;
    Document document = EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol('x', 2 * N));

    final MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, ourProject, true);
    for (int i=0; i<N-1;i++) {
      markupModel.addRangeHighlighter(2*i, 2*i+1, 0, null, HighlighterTargetArea.EXACT_RANGE);
    }
    markupModel.addRangeHighlighter(N / 2, N / 2 + 1, 0, null, HighlighterTargetArea.LINES_IN_RANGE);

    PlatformTestUtil.startPerformanceTest("highlighters lookup", (int)(N*Math.log(N)/1000), () -> {
      List<RangeHighlighterEx> list = new ArrayList<>();
      CommonProcessors.CollectProcessor<RangeHighlighterEx> coll = new CommonProcessors.CollectProcessor<>(list);
      for (int i=0; i<N-1;i++) {
        list.clear();
        markupModel.processRangeHighlightersOverlappingWith(2*i, 2*i+1, coll);
        assertEquals(2, list.size());  // 1 line plus one exact range marker
      }
    }).assertTiming();
  }

  public void testRangeHighlighterIteratorOrder() {
    Document document = EditorFactory.getInstance().createDocument("1234567890");

    final MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, ourProject, true);
    RangeHighlighter exact = markupModel.addRangeHighlighter(3, 6, 0, null, HighlighterTargetArea.EXACT_RANGE);
    RangeHighlighter line = markupModel.addRangeHighlighter(4, 5, 0, null, HighlighterTargetArea.LINES_IN_RANGE);
    List<RangeHighlighter> list = new ArrayList<>();
    markupModel.processRangeHighlightersOverlappingWith(2, 9, new CommonProcessors.CollectProcessor<>(list));
    assertEquals(Arrays.asList(line, exact), list);
  }

  public void testLazyRangeMarkers() {
    psiFile = createFile("x.txt", "xxx");

    LazyRangeMarkerFactoryImpl factory = (LazyRangeMarkerFactoryImpl)LazyRangeMarkerFactory.getInstance(getProject());
    VirtualFile virtualFile = psiFile.getVirtualFile();
    LazyRangeMarkerFactoryImpl.LazyMarker marker = (LazyRangeMarkerFactoryImpl.LazyMarker)factory.createRangeMarker(virtualFile, 0);
    WeakList<LazyRangeMarkerFactoryImpl.LazyMarker> markers = LazyRangeMarkerFactoryImpl.getMarkers(virtualFile);
    assertSame(marker, assertOneElement(markers));

    assertFalse(marker.isDelegated());
    assertTrue(marker.isValid());
    assertEquals(0, marker.getStartOffset());
    assertFalse(marker.isDelegated());

    marker.dispose();
    assertFalse(marker.isValid());
    assertEmpty(LazyRangeMarkerFactoryImpl.getMarkers(virtualFile));


    marker = (LazyRangeMarkerFactoryImpl.LazyMarker)factory.createRangeMarker(virtualFile, 0);
    assertFalse(marker.isDelegated());
    assertTrue(marker.isValid());
    assertEquals(0, marker.getStartOffset());
    assertFalse(marker.isDelegated());

    Document document = marker.getDocument();
    document.insertString(2, "yyy");
    assertTrue(marker.isDelegated());
    assertTrue(marker.isValid());
    assertEquals(0, marker.getStartOffset());

    assertEmpty(LazyRangeMarkerFactoryImpl.getMarkers(virtualFile));
    marker.dispose();
    assertEmpty(LazyRangeMarkerFactoryImpl.getMarkers(virtualFile));
  }

  public void testNonGreedyMarkersGrowOnAppendingReplace() {
    Document doc = new DocumentImpl("foo");
    RangeMarker marker = doc.createRangeMarker(0, 3);
    assertFalse(marker.isGreedyToLeft());
    assertFalse(marker.isGreedyToRight());

    doc.replaceString(0, 3, "foobar");
    assertValidMarker(marker, 0, 6);

    doc.replaceString(0, 3, "goofoo");
    assertValidMarker(marker, 0, 9);
  }

  public void testMoveTextCrashesStress() {
    AtomicReference<List<Integer>> minOffsets = new AtomicReference<>();
    AtomicInteger failPrinted = new AtomicInteger();
    String text = StringUtil.repeat("blah", 1000);
    IntStream.range(0, 10_000).parallel().forEach(iter -> {
      DocumentEx doc = new DocumentImpl(text, true);
      List<Integer> offsets = new ArrayList<>();

      try {
        List<Integer> oldOffsets = minOffsets.get();
        int n = oldOffsets == null ? 261 : oldOffsets.size()/4;
        //if (iter%1000==0) System.out.println(iter +" (length="+n+")");

        List<RangeMarker> markers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
          int limit = doc.getTextLength() + 1;
          int offset = raaand(limit);
          int startOffset = 1+raaand(limit - 3);
          int endOffset = startOffset + 1 + raaand(limit - startOffset - 2);
          int targetOffset = raaand(limit - (endOffset - startOffset + 1));
          if (targetOffset >= startOffset) targetOffset += endOffset - startOffset+1;

          offsets.add(offset);
          offsets.add(startOffset);
          offsets.add(endOffset);
          offsets.add(targetOffset);
          RangeMarker marker = doc.createRangeMarker(offset, offset);
          markers.add(marker);
          doc.moveText(startOffset, endOffset, targetOffset);
          assertTrue(marker.toString(), marker.isValid());
          for (RangeMarker rm : markers) {
            assertTrue(rm.isValid());
          }
        }
      }
      catch (AssertionError e) {
        List<Integer> updated = minOffsets.updateAndGet(oldMarkers -> oldMarkers == null || offsets.size() < oldMarkers.size() ? offsets : oldMarkers);
        if (updated == offsets && failPrinted.getAndIncrement() < 10) {
          System.err.println("Aha: " + offsets.size() + " " + e+"\n"+minOffsets);
        }
      }
      catch (Throwable e) {
        e.printStackTrace();
        throw e;
      }
    });
    if (minOffsets.get() == null) {
      System.out.println("Can't find anything, giving up");
    }
    else {
      System.err.println("Moves and offsets ("+minOffsets.get().size()+"): " + minOffsets);
      fail();
    }
  }

  private static int raaand(int limit) {
    return limit == 0 ? 0 : ThreadLocalRandom.current().nextInt(limit);
  }

  public void testWeirdMoveText1() {
    int[] movesAndOffsets = {3375, 520, 1159, 2314, 3445, 1548, 2840, 3667, 333, 2517, 3072, 686, 174, 1703, 2848, 3575, 3380, 782, 2699, 2977, 3162, 903, 2979, 226, 2381, 131, 2996, 3944, 1722, 649, 1429, 2916, 383, 2945, 3273, 383, 500, 415, 1003, 1427, 824, 1400, 1474, 3291, 3386, 2408, 2979, 724, 2536, 242, 1423, 1934, 2562, 1451, 1934, 42, 1247, 3378, 3458, 1839, 3282, 180, 1523, 2905, 2067, 1631, 2476, 2536, 102, 502, 2361, 217, 863, 2898, 3836, 1669, 166, 3023, 3034, 2631, 2682, 3877, 3891, 88, 1708, 199, 957, 2563, 3608, 345, 2002, 3037, 1099, 55, 132, 3972, 3877, 3158, 3757, 3830, 3318, 500, 1136, 1729, 2198, 1742, 2851, 219, 1381, 66, 2743, 2990, 1549, 1466, 3407, 3945, 658, 2968, 3756, 1199, 1278, 1581, 2556, 2654, 2999, 603, 2765, 25, 1942, 1143, 3167, 1060, 1003, 671, 975, 3487, 2876, 104, 388, 1715, 3022, 670, 1685, 3834, 1595, 701, 2077, 3565, 1072, 2746, 3872, 1479, 1514, 166, 1226, 3743, 2557, 2887, 3477, 899, 3963, 1840, 3177, 500, 2947, 75, 2249, 2495, 1207, 3817, 3923, 1236, 3317, 513, 1566, 2376, 678, 1109, 2081, 104, 865, 2120, 3331, 588, 265, 1458, 3843, 312, 3813, 709, 934, 1754, 3914, 1769, 2724, 432, 371, 410, 994, 383, 747, 564, 2239, 3072, 3770, 761, 2889, 3748, 2845, 673, 803, 1127, 3538, 233, 1525, 3843, 680, 685, 1618, 2816, 150, 3155, 3972, 1316, 3992, 697, 2771, 3572, 3930, 1285, 1914, 2748, 1480, 1362, 3120, 435, 2250, 2175, 3214, 37, 1431, 765, 1807, 3032, 2566, 1388, 3415, 989, 1078, 2498, 3482, 710, 2780, 613, 914, 449, 3955, 95, 3197, 3491};
    doTextMoves(movesAndOffsets);
  }
  public void testWeirdMoveText2() {
    int[] movesAndOffsets = {686, 2802, 3005, 2522, 781, 2766, 3857, 827, 2343, 1686, 2001, 3625, 1193, 472, 3300, 3537, 421, 2255, 2826, 3423, 3650, 2040, 2706, 1877, 2331, 1033, 3226, 134, 115, 3776, 3837, 2422, 1916, 3478, 3863, 54, 3365, 1245, 2089, 779, 3778, 1483, 1723, 1245, 1496, 662, 1091, 427, 1580, 2763, 3723, 1778, 3736, 2016, 3854, 966, 863, 291, 582, 2933, 156, 1612, 3241, 1351, 559, 1121, 2315, 831, 2450, 1488, 3620, 1444, 606, 590, 3012, 3724, 2555, 3635, 3905, 1221, 3268, 2925, 3603, 221, 2667, 2821, 3176, 2770, 3992, 1782, 2125, 3450, 3690, 3356, 3608, 1244, 3900, 770, 1596, 503, 1213, 3642, 3959, 1905, 2711, 3827, 3999, 1934, 2215, 1391, 1882, 2298, 2637, 3201, 3395, 1565, 3542, 2745, 2992, 2708, 2278, 352, 2875, 3636, 574, 710, 1443, 2808, 1516, 2351, 3069, 520, 833, 2271, 2671, 3451, 2679, 3692, 3712, 1838, 1767, 1782, 3447, 3768, 1494, 1288, 1649, 1677, 1335, 127, 1697, 3297, 1445, 3564, 3691, 3032, 2155, 82, 717, 2360, 1314, 316, 3024, 3268, 2293, 283, 3825, 3968, 1708, 1411, 1471, 159, 925, 3245, 3492, 2623, 2164, 586, 3208, 3940, 3761, 1763, 2531, 837, 523, 3202, 3282, 44, 1770, 1527, 3101, 558, 2776, 1352, 1560, 1230, 3998, 3570, 3696, 3222, 1989, 567, 1919, 2735, 2934, 3357, 3594, 3710, 1289, 3452, 3781, 1765, 1127, 1482, 1961, 1154, 487, 3740, 3977, 2453, 3305, 3266, 3921, 2943, 3509, 637, 3081, 3514, 50, 3035, 3678, 3866, 3024, 1789, 2211, 2779, 3950, 2330, 2687, 2267, 1738, 2599, 2644, 2074, 3812, 3540, 3609, 2484, 2616, 1443, 3211, 408, 2718, 1909, 2136, 1073, 1808, 102, 3239, 55, 3239, 3492, 3525, 1099};
    doTextMoves(movesAndOffsets);
  }

  private static void doTextMoves(int[] movesAndOffsets) {
    DocumentEx doc = new DocumentImpl(StringUtil.repeat("blah", 1000));
    List<RangeMarker> markers = new ArrayList<>();
    for (int i = 0; i < movesAndOffsets.length; i+=4) {
      int offset = movesAndOffsets[i];
      int startOffset=movesAndOffsets[i+1];
      int endOffset=movesAndOffsets[i+2];
      int targetOffset=movesAndOffsets[i+3];
      RangeMarker marker = doc.createRangeMarker(offset, offset);
      markers.add(marker);

      doc.moveText(startOffset, endOffset, targetOffset);
      assertTrue(marker.toString(), marker.isValid());
      for (RangeMarker rm : markers) {
        assertTrue(rm.isValid());
      }
    }
  }

  public void testGetOffsetPerformance() {
    DocumentEx doc = new DocumentImpl(StringUtil.repeat("blah", 1000));
    List<RangeMarker> markers = new ArrayList<>();
    int N = 100_000;
    for (int i = 0; i < N; i++) {
      int start = i % doc.getTextLength();
      int end = start + 1;
      RangeMarker marker = doc.createRangeMarker(start, end);
      markers.add(marker);
    }
    PlatformTestUtil.startPerformanceTest("RM.getStartOffset", 15000, ()->{
      doc.insertString(0, " ");
      for (int i=0; i<1000; i++) {
        for (RangeMarker rm : markers) {
          int length = rm.getEndOffset() - rm.getStartOffset();
          assertEquals(1, length);
          assertTrue(rm.isValid());
        }
      }
      doc.deleteString(0, 1);
    }).assertTiming();
  }

  public void testGetOffsetDuringModificationsPerformance() {
    DocumentEx doc = new DocumentImpl(StringUtil.repeat("blah", 1000));
    List<RangeMarker> markers = new ArrayList<>();
    int N = 100_000;
    for (int i = 0; i < N; i++) {
      int start = i % doc.getTextLength();
      int end = start + 1;
      RangeMarker marker = doc.createRangeMarker(start, end);
      markers.add(marker);
    }
    PlatformTestUtil.startPerformanceTest("RM.getStartOffset", 20000, ()->{
      doc.insertString(0, " ");
      for (int i=0; i<1000; i++) {
        for (int j = 0; j < markers.size(); j++) {
          RangeMarker rm = markers.get(j);
          doc.setModificationStamp(i+j);
          int length = rm.getEndOffset() - rm.getStartOffset();
          assertEquals(1, length);
          assertTrue(rm.isValid());
        }
      }
      doc.deleteString(0, 1);
    }).assertTiming();
  }

  public void testDocModificationPerformance() {
    DocumentEx doc = new DocumentImpl(StringUtil.repeat("blah", 1000));
    List<RangeMarker> markers = new ArrayList<>();
    int N = 100_000;
    for (int i = 0; i < N; i++) {
      int start = i % doc.getTextLength();
      int end = start + 1;
      RangeMarker marker = doc.createRangeMarker(start, end);
      markers.add(marker);
    }
    PlatformTestUtil.startPerformanceTest("insert/delete string", 2000, ()->{
      for (int i=0; i<15000; i++) {
        doc.insertString(0, " ");
        doc.deleteString(0, 1);
      }
    }).assertTiming();
    for (RangeMarker rm : markers) {
      assertTrue(rm.isValid());
    }
  }

  public void testRMInsertPerformance() {
    DocumentEx doc = new DocumentImpl(StringUtil.repeat("blah", 1000));
    int N = 100_000;
    List<RangeMarker> markers = new ArrayList<>(N);
    PlatformTestUtil.startPerformanceTest("createRM", 2000, ()->{
      for (int i = 0; i < N; i++) {
        int start = i % doc.getTextLength();
        int end = start + 1;
        RangeMarker marker = doc.createRangeMarker(start, end);
        markers.add(marker);
      }
      for (RangeMarker marker : markers) {
        marker.dispose();
      }
    }).assertTiming();
  }

  public void testProcessOverlappingPerformance() {
    DocumentEx doc = new DocumentImpl(StringUtil.repeat("blah", 1000));
    int N = 100_000;
    List<RangeMarker> markers = new ArrayList<>(N);
    for (int i = 0; i < N; i++) {
      int start = i % doc.getTextLength();
      int end = start + 1;
      RangeMarker marker = doc.createRangeMarker(start, end);
      markers.add(marker);
    }
    PlatformTestUtil.startPerformanceTest(getTestName(false), 2000, ()->{
      for (int it=0;it<50;it++) {
        for (int i=1; i<doc.getTextLength()-1;i++) {
          List<RangeMarker> overlaps = new ArrayList<>();
          doc.processRangeMarkersOverlappingWith(i,i+1, rm->{
            overlaps.add(rm);
            return true;
          });
          if (overlaps.isEmpty()) {
            fail("it="+it+"; i="+i);
          }
        }
      }
    }).assertTiming();
    assertNotEmpty(markers);
  }
}
