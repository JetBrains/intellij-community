// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.plugins.journey.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.journey.JourneyLeftovers;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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

  /**
   * Steal editor from other tab (old variant) or create custom editor component (correct variant)
   * Stolen editor is more feature-rich, used until feature parity with custom editor.
   */
  private static final boolean STEAL_EDITOR = false;

  public static interface GenericCallback<T> extends Consumer<T>, EventListener {
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

  public Editor getOpenedJourneyEditor(PsiElement psiKey) {
    return OPENED_JOURNEY_EDITORS.get(psiKey);
  }

  @RequiresEdt
  public Editor openPsiElementInEditor(PsiElement psiElement) {
    PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    int startOffset = psiElement.getTextRange().getStartOffset();
    Editor editor;
    if (JourneyLeftovers.IS_PENDING) {
      System.out.println("ERROR PENDING WHILE PENDING");
    }
    JourneyLeftovers.IS_PENDING = true;
    try {
      if (STEAL_EDITOR) {
        editor = JourneyEditorFactory.stealEditor(psiElement);
      }
      else {
        editor = JourneyEditorFactory.createEditor(psiElement.getProject(), psiFile.getFileDocument(), psiFile.getViewProvider().getVirtualFile());
      }
    } finally {
      JourneyLeftovers.IS_PENDING = false;
    }

    registerEditor(psiElement, editor);

    EditorUtils.scrollToOffset(editor, startOffset);
    ((EditorEx) editor).setBackgroundColor(com.intellij.ui.Gray._249);
    JComponent component = editor.getComponent();
    TextRange range = psiElement.getTextRange();
    Point p1 = editor.offsetToXY(range.getStartOffset());
    Point p2 = editor.offsetToXY(range.getEndOffset());
    int h = p2.y - p1.y;
    h = Math.min(h, 650);
    h = Math.max(h, 250);
    component.setSize(650, h);
    component.setPreferredSize(new Dimension(650, h));
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
