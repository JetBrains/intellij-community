// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.plugins.journey.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
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
    Editor editor = OPENED_JOURNEY_EDITORS.get(psiElement.getContainingFile());
    if (editor != null && editor.getProject() != null && editor.getVirtualFile() != null) {
      FileEditorManager.getInstance(editor.getProject()).closeFile(editor.getVirtualFile());
    }
    OPENED_JOURNEY_EDITORS.remove(psiElement);
    NODE_PANELS.remove(psiElement);
    if (editor != null) closeEditor.getMulticaster().accept(editor);
  }

  public final Map<PsiElement, Editor> OPENED_JOURNEY_EDITORS = new ConcurrentHashMap<>();
  public final Map<PsiElement, JourneyEditorWrapper> NODE_PANELS = new ConcurrentHashMap<>();

  public static final float BASE_FONT_SIZE = 13.0f;
  public static final int BASE_WIDTH = 800;
  public static final int TITLE_OFFSET = 100;

  public static void updateEditorSize(Editor editor, PsiElement psiElement, float zoom, boolean isResize) {
    if (isResize) {
      editor.getColorsScheme().setEditorFontSize(BASE_FONT_SIZE);
      TextRange range = psiElement.getTextRange();
      int lineStartOffset = editor.getDocument().getLineStartOffset(editor.getDocument().getLineNumber(range.getStartOffset()));
      int lineEndOffset = editor.getDocument().getLineEndOffset(editor.getDocument().getLineNumber(range.getEndOffset()));

      Point p1 = editor.offsetToXY(lineStartOffset);
      Point p2 = editor.offsetToXY(lineEndOffset);
      int h = (p2.y - p1.y + TITLE_OFFSET);
      h = Math.min(800, h);
      editor.getComponent().setSize(new Dimension(BASE_WIDTH, h));
    }

    editor.getColorsScheme().setEditorFontSize(BASE_FONT_SIZE * zoom);
  }

  @RequiresEdt
  public Editor openEditor(PsiMember psiElement) {
    PsiFile psiFile = ReadAction.nonBlocking(() -> psiElement.getContainingFile()).executeSynchronously();
    if (psiFile == null) {
      return null;
    }
    Editor editor = JourneyEditorFactory.createEditor(psiFile.getProject(), psiFile.getFileDocument(),
                                                      psiFile.getViewProvider().getVirtualFile());
    registerEditor(psiFile, editor);

    return editor;
  }

  private void registerEditor(PsiFile psiFile, Editor editor) {
    OPENED_JOURNEY_EDITORS.put(psiFile, editor);
    EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
      @Override
      public void editorReleased(@NotNull EditorFactoryEvent event) {
        if (event.getEditor() == editor) {
          closeNode(psiFile);
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
    NODE_PANELS.clear();
  }
}
