// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.plugins.journey.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.graph.view.NodeRealizer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.EventListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages creation and disposing of editors and jcomponents of journey.
 * We use editors for diagram node rendering, which requires us to correctly create, manage and dispose editors.
 */
public final class JourneyEditorManager implements Disposable {

  public interface GenericCallback<T> extends Consumer<T>, EventListener {
  }

  public final EventDispatcher<GenericCallback<Editor>> closeEditor =
    (EventDispatcher<GenericCallback<Editor>>)(EventDispatcher<?>)EventDispatcher.create(GenericCallback.class);

  public void closeNode(PsiElement psiElement) {
    Editor editor = OPENED_JOURNEY_EDITORS.get(psiElement);
    if (editor != null && editor.getProject() != null && editor.getVirtualFile() != null) {
      FileEditorManager.getInstance(editor.getProject()).closeFile(editor.getVirtualFile());
    }
    OPENED_JOURNEY_EDITORS.remove(psiElement);
    if (editor != null) closeEditor.getMulticaster().accept(editor);
  }

  public final Map<PsiElement, Editor> OPENED_JOURNEY_EDITORS = new ConcurrentHashMap<>();

  public static void foldOnlyRange(Editor editor, PsiElement psiElement, boolean isExpanded) {
    FoldingModel foldingModel = editor.getFoldingModel();
    int documentLength = editor.getDocument().getTextLength();

    TextRange range = psiElement.getTextRange();
    int lineStartOffset = editor.getDocument().getLineStartOffset(editor.getDocument().getLineNumber(range.getStartOffset()));
    int lineEndOffset = editor.getDocument().getLineEndOffset(editor.getDocument().getLineNumber(range.getEndOffset()));

    // Run folding operations within a batch to ensure atomic updates
    foldingModel.runBatchFoldingOperation(() -> {
      // Create a fold region before the visible range
      if (lineStartOffset > 0) {
        FoldRegion beforeRegion = foldingModel.getFoldRegion(0, lineStartOffset);
        if (beforeRegion == null) {
          beforeRegion = foldingModel.addFoldRegion(0, lineStartOffset, "");
        }
        beforeRegion.setExpanded(isExpanded);
      }

      // Create a fold region after the visible range
      if (lineEndOffset < documentLength) {
        FoldRegion afterRegion = foldingModel.getFoldRegion(lineEndOffset, documentLength);
        if (afterRegion == null) {
          afterRegion = foldingModel.addFoldRegion(lineEndOffset, documentLength, "");
        }
        afterRegion.setExpanded(isExpanded);
      }
    });
  }

  public static final float BASE_FONT_SIZE = 13.0f;
  public static final int BASE_WIDTH = 800;
  public static final int TITLE_OFFSET = 60;

  public static void updateEditorSize(Editor editor, NodeRealizer realizer, PsiElement psiElement, float zoom) {
    float fontSize = BASE_FONT_SIZE * zoom;
    editor.getColorsScheme().setEditorFontSize(fontSize);
    editor.getCaretModel().moveToOffset(psiElement.getTextRange().getStartOffset());

    TextRange range = psiElement.getTextRange();
    int lineStartOffset = editor.getDocument().getLineStartOffset(editor.getDocument().getLineNumber(range.getStartOffset()));
    int lineEndOffset = editor.getDocument().getLineEndOffset(editor.getDocument().getLineNumber(range.getEndOffset()));

    Point p1 = editor.offsetToXY(lineStartOffset);
    Point p2 = editor.offsetToXY(lineEndOffset);
    // TODO change const offset
    int h = (int)((p2.y - p1.y + TITLE_OFFSET) / zoom);
    if (realizer != null) {
      realizer.setHeight(h);
    } else {
      editor.getComponent().setSize(new Dimension(BASE_WIDTH, h));
    }
  }

  @RequiresEdt
  public Editor openPsiElementInEditor(PsiElement psiElement, float zoom) {
    PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    Editor editor = JourneyEditorFactory.createEditor(psiElement.getProject(), psiFile.getFileDocument(),
                                                      psiFile.getViewProvider().getVirtualFile());
    registerEditor(psiElement, editor);
    AsyncEditorLoader.Companion.performWhenLoaded(editor, () -> {
      editor.getCaretModel().moveToOffset(psiElement.getTextRange().getStartOffset());
      foldOnlyRange(editor, psiElement, false);
    });

    updateEditorSize(editor, null, psiElement, zoom);
    return editor;
  }

  private void registerEditor(PsiElement psiElement, Editor editor) {
    OPENED_JOURNEY_EDITORS.put(psiElement, editor);
    EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        if (event.getEditor() == editor) {
          closeNode(psiElement);
        }
      }
    }, this);
  }

  @Override
  public void dispose() {
    OPENED_JOURNEY_EDITORS.values().forEach(it -> {
      try {
        EditorFactory.getInstance().releaseEditor(it);
        if (it.getProject() != null && it.getVirtualFile() != null) {
          FileEditorManager.getInstance(it.getProject()).closeFile(it.getVirtualFile());
        }
      } catch (Throwable e) {
        e.printStackTrace();
      }
    });
    OPENED_JOURNEY_EDITORS.clear();
  }
}
