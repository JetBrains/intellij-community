/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class EditorStressTest extends AbstractEditorTest {
  private static final int ITERATIONS = 10_000;
  private static final Long SEED_OVERRIDE = null; // set non-null value to run with a specific seed

  private static final List<? extends Action> ourActions = Arrays.asList(new AddText("a"),
                                                                         new AddText("\n"),
                                                                         new AddText("\t"),
                                                                         new AddText(HIGH_SURROGATE),
                                                                         new AddText(LOW_SURROGATE),
                                                                         new RemoveCharacter(),
                                                                         new MoveCharacter(),
                                                                         new AddFoldRegion(),
                                                                         new RemoveFoldRegion(),
                                                                         new CollapseFoldRegion(),
                                                                         new ExpandFoldRegion(),
                                                                         new ChangeBulkModeState(),
                                                                         new ChangeEditorVisibility(),
                                                                         new AddInlay(),
                                                                         new RemoveInlay(),
                                                                         new MoveCaret());

  private final Random myRandom = new Random() {{
    //noinspection ConstantConditions
    setSeed(mySeed = SEED_OVERRIDE == null ? nextLong() : SEED_OVERRIDE);
  }};
  private long mySeed;

  public void testRandomActions() {
    LOG.debug("Seed is " + mySeed);
    int i = 0;
    try {
      initText("");
      configureSoftWraps(10);
      EditorImpl editor = (EditorImpl)myEditor;
      for (i = 0; i < ITERATIONS; i++) {
        doRandomAction(editor);
        editor.validateState();
      }
      if (editor.getDocument().isInBulkUpdate()) editor.getDocument().setInBulkUpdate(false);
    }
    catch (Throwable t) {
      String message = "Failed when run with seed=" + mySeed + " in iteration " + i;
      System.err.println(message);
      throw new RuntimeException(message, t);
    }
  }

  private void doRandomAction(EditorEx editor) {
    ourActions.get(myRandom.nextInt(ourActions.size())).perform(editor, myRandom);
  }

  @FunctionalInterface
  interface Action {
    void perform(EditorEx editor, Random random);
  }

  private static class AddText implements Action {
    private final String myText;

    AddText(String text) {
      myText = text;
    }

    @Override
    public void perform(EditorEx editor, Random random) {
      Document document = editor.getDocument();
      int offset = random.nextInt(document.getTextLength() + 1);
      new WriteCommandAction.Simple(getProject()) {
        @Override
        protected void run() {
          document.insertString(offset, myText);
        }
      }.execute().throwException();
    }
  }

  private static class RemoveCharacter implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      if (textLength <= 0) return;
      int offset = random.nextInt(textLength);
      new WriteCommandAction.Simple(getProject()) {
        @Override
        protected void run() {
          document.deleteString(offset, offset + 1);
        }
      }.execute().throwException();
    }
  }

  private static class MoveCharacter implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      if (textLength <= 0) return;
      int offset = random.nextInt(textLength);
      int targetOffset = random.nextInt(textLength + 1);
      if (targetOffset < offset || targetOffset > offset + 1) {
        new WriteCommandAction.Simple(getProject()) {
          @Override
          protected void run() {
            ((DocumentEx)document).moveText(offset, offset + 1, targetOffset);
          }
        }.execute().throwException();
      }
    }
  }

  private static class AddFoldRegion implements Action {
    @Override
    public void perform(final EditorEx editor, Random random) {
      DocumentEx document = editor.getDocument();
      if (document.isInBulkUpdate()) return;
      int textLength = document.getTextLength();
      if (textLength <= 0) return;
      final int startOffset = random.nextInt(textLength + 1);
      final int endOffset = random.nextInt(textLength + 1);
      if (startOffset == endOffset) return;
      final FoldingModel foldingModel = editor.getFoldingModel();
      foldingModel.runBatchFoldingOperation(() -> foldingModel.addFoldRegion(Math.min(startOffset, endOffset),
                                                                             Math.max(startOffset, endOffset),
                                                                             "."));
    }
  }

  private static class RemoveFoldRegion implements Action {
    @Override
    public void perform(final EditorEx editor, Random random) {
      DocumentEx document = editor.getDocument();
      if (document.isInBulkUpdate()) return;
      final FoldingModel foldingModel = editor.getFoldingModel();
      FoldRegion[] foldRegions = foldingModel.getAllFoldRegions();
      if (foldRegions.length == 0) return;
      final FoldRegion region = foldRegions[random.nextInt(foldRegions.length)];
      foldingModel.runBatchFoldingOperation(() -> foldingModel.removeFoldRegion(region));
    }
  }

  private static class CollapseFoldRegion implements Action {
    @Override
    public void perform(final EditorEx editor, Random random) {
      DocumentEx document = editor.getDocument();
      if (document.isInBulkUpdate()) return;
      final FoldingModel foldingModel = editor.getFoldingModel();
      FoldRegion[] foldRegions = foldingModel.getAllFoldRegions();
      if (foldRegions.length == 0) return;
      final FoldRegion region = foldRegions[random.nextInt(foldRegions.length)];
      foldingModel.runBatchFoldingOperation(() -> region.setExpanded(false));
    }
  }

  private static class ExpandFoldRegion implements Action {
    @Override
    public void perform(final EditorEx editor, Random random) {
      DocumentEx document = editor.getDocument();
      if (document.isInBulkUpdate()) return;
      final FoldingModel foldingModel = editor.getFoldingModel();
      FoldRegion[] foldRegions = foldingModel.getAllFoldRegions();
      if (foldRegions.length == 0) return;
      final FoldRegion region = foldRegions[random.nextInt(foldRegions.length)];
      foldingModel.runBatchFoldingOperation(() -> region.setExpanded(true));
    }
  }
  
  private static class ChangeBulkModeState implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      DocumentEx document = editor.getDocument();
      document.setInBulkUpdate(!document.isInBulkUpdate());
    }
  }
  
  private static class ChangeEditorVisibility implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      DocumentEx document = editor.getDocument();
      if (document.isInBulkUpdate()) return;
      JViewport viewport = editor.getScrollPane().getViewport();
      viewport.setExtentSize(viewport.getExtentSize().getWidth() == 0 ? new Dimension(1000, 1000) : new Dimension(0, 0));
    }
  }

  private static class AddInlay implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      addInlay(random.nextInt(editor.getDocument().getTextLength() + 1));
    }
  }

  private static class RemoveInlay implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      List<Inlay> inlays = myEditor.getInlayModel().getInlineElementsInRange(0, editor.getDocument().getTextLength());
      if (!inlays.isEmpty()) Disposer.dispose(inlays.get(random.nextInt(inlays.size())));
    }
  }

  private static class MoveCaret implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      DocumentEx document = editor.getDocument();
      if (document.isInBulkUpdate()) return;
      myEditor.getCaretModel().moveToOffset(random.nextInt(document.getTextLength() + 1));
    }
  }
}