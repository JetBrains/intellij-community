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

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.LineStatusTracker.Mode;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.ex.RangesBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.List;

/**
 * author: lesya
 */
public abstract class BaseLineStatusTrackerTestCase extends LightPlatformTestCase {
  protected VirtualFile myFile;
  protected Document myDocument;
  protected Document myUpToDateDocument;
  protected LineStatusTracker myTracker;

  @Override
  public void tearDown() throws Exception {
    try {
      releaseTracker();
    }
    finally {
      super.tearDown();
    }
  }

  protected void runCommand(@NotNull final Runnable task) {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(task), "", null);
  }

  protected void replaceString(final int startOffset, final int endOffset, @NotNull final String s) {
    runCommand(() -> myDocument.replaceString(startOffset, endOffset, s));
  }

  protected void insertString(final int offset, @NotNull final String s) {
    runCommand(() -> myDocument.insertString(offset, s));
  }

  protected void deleteString(final int startOffset, final int endOffset) {
    runCommand(() -> myDocument.deleteString(startOffset, endOffset));
  }

  protected void rollback(@NotNull final Range range) {
    runCommand(() -> myTracker.rollbackChanges(range));
  }

  protected void rollback(@NotNull final BitSet lines) {
    runCommand(() -> myTracker.rollbackChanges(lines));
  }

  protected void compareRanges() throws FilesTooBigForDiffException {
    List<Range> expected = RangesBuilder.createRanges(myDocument, myUpToDateDocument);
    List<Range> actual = myTracker.getRanges();
    assertEquals(expected, actual);
  }

  protected void createDocument(@NotNull String text) throws FilesTooBigForDiffException {
    createDocument(text, text);
    compareRanges();
    assertEquals(0, DocumentMarkupModel.forDocument(myDocument, getProject(), true).getAllHighlighters().length);
  }

  protected void createDocument(@NotNull String text, @NotNull final String upToDateDocument) {
    createDocument(text, upToDateDocument, false);
  }

  protected void createDocument(@NotNull String text, @NotNull final String upToDateDocument, boolean smart) {
    myFile = new LightVirtualFile("LSTTestFile", PlainTextFileType.INSTANCE, text);
    myDocument = FileDocumentManager.getInstance().getDocument(myFile);
    assertNotNull(myDocument);
    ApplicationManager.getApplication().runWriteAction(() -> {
      assert myTracker == null;
      myTracker = LineStatusTracker.createOn(myFile, myDocument, getProject(), smart ? Mode.SMART : Mode.DEFAULT);
      myTracker.setBaseRevision(upToDateDocument);
    });
    myUpToDateDocument = myTracker.getVcsDocument();
  }

  protected void releaseTracker() {
    if (myTracker != null) {
      myTracker.release();
      myTracker = null;
    }
  }

  protected void checkCantTrim() {
    List<Range> ranges = myTracker.getRanges();
    for (Range range : ranges) {
      if (range.getType() != Range.MODIFIED) continue;

      List<String> lines1 = DiffUtil.getLines(myUpToDateDocument, range.getVcsLine1(), range.getVcsLine2());
      List<String> lines2 = DiffUtil.getLines(myDocument, range.getLine1(), range.getLine2());

      String f1 = ContainerUtil.getFirstItem(lines1);
      String f2 = ContainerUtil.getFirstItem(lines2);

      String l1 = ContainerUtil.getLastItem(lines1);
      String l2 = ContainerUtil.getLastItem(lines2);

      assertFalse(Comparing.equal(f1, f2));
      assertFalse(Comparing.equal(l1, l2));
    }
  }

  protected void checkCantMerge() {
    List<Range> ranges = myTracker.getRanges();
    for (int i = 0; i < ranges.size() - 1; i++) {
      assertFalse(ranges.get(i).getLine2() == ranges.get(i + 1).getLine1());
    }
  }

  protected void checkInnerRanges() {
    List<Range> ranges = myTracker.getRangesInner();

    for (Range range : ranges) {
      List<Range.InnerRange> innerRanges = range.getInnerRanges();
      if (innerRanges == null) return;

      int last = range.getLine1();
      for (Range.InnerRange innerRange : innerRanges) {
        assertEquals(innerRange.getLine1() == innerRange.getLine2(), innerRange.getType() == Range.DELETED);

        assertEquals(last, innerRange.getLine1());
        last = innerRange.getLine2();
      }
      assertEquals(last, range.getLine2());

      List<String> lines1 = DiffUtil.getLines(myUpToDateDocument, range.getVcsLine1(), range.getVcsLine2());
      List<String> lines2 = DiffUtil.getLines(myDocument, range.getLine1(), range.getLine2());

      int start = 0;
      for (Range.InnerRange innerRange : innerRanges) {
        if (innerRange.getType() != Range.EQUAL) continue;

        for (int i = innerRange.getLine1(); i < innerRange.getLine2(); i++) {
          String line = lines2.get(i - range.getLine1());
          List<String> searchSpace = lines1.subList(start, lines1.size());
          int index = ContainerUtil.<String>indexOf(searchSpace, (it) -> StringUtil.equalsIgnoreWhitespaces(it, line));
          assertTrue(index != -1);
          start += index + 1;
        }
      }
    }
  }
}
