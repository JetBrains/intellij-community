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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.ui.components.panels.Wrapper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PatchDiffTool implements FrameDiffTool {
  @NotNull
  @Override
  public String getName() {
    return "Patch content viewer";
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

    public MyPatchViewer(DiffContext context, PatchDiffRequest request) {
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
      myPanel.setPersistentNotifications(DiffUtil.getCustomNotifications(myContext, myRequest));
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

      myEditor.getGutterComponentEx()
        .setLineNumberConvertor(builder.getLineConvertor1().createConvertor(), builder.getLineConvertor2().createConvertor());

      for (int line : builder.getSeparatorLines().toNativeArray()) {
        int offset = patchDocument.getLineStartOffset(line);
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
    public Object getData(@NonNls String dataId) {
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
