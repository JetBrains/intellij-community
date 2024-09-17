// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.journey;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JourneyManager {
  public static boolean IS_PENDING = false;

  public static final Map<PsiElement, Editor> OPENED_JOURNEY_EDITORS = new ConcurrentHashMap<>();
  public static final Map<PsiElement, JComponent> OPENED_JOURNEY_COMPONENTS = new ConcurrentHashMap<>();

  public static JComponent getOpenedJourneyComponent(PsiElement psiKey) {
    return OPENED_JOURNEY_COMPONENTS.get(psiKey);
  }

  public static JComponent openPsiElementInEditor(PsiElement psiElement) {
    PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    Project project = psiElement.getProject();
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    int startOffset = psiElement.getTextRange().getStartOffset();
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, startOffset);
    descriptor = descriptor.setUseCurrentWindow(false);
    Editor editor;
    if (IS_PENDING) {
      System.out.println("ERROR PENDING WHILE PENDING");
    }
    IS_PENDING = true;
    try {
      editor = fileEditorManager.openTextEditor(descriptor, false);
    } finally {
      IS_PENDING = false;
    }
    AsyncEditorLoader.Companion.performWhenLoaded(editor, () -> {
      LogicalPosition position = editor.offsetToLogicalPosition(startOffset);
      position = new LogicalPosition(position.line, 0);
      editor.getCaretModel().moveToOffset(startOffset);
      editor.getScrollingModel().scrollTo(position, ScrollType.CENTER);
    });
    ((EditorEx) editor).setBackgroundColor(com.intellij.ui.Gray._249);
    JComponent component = editor.getComponent();
    OPENED_JOURNEY_COMPONENTS.put(psiElement, component);
    OPENED_JOURNEY_EDITORS.put(psiElement, editor);
    TextRange range = psiElement.getTextRange();
    Point p1 = editor.offsetToXY(range.getStartOffset());
    Point p2 = editor.offsetToXY(range.getEndOffset());
    int h = p2.y - p1.y;
    h = Math.min(h, 650);
    h = Math.max(h, 250);
    component.setSize(650, h);
    component.setPreferredSize(new Dimension(650, h));
    return component;
  }

  public static void dispose() {
    OPENED_JOURNEY_EDITORS.values().forEach(it -> {
      try {
        if (it instanceof Disposable d) Disposer.dispose(d);
        EditorFactory.getInstance().releaseEditor(it);
        if (it.getProject() != null ) FileEditorManager.getInstance(it.getProject()).closeFile(it.getVirtualFile());
      } catch (Throwable e) {
        e.printStackTrace();
      }
    });
    OPENED_JOURNEY_EDITORS.clear();
    OPENED_JOURNEY_COMPONENTS.clear();
  }
}
