// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class EditorStressTest extends AbstractEditorTest {
  private static final int ITERATIONS = 10_000;
  private static final Long SEED_OVERRIDE = null; // set non-null value to run with a specific seed

  private final Random myRandom = new Random() {{
    //noinspection ConstantConditions
    setSeed(mySeed = SEED_OVERRIDE == null ? nextLong() : SEED_OVERRIDE);
  }};
  private long mySeed;

  private static final String CHARS_TO_USE = "a\r\n\t" + SURROGATE_PAIR;
  private static final int MAX_CHARS_TO_ADD = 10;
  private static final int MAX_CHARS_TO_REMOVE = 5;
  private static final int MIN_INLAY_WIDTH = 1;
  private static final int MAX_INLAY_WIDTH = 9;
  private static final int MAX_INLAY_OPERATIONS_IN_BATCH = 3;
  private static final int MAX_CUSTOM_FOLD_REGION_WIDTH = 50;

  private static final List<? extends Action> INLAY_PRIMITIVE_ACTIONS = Arrays.asList(
    new AddInlay(),
    new RemoveInlay(),
    new UpdateInlay()
  );
  private final List<? extends Action> ACTIONS = ContainerUtil.concat(Arrays.asList(
    new AddText(),
    new RemoveText(),
    new MoveText(),
    new AddFoldRegion(),
    new AddCustomFoldRegion(),
    new UpdateCustomFoldRegion(),
    new RemoveFoldRegion(),
    new ExpandOrCollapseFoldRegions(),
    new ClearFoldRegions(),
    new ChangeBulkModeState(),
    new ChangeEditorVisibility(),
    new BatchInlayOperation(),
    new MoveCaret()
  ), INLAY_PRIMITIVE_ACTIONS);

  public void testRandomActions() {
    LOG.debug("Seed is " + mySeed);
    int i = 0;
    try {
      initText("");
      ((DocumentImpl)getEditor().getDocument()).setAcceptSlashR(true);
      configureSoftWraps(10);
      EditorImpl editor = (EditorImpl)getEditor();
      for (i = 0; i < ITERATIONS; i++) {
        doRandomAction(editor, myRandom, ACTIONS);
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

  private static void doRandomAction(EditorEx editor, Random random, List<? extends Action> actions) {
    actions.get(random.nextInt(actions.size())).perform(editor, random);
  }

  @FunctionalInterface
  interface Action {
    void perform(EditorEx editor, Random random);
  }

  private class AddText implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      StringBuilder text = new StringBuilder();
      int count = 1 + random.nextInt(MAX_CHARS_TO_ADD);
      for (int i = 0; i < count; i++) {
        text.append(CHARS_TO_USE.charAt(random.nextInt(CHARS_TO_USE.length())));
      }
      Document document = editor.getDocument();
      int offset = random.nextInt(document.getTextLength() + 1);
      WriteCommandAction.writeCommandAction(getProject()).run(() -> document.insertString(offset, text.toString()));
    }
  }

  private class RemoveText implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      Document document = editor.getDocument();
      int textLength = document.getTextLength();
      if (textLength <= 0) return;
      int count = 1 + random.nextInt(Math.min(MAX_CHARS_TO_REMOVE, textLength));
      int offset = random.nextInt(textLength - count + 1);
      WriteCommandAction.writeCommandAction(getProject()).run(() -> document.deleteString(offset, offset + count));
    }
  }

  private class MoveText implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      DocumentEx document = editor.getDocument();
      int textLength = document.getTextLength();
      if (textLength <= 1) return;
      int count = 1 + random.nextInt(textLength - 1);
      int srcStart = random.nextInt(textLength - count + 1);
      int targetPos = random.nextInt(textLength - count);
      int targetOffset = targetPos < srcStart ? targetPos : targetPos + count + 1;
      WriteCommandAction.writeCommandAction(getProject()).run(() -> document.moveText(srcStart, srcStart + count, targetOffset));
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
                                                                             random.nextBoolean() ? "." : ""));
    }
  }

  private static class AddCustomFoldRegion implements Action {
    @Override
    public void perform(final EditorEx editor, Random random) {
      DocumentEx document = editor.getDocument();
      if (document.isInBulkUpdate()) return;
      int lineCount = Math.max(1, document.getLineCount());
      final int startLine = random.nextInt(lineCount);
      final int endLine = random.nextInt(lineCount);
      final FoldingModel foldingModel = editor.getFoldingModel();
      foldingModel.runBatchFoldingOperation(() -> foldingModel.addCustomLinesFolding(Math.min(startLine, endLine),
                                                                                     Math.max(startLine, endLine),
                                                                                     MyCustomFoldRegionRenderer.INSTANCE));
    }
  }

  private static class UpdateCustomFoldRegion implements Action {
    @Override
    public void perform(final EditorEx editor, Random random) {
      if (editor.getDocument().isInBulkUpdate()) return;
      List<CustomFoldRegion> customRegions = ContainerUtil.findAll(editor.getFoldingModel().getAllFoldRegions(), CustomFoldRegion.class);
      int count = customRegions.size();
      if (count > 0) {
        CustomFoldRegion region = customRegions.get(random.nextInt(count));
        region.putUserData(REGION_WIDTH, random.nextInt(MAX_CUSTOM_FOLD_REGION_WIDTH));
        region.update();
      }
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

  private static class ExpandOrCollapseFoldRegions implements Action {
    @Override
    public void perform(final EditorEx editor, Random random) {
      DocumentEx document = editor.getDocument();
      if (document.isInBulkUpdate()) return;
      final FoldingModel foldingModel = editor.getFoldingModel();
      FoldRegion[] foldRegions = foldingModel.getAllFoldRegions();
      if (foldRegions.length == 0) return;
      int operations = random.nextInt(foldRegions.length) + 1;
      foldingModel.runBatchFoldingOperation(() -> {
        for (int i = 0; i < operations; i++) {
          FoldRegion region = foldRegions[random.nextInt(foldRegions.length)];
          region.setExpanded(!region.isExpanded());
        }
      });
    }
  }

  private static class ClearFoldRegions implements Action {
    @Override
    public void perform(final EditorEx editor, Random random) {
      DocumentEx document = editor.getDocument();
      if (document.isInBulkUpdate()) return;
      FoldingModelEx foldingModel = editor.getFoldingModel();
      foldingModel.runBatchFoldingOperation(foldingModel::clearFoldRegions);
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
      int offset = random.nextInt(editor.getDocument().getTextLength() + 1);
      editor.getInlayModel().addInlineElement(offset, false, new MyInlayRenderer());
    }
  }

  private static class RemoveInlay implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      List<Inlay<?>> inlays = editor.getInlayModel().getInlineElementsInRange(0, editor.getDocument().getTextLength());
      if (!inlays.isEmpty()) {
        Disposer.dispose(inlays.get(random.nextInt(inlays.size())));
      }
    }
  }

  private static final class UpdateInlay implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      List<Inlay<? extends MyInlayRenderer>> inlays =
        editor.getInlayModel().getInlineElementsInRange(0, editor.getDocument().getTextLength(), MyInlayRenderer.class);
      if (!inlays.isEmpty()) {
        Inlay<? extends MyInlayRenderer> inlay = inlays.get(random.nextInt(inlays.size()));
        inlay.getRenderer().width = MIN_INLAY_WIDTH + random.nextInt(MAX_INLAY_WIDTH - MIN_INLAY_WIDTH + 1);
        inlay.update();
      }
    }
  }

  private static class BatchInlayOperation implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      editor.getInlayModel().execute(true, () -> {
        int count = 1 + random.nextInt(MAX_INLAY_OPERATIONS_IN_BATCH);
        for (int i = 0; i < count; i++) {
          doRandomAction(editor, random, INLAY_PRIMITIVE_ACTIONS);
        }
      });
    }
  }

  private static class MoveCaret implements Action {
    @Override
    public void perform(EditorEx editor, Random random) {
      DocumentEx document = editor.getDocument();
      if (document.isInBulkUpdate()) return;
      editor.getCaretModel().moveToOffset(random.nextInt(document.getTextLength() + 1));
    }
  }

  private static class MyInlayRenderer implements EditorCustomElementRenderer {
    int width = 1;

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) { return width; }
  }

  private static final Key<Integer> REGION_WIDTH = Key.create("custom.region.width");

  private static final class MyCustomFoldRegionRenderer implements CustomFoldRegionRenderer {
    private static final MyCustomFoldRegionRenderer INSTANCE = new MyCustomFoldRegionRenderer();

    @Override
    public int calcWidthInPixels(@NotNull CustomFoldRegion region) {
      Integer value = region.getUserData(REGION_WIDTH);
      return value == null ? 0 : value;
    }

    @Override
    public int calcHeightInPixels(@NotNull CustomFoldRegion region) {
      return 1;
    }

    @Override
    public void paint(@NotNull CustomFoldRegion region,
                      @NotNull Graphics2D g,
                      @NotNull Rectangle2D targetRegion,
                      @NotNull TextAttributes textAttributes) {}
  }
}