package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.RedBlackTree;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiToDocumentSynchronizer;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.Timings;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * @author mike
 */
public class RangeMarkerTest extends LightPlatformTestCase {
  @Override
  protected void runTest() throws Throwable {
    if (getTestName(false).contains("NoVerify")) {
      super.runTest();
      return;
    }
    boolean oldVerify = RedBlackTree.VERIFY;
    RedBlackTree.VERIFY = true;
    final Throwable[] ex = {null};
    try {
      if (getTestName(false).contains("NoCommand")) {
        super.runTest();
        return;
      }
      CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                try {
                  RangeMarkerTest.super.runTest();
                }
                catch (Throwable throwable) {
                  ex[0] = throwable;
                }
              }
            });
          }
        }, "", null);
    }
    finally {
      RedBlackTree.VERIFY = oldVerify;
    }

    if (ex[0] != null) throw ex[0];
  }

  public void testCreation() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    assertEquals(2, marker.getStartOffset());
    assertEquals(5, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testDeleteBeforeStart() throws Exception {
    RangeMarker marker = createMarker("01[234]56789");

    marker.getDocument().deleteString(0, 1);

    assertEquals(1, marker.getStartOffset());
    assertEquals(4, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testInsertIntoRange() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().insertString(4, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(8, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testInsertIntoPoint() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 2);

    marker.getDocument().insertString(2, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(2, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testDeletePoint() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 2);

    marker.getDocument().deleteString(1, 3);

    assertFalse(marker.isValid());
  }

  public void testDeleteRangeInside() throws Exception {
    RangeMarker marker = createMarker("0123456789", 1, 7);

    marker.getDocument().deleteString(2, 5);

    assertTrue(marker.isValid());
  }

  public void testReplaceRangeToSingleChar() throws Exception {
    RangeMarker marker = createMarker("0123456789", 1, 7);

    marker.getDocument().replaceString(2, 5, " ");

    assertTrue(marker.isValid());
  }

  public void testReplaceWholeRange() throws Exception {
    RangeMarker marker = createMarker("0123456789", 1, 7);
    marker.getDocument().replaceString(1, 7, "abc");
    assertTrue(marker.isValid());
    assertEquals(1, marker.getStartOffset());
    assertEquals(4, marker.getEndOffset());
  }

  public void testUpdateInvalid() throws Exception {
    RangeMarker marker = createMarker("01[]23456789");

    marker.getDocument().deleteString(1, 3);
    assertFalse(marker.isValid());

    marker.getDocument().insertString(2, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(2, marker.getEndOffset());
    assertFalse(marker.isValid());
  }

  public void testInsertAfterEnd() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().insertString(6, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(5, marker.getEndOffset());
    assertTrue(marker.isValid());
  }


  public void testDeleteEndPart() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().deleteString(4, 6);

    assertTrue(marker.isValid());
    assertEquals(2, marker.getStartOffset());
    assertEquals(4, marker.getEndOffset());
  }

  public void testDeleteStartPart() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().deleteString(0, 4);

    assertTrue(marker.isValid());
    assertEquals(0, marker.getStartOffset());
    assertEquals(1, marker.getEndOffset());
  }

  public void testReplaceStartPartInvalid() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().replaceString(0, 4, "xxxx");

    assertTrue(marker.isValid());
    assertEquals(4, marker.getStartOffset());
    assertEquals(5, marker.getEndOffset());
  }

  public void testDeleteFirstChar() throws Exception {
    RangeMarker marker = createMarker("0123456789", 0, 5);

    marker.getDocument().deleteString(0, 1);

    assertTrue(marker.isValid());
    assertEquals(0, marker.getStartOffset());
    assertEquals(4, marker.getEndOffset());
  }

  public void testInsertBeforeStart() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().insertString(0, "xxx");

    assertEquals(5, marker.getStartOffset());
    assertEquals(8, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testInsertIntoStart() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().insertString(2, "xxx");

    assertTrue(marker.isValid());
    assertEquals(5, marker.getStartOffset());
    assertEquals(8, marker.getEndOffset());
  }

  public void testInsertIntoStartExpandToLeft() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.setGreedyToLeft(true);

    marker.getDocument().insertString(2, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(8, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testInsertIntoEnd() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().insertString(5, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(5, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testInsertIntoEndExpandRight() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.setGreedyToRight(true);

    marker.getDocument().insertString(5, "xxx");

    assertEquals(2, marker.getStartOffset());
    assertEquals(8, marker.getEndOffset());
    assertTrue(marker.isValid());
  }

  public void testNoNegative() throws Exception {
    RangeMarker marker = createMarker("package safd;\n\n[import javax.swing.JPanel;]\nimport java.util.ArrayList;\n\nclass T{}");

    marker.getDocument()
      .replaceString(15, 15 + "import javax.swing.JPanel;\nimport java.util.ArrayList;".length(), "import java.util.ArrayList;");

    assertEquals(15, marker.getStartOffset());
  }

  public void testReplaceRightIncludingFirstChar() throws Exception {
    String s = "12345\n \n12345";
    RangeMarker marker = createMarker(s, 6, 8);

    marker.getDocument().replaceString(0, s.length(), s.replaceAll(" ", ""));

    assertTrue(marker.isValid());
    assertEquals(6, marker.getStartOffset());
    assertEquals(7, marker.getEndOffset());
  }

  public void testDeleteRightPart() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().deleteString(4, 6);

    assertTrue(marker.isValid());
    assertEquals(2, marker.getStartOffset());
    assertEquals(4, marker.getEndOffset());
  }

  public void testDeleteRightPart2() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().deleteString(4, 5);

    assertTrue(marker.isValid());
    assertEquals(2, marker.getStartOffset());
    assertEquals(4, marker.getEndOffset());
  }

  public void testReplaceRightPartInvalid() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().replaceString(4, 6, "xxx");

    assertTrue(marker.isValid());
    assertEquals(2, marker.getStartOffset());
    assertEquals(4, marker.getEndOffset());
  }

  public void testDeleteWholeRange() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);
    marker.getDocument().deleteString(1, 6);
    assertFalse(marker.isValid());
  }

  public void testDeleteExactRange() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().deleteString(2, 5);
    assertTrue(marker.isValid());
    assertEquals(2, marker.getStartOffset());
    assertEquals(2, marker.getEndOffset());
  }

  public void testDeleteJustBeforeStart() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().deleteString(0, 2);
    assertTrue(marker.isValid());
    assertEquals(0, marker.getStartOffset());
    assertEquals(3, marker.getEndOffset());
  }

  public void testDeleteRightAfterEnd() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 2);

    marker.getDocument().deleteString(2, 5);
    assertTrue(marker.isValid());
    assertEquals(2, marker.getStartOffset());
    assertEquals(2, marker.getEndOffset());
  }

  public void testReplacementWithOldTextOverlap() throws Exception {
    RangeMarker marker = createMarker("0123456789", 2, 5);

    marker.getDocument().replaceString(0, 10, "0123456789");
    assertTrue(marker.isValid());
    assertEquals(2, marker.getStartOffset());
    assertEquals(5, marker.getEndOffset());
  }

  // Psi -> Document synchronization

  public void testPsi2Doc1() throws Exception {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker("0123456789", 2, 5);
    PsiToDocumentSynchronizer synchronizer = ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject())).getSynchronizer();
    synchronizer.startTransaction(getProject(), marker.getDocument(), null);

    synchronizer.insertString(marker.getDocument(), 3, "a");
    buffer.insert(3, "a");

    synchronizer.doCommitTransaction(marker.getDocument());

    assertEquals(buffer.toString(), marker.getDocument().getText());

    assertTrue(marker.isValid());
    assertEquals(2, marker.getStartOffset());
    assertEquals(6, marker.getEndOffset());
  }

  public void testDocSynchronizerPrefersLineBoundaryChanges() throws Exception {
    RangeMarker marker = createMarker("import java.awt.List;\n" +
                                      "[import java.util.ArrayList;\n]" +
                                      "import java.util.HashMap;\n" +
                                      "import java.util.Map;");
    PsiToDocumentSynchronizer synchronizer = ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject())).getSynchronizer();
    Document document = marker.getDocument();
    synchronizer.startTransaction(getProject(), document, null);

    String newText = StringUtil.replaceSubstring(document.getText(), TextRange.create(marker), "");
    synchronizer.replaceString(document, 0, document.getTextLength(), newText);

    final List<DocumentEvent> events = new ArrayList<DocumentEvent>();
    document.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        events.add(e);
      }
    });
    synchronizer.doCommitTransaction(document);

    assertEquals(newText, document.getText());
    DocumentEvent event = assertOneElement(events);
    assertEquals("DocumentEventImpl[myOffset=22, myOldLength=28, myNewLength=0, myOldString='import java.util.ArrayList;\n', myNewString=''].", event.toString());
  }

  public void testPsi2DocReplaceAfterAdd() throws Exception {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker("0123456789", 2, 5);
    PsiToDocumentSynchronizer synchronizer = ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject())).getSynchronizer();
    synchronizer.startTransaction(getProject(), marker.getDocument(), null);

    synchronizer.insertString(marker.getDocument(), 1, "a");
    buffer.insert(1, "a");

    synchronizer.replaceString(marker.getDocument(), 3, 4, "a");
    buffer.replace(3, 4, "a");

    synchronizer.doCommitTransaction(marker.getDocument());

    assertEquals(buffer.toString(), marker.getDocument().getText());

    assertTrue(marker.isValid());
    assertEquals(3, marker.getStartOffset());
    assertEquals(6, marker.getEndOffset());
  }

  public void testPsi2DocMergeReplaceAfterAdd() throws Exception {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker("0123456789", 2, 5);
    PsiToDocumentSynchronizer synchronizer = ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject())).getSynchronizer();
    synchronizer.startTransaction(getProject(), marker.getDocument(), null);

    synchronizer.insertString(marker.getDocument(), 1, "a");
    buffer.insert(1, "a");

    synchronizer.replaceString(marker.getDocument(), 3, 4, "a");
    buffer.replace(3, 4, "a");

    synchronizer.replaceString(marker.getDocument(), 3, 5, "bb");
    buffer.replace(3, 5, "bb");
    final PsiToDocumentSynchronizer.DocumentChangeTransaction transaction = synchronizer.getTransaction(marker.getDocument());
    final Set<Pair<PsiToDocumentSynchronizer.MutableTextRange, StringBuffer>> affectedFragments = transaction.getAffectedFragments();
    assertEquals(affectedFragments.size(), 2);

    synchronizer.doCommitTransaction(marker.getDocument());

    assertEquals(buffer.toString(), marker.getDocument().getText());

    assertTrue(marker.isValid());
    assertEquals(3, marker.getStartOffset());
    assertEquals(6, marker.getEndOffset());
  }

  public void testPsi2DocMergeReplaceWithMultipleAdditions() throws Exception {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker("0123456789", 2, 5);
    PsiToDocumentSynchronizer synchronizer = ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject())).getSynchronizer();
    synchronizer.startTransaction(getProject(), marker.getDocument(), null);

    synchronizer.replaceString(marker.getDocument(), 0, 10, "0");
    buffer.replace(0, 10, "0");

    for (int i = 1; i < 10; i++) {
      synchronizer.insertString(marker.getDocument(), i, "" + i);
      buffer.insert(i, "" + i);
    }
    final PsiToDocumentSynchronizer.DocumentChangeTransaction transaction = synchronizer.getTransaction(marker.getDocument());
    final Set<Pair<PsiToDocumentSynchronizer.MutableTextRange, StringBuffer>> affectedFragments = transaction.getAffectedFragments();
    assertEquals(1, affectedFragments.size());

    synchronizer.doCommitTransaction(marker.getDocument());

    assertEquals(buffer.toString(), marker.getDocument().getText());

    assertTrue(marker.isValid());
    assertEquals(2, marker.getStartOffset());
    assertEquals(5, marker.getEndOffset());
  }

  public void testPsi2DocMergeMultipleAdditionsWithReplace() throws Exception {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker("0123456789", 2, 5);
    PsiToDocumentSynchronizer synchronizer = ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject())).getSynchronizer();
    synchronizer.startTransaction(getProject(), marker.getDocument(), null);
    final PsiToDocumentSynchronizer.DocumentChangeTransaction transaction = synchronizer.getTransaction(marker.getDocument());
    final Set<Pair<PsiToDocumentSynchronizer.MutableTextRange, StringBuffer>> affectedFragments = transaction.getAffectedFragments();


    for (int i = 0; i < 10; i++) {
      synchronizer.insertString(marker.getDocument(), i, "" + i);
      buffer.insert(i, "" + i);
    }

    assertEquals(1, affectedFragments.size());
    synchronizer.replaceString(marker.getDocument(), 0, 20, "0123456789");
    buffer.replace(0, 20, "0123456789");

    assertEquals(1, affectedFragments.size());

    synchronizer.doCommitTransaction(marker.getDocument());

    assertEquals(buffer.toString(), marker.getDocument().getText());

    assertTrue(marker.isValid());
    assertEquals(2, marker.getStartOffset());
    assertEquals(5, marker.getEndOffset());
  }

  public void testPsi2DocSurround() throws Exception {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker("0123456789", 2, 5);
    PsiToDocumentSynchronizer synchronizer = ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject())).getSynchronizer();
    synchronizer.startTransaction(getProject(), marker.getDocument(), null);

    synchronizer.replaceString(marker.getDocument(), 3, 5, "3a4");
    buffer.replace(3, 5, "3a4");

    synchronizer.insertString(marker.getDocument(), 3, "b");
    buffer.insert(3, "b");

    synchronizer.insertString(marker.getDocument(), 7, "d");
    buffer.insert(7, "d");

    final PsiToDocumentSynchronizer.DocumentChangeTransaction transaction = synchronizer.getTransaction(marker.getDocument());
    final Set<Pair<PsiToDocumentSynchronizer.MutableTextRange, StringBuffer>> affectedFragments = transaction.getAffectedFragments();
    assertEquals(3, affectedFragments.size());

    synchronizer.doCommitTransaction(marker.getDocument());

    assertEquals(buffer.toString(), marker.getDocument().getText());

    assertTrue(marker.isValid());
    assertEquals(2, marker.getStartOffset());
    assertEquals(7, marker.getEndOffset());
  }

  public void testPsi2DocForwardRangesChanges() throws Exception {
    StringBuilder buffer = new StringBuilder("0123456789");
    RangeMarker marker = createMarker("0123456789", 2, 5);
    PsiToDocumentSynchronizer synchronizer = ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject())).getSynchronizer();
    Document document = marker.getDocument();
    synchronizer.startTransaction(getProject(), document, null);

    synchronizer.replaceString(document, 4, 5, "3a4");
    buffer.replace(4, 5, "3a4");

    synchronizer.insertString(document, 7, "b");
    buffer.insert(7, "b");

    synchronizer.insertString(document, 1, "b");
    buffer.insert(1, "b");

    synchronizer.doCommitTransaction(document);

    assertEquals(buffer.toString(), document.getText());

    assertTrue(marker.isValid());
    assertEquals(3, marker.getStartOffset());
    assertEquals(8, marker.getEndOffset());
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

  private static List<RangeMarker> add(DocumentEx document, int... offsets) {
    List<RangeMarker> result = new ArrayList<RangeMarker>();
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
      List<Pair<RangeMarker, TextRange>> adds = new ArrayList<Pair<RangeMarker, TextRange>>();
      List<Pair<RangeMarker, TextRange>> dels = new ArrayList<Pair<RangeMarker, TextRange>>();


      try {
        for (int i = 0; i < 30; i++) {
          int x = gen.nextInt(N);
          int y = x + gen.nextInt(N - x);
          if (gen.nextBoolean()) {
            x = 0;
            y = document.getTextLength();
          }
          RangeMarkerEx r = (RangeMarkerEx)document.createRangeMarker(x, y);
          adds.add(Pair.create((RangeMarker)r, TextRange.create(r)));
        }
        List<Pair<RangeMarker, TextRange>> candidates = new ArrayList<Pair<RangeMarker, TextRange>>(adds);
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
    List<RangeMarker> mm =
      add(document, 4,8, 4,4, 4,9, 0,2, 6,8
      );
    edit(document, 3,2,0);
  }

  public void testE7() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 6,7, 0,3, 3,6, 5,9, 2,9
      );
    edit(document, 5,2,0);
  }

  public void testE8() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 5,5, 8,8, 1,3, 3,9
      );
    edit(document, 4,3,0);
  }

  public void testE9() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 4,5, 9,9, 1,2, 0,3
      );
    edit(document, 0,3,0);
  }

  public void testE10() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 9,9, 6,8, 8,8, 5,9
      );
    edit(document, 2,6,0,  2,0,4);
  }

  public void testE11() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 9,9, 7,7, 1,6, 3,7
      );
    //edit(document, 0,0,0);
    delete(mm, 1);
  }
  public void testE12() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 10));
    List<RangeMarker> mm =
      add(document, 3,3, 8,8, 5,5, 5,6
      );
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
    List<RangeMarker> mm = add(document, 6,11, 2,13, 17,17, 13,19, 2,3, 9,10, 10,11, 14,14, 1,3, 4,12, 14,15, 3,10, 14,14, 4,4, 4,8, 6,14, 8,16, 2,12, 11,19, 10,13
    );
    edit(document, 19,0,0,  7,3,0,  16,0,3);
  }

  public void testE15() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 100));
    List<RangeMarker> mm = add(document, 90,93, 0,9, 44,79, 4,48, 44,99, 53,64, 59,82, 12,99, 81,86, 8,40, 24,55, 32,50, 74,79, 14,94, 7,14
    );
    edit(document, 34,0,4,  99,0,3);
  }

  public void testE16() {
    DocumentEx document = (DocumentEx)EditorFactory.getInstance().createDocument(StringUtil.repeatSymbol(' ', 100));
    List<RangeMarker> mm = add(document, 29,63, 47,52, 72,86, 19,86, 13,55, 18,57, 92,95, 83,99, 41,80, 53,85, 10,30, 28,44, 23,32, 70,95, 14,28
    );
    edit(document, 67,5,0,  1,0,4);
    delete(mm, 11);
  }

  public void testRandomEdit_NoCommand() {
    final int N = 100;

    final Random gen = new Random();
    int N_TRIES = Timings.adjustAccordingToMySpeed(7000);
    System.out.println("N_TRIES = " + N_TRIES);
    DocumentEx document = null;
    for (int tryn=0; tryn < N_TRIES;tryn++) {
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
        protected void run(Result result) throws Exception {
          List<Pair<RangeMarker, TextRange>> adds = new ArrayList<Pair<RangeMarker, TextRange>>();
          List<Pair<RangeMarker, TextRange>> dels = new ArrayList<Pair<RangeMarker, TextRange>>();
          List<Trinity<Integer, Integer, Integer>> edits = new ArrayList<Trinity<Integer, Integer, Integer>>();

          try {
            for (int i = 0; i < 30; i++) {
              int x = gen.nextInt(N);
              int y = x + gen.nextInt(N - x);
              RangeMarkerEx r = (RangeMarkerEx)finalDocument.createRangeMarker(x, y);
              adds.add(Pair.create((RangeMarker)r, TextRange.create(r)));
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
            List<Pair<RangeMarker, TextRange>> candidates = new ArrayList<Pair<RangeMarker, TextRange>>(adds);
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

  private static RangeMarkerEx createMarker(String s, final int start, final int end) {
    final Document document = EditorFactory.getInstance().createDocument(s);
    return (RangeMarkerEx)document.createRangeMarker(start, end);
  }

  private static RangeMarkerEx createMarker(@NonNls String string) {
    int start = string.indexOf('[');
    assertTrue(start != -1);
    string = string.replace("[", "");
    int end = string.indexOf(']');
    assertTrue(end != -1);
    string = string.replace("]", "");
    return createMarker(string, start, end);
  }

  public void testRangeMarkersAreWeakReferenced_NoVerify() throws Exception {
    final Document document = EditorFactory.getInstance().createDocument("[xxxxxxxxxxxxxx]");
    int COUNT = 100;
    for (int i = 0; i < COUNT; i++) {
      document.createRangeMarker(0, document.getTextLength());
    }

    LeakHunter.checkLeak(document, RangeMarker.class);
  }

  public void testRangeMarkersAreLazyCreated() throws Exception {
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
  public void testRangeHighlightersRecreateBug() throws Exception {
    Document document = EditorFactory.getInstance().createDocument("[xxxxxxxxxxxxxx]");

    MarkupModel markupModel = document.getMarkupModel();
    for (int i=0; i<2; i++) {
      RangeMarker m = markupModel.addRangeHighlighter(1, 6, 0, null, HighlighterTargetArea.EXACT_RANGE);
      RangeMarker m2 = markupModel.addRangeHighlighter(2, 7, 0, null, HighlighterTargetArea.EXACT_RANGE);
      RangeMarker m3 = markupModel.addRangeHighlighter(1, 6, 0, null, HighlighterTargetArea.EXACT_RANGE);
      markupModel.removeAllHighlighters();
    }
  }
  public void testValidationBug() throws Exception {
    Document document = EditorFactory.getInstance().createDocument("[xxxxxxxxxxxxxx]");
    final Editor editor = EditorFactory.getInstance().createEditor(document);

    try {
      final FoldRegion[] fold = new FoldRegion[1];
      editor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          fold[0] = editor.getFoldingModel().addFoldRegion(0, 2, "");
        }
      });
      RangeMarker marker = document.createRangeMarker(0, 2);
      document.deleteString(1,2);

      assertTrue(marker.isValid());
      assertFalse(fold[0].isValid());
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }
}
