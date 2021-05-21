// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.PrevNextDifferenceIterableBase;
import com.intellij.diff.tools.util.SimpleDiffPanel;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineRange;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.LineNumberConverterAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.components.panels.Wrapper;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

final class PatchDiffTool implements FrameDiffTool {
  @NotNull
  @Override
  public String getName() {
    return VcsBundle.message("patch.content.viewer.name");
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return request instanceof PatchDiffRequest;
  }

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return new MyPatchViewer(context, (PatchDiffRequest)request);
  }

  private static class MyPatchViewer implements DiffViewer, DataProvider {


    private final Project myProject;
    private final SimpleDiffPanel myPanel;
    private final EditorEx myEditor;
    private final DiffContext myContext;
    private final PatchDiffRequest myRequest;
    private final MyPrevNextDifferenceIterable myPrevNextDifferenceIterable;
    private final List<PatchChangeBuilder.Hunk> myHunks = new ArrayList<>();

    MyPatchViewer(DiffContext context, PatchDiffRequest request) {
      myProject = context.getProject();
      myContext = context;
      myRequest = request;
      Document document = EditorFactory.getInstance().createDocument("");
      myEditor = DiffUtil.createEditor(document, myProject, true, true);
      myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();

      Wrapper editorPanel = new Wrapper(new BorderLayout(0, DiffUtil.TITLE_GAP), myEditor.getComponent());
      String panelTitle = request.getPanelTitle();
      if (panelTitle != null) {
        editorPanel.add(DiffUtil.createTitle(panelTitle), BorderLayout.NORTH);
      }
      myPanel = new SimpleDiffPanel(editorPanel, this, context);
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myPanel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myEditor.getContentComponent();
    }

    @NotNull
    @Override
    public ToolbarComponents init() {
      myPanel.setPersistentNotifications(DiffUtil.createCustomNotifications(this, myContext, myRequest));
      onInit();
      return new FrameDiffTool.ToolbarComponents();
    }

    @Override
    public void dispose() {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }

    private void onInit() {
      PatchChangeBuilder builder = new PatchChangeBuilder();
      builder.exec(myRequest.getPatch().getHunks());
      myHunks.addAll(builder.getHunks());

      Document patchDocument = myEditor.getDocument();
      WriteAction.run(() -> patchDocument.setText(builder.getPatchContent().toString()));

      myEditor.getGutter().setLineNumberConverter(
        new LineNumberConverterAdapter(builder.getLineConvertor1().createConvertor()),
        new LineNumberConverterAdapter(builder.getLineConvertor2().createConvertor())
      );

      for (IntListIterator iterator = builder.getSeparatorLines().iterator(); iterator.hasNext(); ) {
        int offset = patchDocument.getLineStartOffset(iterator.nextInt());
        DiffDrawUtil.createLineSeparatorHighlighter(myEditor, offset, offset, BooleanGetter.TRUE);
      }

      // highlighting
      for (PatchChangeBuilder.Hunk hunk : myHunks) {
        List<DiffFragment> innerFragments = PatchChangeBuilder.computeInnerDifferences(patchDocument, hunk);
        DiffDrawUtil.createUnifiedChunkHighlighters(myEditor, hunk.getPatchDeletionRange(), hunk.getPatchInsertionRange(), innerFragments);
      }

      myEditor.getGutterComponentEx().revalidateMarkup();
    }

    @Nullable
    @Override
    public Object getData(@NotNull @NonNls String dataId) {
      if (CommonDataKeys.PROJECT.is(dataId)) return myProject;
      if (DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.is(dataId)) return myPrevNextDifferenceIterable;
      if (DiffDataKeys.CURRENT_EDITOR.is(dataId)) return myEditor;
      if (DiffDataKeys.CURRENT_CHANGE_RANGE.is(dataId)) {
        return myPrevNextDifferenceIterable.getHunkRangeByLine(myEditor.getCaretModel().getLogicalPosition().line);
      }
      return null;
    }

    private class MyPrevNextDifferenceIterable extends PrevNextDifferenceIterableBase<PatchChangeBuilder.Hunk> {
      @NotNull
      @Override
      protected List<PatchChangeBuilder.Hunk> getChanges() {
        return myHunks;
      }

      @NotNull
      @Override
      protected EditorEx getEditor() {
        return myEditor;
      }

      @Override
      protected int getStartLine(@NotNull PatchChangeBuilder.Hunk change) {
        return change.getPatchDeletionRange().start;
      }

      @Override
      protected int getEndLine(@NotNull PatchChangeBuilder.Hunk change) {
        return change.getPatchInsertionRange().end;
      }

      @Nullable
      LineRange getHunkRangeByLine(int line) {
        for (PatchChangeBuilder.Hunk hunk : getChanges()) {
          int start = hunk.getPatchDeletionRange().start;
          int end = hunk.getPatchInsertionRange().end;
          if (start <= line && end > line) {
            return new LineRange(start, end);
          }
          if (start > line) return null;
        }
        return null;
      }
    }
  }
}
