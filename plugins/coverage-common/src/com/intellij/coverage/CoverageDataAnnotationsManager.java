// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class stores the data annotations for coverage information in the editor.
 */
@Service(Service.Level.PROJECT)
public final class CoverageDataAnnotationsManager implements Disposable {
  private final Project myProject;
  private final Object myAnnotationsLock = new Object();
  private final Map<Editor, CoverageEditorAnnotator> myAnnotators = new HashMap<>();
  private final Map<Editor, Runnable> myRequests = new ConcurrentHashMap<>();

  private Alarm myRequestsAlarm;

  public CoverageDataAnnotationsManager(Project project) {
    myProject = project;
  }

  public static CoverageDataAnnotationsManager getInstance(@NotNull Project project) {
    return project.getService(CoverageDataAnnotationsManager.class);
  }


  public void clearAnnotations() {
    disposeAnnotators();
  }

  public void update() {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    List<VirtualFile> openFiles = fileEditorManager.getOpenFilesWithRemotes();
    for (VirtualFile openFile : openFiles) {
      FileEditor[] allEditors = fileEditorManager.getAllEditors(openFile);

      PsiFile psiFile = ReadAction.compute(() -> openFile.isValid() ? PsiManager.getInstance(myProject).findFile(openFile) : null);
      if (psiFile == null || !psiFile.isPhysical()) return;

      for (FileEditor fileEditor : allEditors) {
        if (fileEditor instanceof TextEditor textEditor) {
          Editor editor = textEditor.getEditor();
          callShowLater(editor, psiFile);
        }
      }
    }
  }

  @NotNull
  private synchronized Alarm getRequestsAlarm() {
    if (myRequestsAlarm == null) {
      myRequestsAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    }
    return myRequestsAlarm;
  }

  private void callShowLater(@NotNull Editor editor, PsiFile psiFile) {
    Runnable task = () -> show(editor, psiFile);
    myRequests.put(editor, task);
    getRequestsAlarm().addRequest(task, 0);
  }

  @NotNull
  private CoverageEditorAnnotator getOrCreateAnnotator(Editor editor, PsiFile file, CoverageEngine engine) {
    synchronized (myAnnotationsLock) {
      return myAnnotators.computeIfAbsent(editor, (x) -> engine.createSrcFileAnnotator(file, editor));
    }
  }

  private void clearEditor(Editor editor) {
    myRequests.remove(editor);
    CoverageEditorAnnotator annotator;
    synchronized (myAnnotationsLock) {
      annotator = myAnnotators.remove(editor);
    }
    if (annotator != null) {
      Disposer.dispose(annotator);
    }
  }

  private void disposeAnnotators() {
    synchronized (myAnnotationsLock) {
      for (var entry : myAnnotators.entrySet()) {
        myRequests.remove(entry.getKey());
        Disposer.dispose(entry.getValue());
      }
      myAnnotators.clear();
    }
  }

  private void show(Editor editor, PsiFile psiFile) {
    for (CoverageSuitesBundle bundle : CoverageDataManager.getInstance(myProject).activeSuites()) {
      CoverageEngine engine = bundle.getCoverageEngine();
      if (!engine.coverageEditorHighlightingApplicableTo(psiFile)) return;
      if (!engine.acceptedByFilters(psiFile, bundle)) return;

      CoverageEditorAnnotator annotator = getOrCreateAnnotator(editor, psiFile, engine);
      annotator.showCoverage(bundle);
    }
  }

  @Override
  public void dispose() {
    disposeAnnotators();
  }


  public static class CoverageEditorFactoryListener implements EditorFactoryListener {
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
      Editor editor = event.getEditor();
      Project project = editor.getProject();
      if (project == null) return;
      if (CoverageDataManager.getInstance(project).activeSuites().isEmpty()) return;
      CoverageDataAnnotationsManager manager = project.getServiceIfCreated(CoverageDataAnnotationsManager.class);
      if (manager == null) return;

      PsiFile psiFile = ReadAction.compute(() -> PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()));
      if (psiFile == null || !psiFile.isPhysical()) return;
      manager.callShowLater(editor, psiFile);
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
      Editor editor = event.getEditor();
      Project project = editor.getProject();
      if (project == null) return;
      CoverageDataAnnotationsManager manager = project.getServiceIfCreated(CoverageDataAnnotationsManager.class);
      if (manager == null) return;

      Runnable request = manager.myRequests.remove(editor);
      if (request != null) {
        manager.getRequestsAlarm().cancelRequest(request);
      }

      manager.getRequestsAlarm().addRequest(() -> manager.clearEditor(editor), 0);
    }
  }
}
