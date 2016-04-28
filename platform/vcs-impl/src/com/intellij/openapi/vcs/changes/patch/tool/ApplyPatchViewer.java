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
package com.intellij.openapi.vcs.changes.patch.tool;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.fragmented.LineNumberConvertor;
import com.intellij.diff.tools.simple.SimpleDiffViewer;
import com.intellij.diff.tools.util.DiffSplitter;
import com.intellij.diff.tools.util.StatusPanel;
import com.intellij.diff.util.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class ApplyPatchViewer implements Disposable {
  @NotNull private final ApplyPatchDiffRequest myPatchRequest;

  @Nullable private final Project myProject;
  @NotNull private final Document myResultDocument;

  @NotNull private final MySimpleDiffViewer myViewer;

  @NotNull private final DiffSplitter myPatchSplitter;
  @NotNull private final EditorEx myPatchEditor;
  @NotNull private final EditorEx myResultEditor;

  @NotNull private final StatusPanel myStatusPanel;

  @NotNull private final List<ApplyPatchChange> myPatchChanges = new ArrayList<>();

  public ApplyPatchViewer(@NotNull DiffContext context,
                          @NotNull ApplyPatchDiffRequest patchRequest,
                          @Nullable Document resultDocument) {
    myPatchRequest = patchRequest;

    myProject = context.getProject();
    myResultDocument = resultDocument != null ? resultDocument : new DocumentImpl("");

    VirtualFile file = patchRequest.getHighlightFile();
    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
    DocumentContent localContent = contentFactory.create(myPatchRequest.getLocalContent(), file);
    DocumentContent mergedContent = contentFactory.create(myProject, myResultDocument, file);

    SimpleDiffRequest diffRequest = new SimpleDiffRequest(myPatchRequest.getTitle(), localContent, mergedContent,
                                                          myPatchRequest.getLocalTitle(), myPatchRequest.getResultTitle());
    if (resultDocument == null) diffRequest.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);

    myViewer = new MySimpleDiffViewer(context, diffRequest);
    myResultEditor = myViewer.getEditor2();

    myPatchEditor = DiffUtil.createEditor(new DocumentImpl("", true), myProject, true, true);
    DiffUtil.setEditorHighlighter(myProject, myPatchEditor, mergedContent);
    DiffUtil.disableBlitting(myPatchEditor);
    myPatchEditor.getGutterComponentEx().setForceShowRightFreePaintersArea(true);

    JPanel patchPanel = new JPanel(new BorderLayout(0, DiffUtil.TITLE_GAP));
    patchPanel.add(myPatchEditor.getComponent(), BorderLayout.CENTER);
    patchPanel.add(myViewer.getPatchTitle(), BorderLayout.NORTH);


    myPatchSplitter = new DiffSplitter();
    myPatchSplitter.setProportion(2f / 3);
    myPatchSplitter.setFirstComponent(myViewer.getComponent());
    myPatchSplitter.setSecondComponent(patchPanel);
    myPatchSplitter.setHonorComponentsMinimumSize(false);

    myStatusPanel = new MyStatusPanel();
  }

  @NotNull
  public JComponent getComponent() {
    return myPatchSplitter;
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myViewer.getPreferredFocusedComponent();
  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myPatchEditor);
    Disposer.dispose(myViewer);
  }

  @NotNull
  public StatusPanel getStatusPanel() {
    return myStatusPanel;
  }

  @NotNull
  public MySimpleDiffViewer getViewer() {
    return myViewer;
  }

  @NotNull
  public EditorEx getResultEditor() {
    return myResultEditor;
  }

  @NotNull
  public EditorEx getPatchEditor() {
    return myPatchEditor;
  }

  @NotNull
  public FrameDiffTool.ToolbarComponents doInit() {
    Document patchDocument = myPatchEditor.getDocument();


    PatchChangeBuilder builder = new PatchChangeBuilder();
    builder.exec(myPatchRequest.getPatch().getHunks(), myPatchRequest.getLocalContent());


    DiffUtil.executeWriteCommand(myResultDocument, myProject, "Init merge content", () -> {
      myResultDocument.setText(builder.getPatchApplyResult());

      UndoManager undoManager = myProject != null ? UndoManager.getInstance(myProject) : UndoManager.getGlobalInstance();
      if (undoManager != null) {
        DocumentReference ref = DocumentReferenceManager.getInstance().create(myResultDocument);
        undoManager.nonundoableActionPerformed(ref, false);
      }
    });

    patchDocument.setText(builder.getPatchContent());


    LineNumberConvertor convertor = builder.getLineConvertor();
    myPatchEditor.getGutterComponentEx().setLineNumberConvertor(convertor.createConvertor1(), convertor.createConvertor2());


    TIntArrayList lines = builder.getSeparatorLines();
    for (int i = 0; i < lines.size(); i++) {
      int offset = patchDocument.getLineStartOffset(lines.get(i));
      DiffDrawUtil.createLineSeparatorHighlighter(myPatchEditor, offset, offset, BooleanGetter.TRUE);
    }


    for (PatchChangeBuilder.Hunk hunk : builder.getHunks()) {
      myPatchChanges.add(new ApplyPatchChange(this, hunk));
    }
    myStatusPanel.update();


    myPatchSplitter.setPainter(new MyDividerPainter());

    VisibleAreaListener areaListener = (e) -> myPatchSplitter.repaint();
    myResultEditor.getScrollingModel().addVisibleAreaListener(areaListener);
    myPatchEditor.getScrollingModel().addVisibleAreaListener(areaListener);

    myResultEditor.getDocument().addDocumentListener(new MyDocumentListener(), this);


    FrameDiffTool.ToolbarComponents init = myViewer.init();
    init.statusPanel = getStatusPanel();
    return init;
  }

  private class MyDocumentListener implements DocumentListener {
    private boolean myShouldRepaint;

    @Override
    public void beforeDocumentChange(DocumentEvent e) {
      myShouldRepaint = false;
      if (myPatchChanges.isEmpty()) return;

      LineRange lineRange = DiffUtil.getAffectedLineRange(e);
      int shift = DiffUtil.countLinesShift(e);

      for (ApplyPatchChange change : myPatchChanges) {
        myShouldRepaint |= change.processChange(lineRange.start, lineRange.end, shift);
      }
    }

    @Override
    public void documentChanged(DocumentEvent e) {
      if (myShouldRepaint) {
        myShouldRepaint = false;

        myPatchSplitter.repaintDivider();
        for (ApplyPatchChange change : myPatchChanges) {
          change.reinstallHighlighters();
        }
      }
    }
  }

  private class MyDividerPainter implements DiffSplitter.Painter, DiffDividerDrawUtil.DividerPaintable {
    @Override
    public void paint(@NotNull Graphics g, @NotNull JComponent divider) {
      Graphics2D gg = DiffDividerDrawUtil.getDividerGraphics(g, divider, myPatchEditor.getComponent());

      gg.setColor(DiffDrawUtil.getDividerColor(myPatchEditor));
      gg.fill(gg.getClipBounds());

      DiffDividerDrawUtil.paintPolygons(gg, divider.getWidth(), myResultEditor, myPatchEditor, this);

      gg.dispose();
    }

    @Override
    public void process(@NotNull Handler handler) {
      for (ApplyPatchChange change : myPatchChanges) {
        LineRange appliedTo = change.getAppliedTo();
        if (appliedTo == null) continue;

        int patchLine1 = change.getPatchDeletionRange().start;
        int patchLine2 = change.getPatchInsertionRange().end;

        Color color = TextDiffType.MODIFIED.getColor(myPatchEditor);

        // do not abort - ranges are ordered in patch order, but they can be not ordered in terms of appliedTo
        handler.process(appliedTo.start, appliedTo.end, patchLine1, patchLine2, color);
      }
    }
  }

  private class MyStatusPanel extends StatusPanel {
    @Nullable
    @Override
    protected String getMessage() {
      int total = myPatchChanges.size();
      int alreadyApplied = 0;
      int notApplied = 0;
      for (ApplyPatchChange change : myPatchChanges) {
        switch (change.getStatus()) {
          case ALREADY_APPLIED:
            alreadyApplied++;
            break;
          case NOT_APPLIED:
            notApplied++;
            break;
          case EXACTLY_APPLIED:
            break;
        }
      }

      if (total == notApplied) {
        return DiffBundle.message("apply.somehow.status.message.cant.apply", notApplied);
      }
      else {
        String message = DiffBundle.message("apply.somehow.status.message.cant.apply.some", notApplied, total);
        if (alreadyApplied == 0) return message;
        return message + ". " + DiffBundle.message("apply.somehow.status.message.already.applied", alreadyApplied);
      }
    }
  }

  class MySimpleDiffViewer extends SimpleDiffViewer {
    private JComponent myPatchTitleComponent;

    public MySimpleDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
      super(context, request);
    }

    @NotNull
    @Override
    protected List<JComponent> createTitles() {
      List<JComponent> requestTitles = DiffUtil.createTextTitles(myRequest, getEditors());
      JComponent patchTitleLabel = DiffUtil.createTitle(myPatchRequest.getPatchTitle(), null, null, false);
      assert requestTitles.size() == 2;

      List<JComponent> titles = ContainerUtil.append(requestTitles, patchTitleLabel);
      List<JComponent> syncTitles = DiffUtil.createSyncHeightComponents(titles);

      JComponent title1 = syncTitles.get(0);
      JComponent title2 = syncTitles.get(1);
      myPatchTitleComponent = syncTitles.get(2);
      return ContainerUtil.list(title1, title2);
    }

    public JComponent getPatchTitle() {
      return myPatchTitleComponent;
    }
  }
}
