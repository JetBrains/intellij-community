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
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * This class stores the data annotations for coverage information in the editor.
 */
@Service(Service.Level.PROJECT)
public final class CoverageDataAnnotationsManager implements Disposable {
  private final Project myProject;
  private final Object myAnnotationsLock = new Object();
  private final ExecutorService myExecutor;
  private final Map<Editor, CoverageEditorAnnotator> myAnnotators = new HashMap<>();
  private final Map<Editor, Future<?>> myRequests = new ConcurrentHashMap<>();

  public CoverageDataAnnotationsManager(Project project) {
    myProject = project;
    myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService("CoverageDataAnnotationsManager Pool", 1);
  }

  public static CoverageDataAnnotationsManager getInstance(@NotNull Project project) {
    return project.getService(CoverageDataAnnotationsManager.class);
  }

  @Override
  public void dispose() {
    clearAnnotations();
  }

  public synchronized void clearAnnotations() {
    for (var it = myRequests.entrySet().iterator(); it.hasNext(); ) {
      it.next().getValue().cancel(true);
      it.remove();
    }
    myExecutor.execute(() -> {
      synchronized (myAnnotationsLock) {
        for (CoverageEditorAnnotator annotator : myAnnotators.values()) {
          Disposer.dispose(annotator);
        }
        myAnnotators.clear();
      }
    });
  }

  public synchronized void update() {
    if (CoverageDataManager.getInstance(myProject).activeSuites().isEmpty()) return;
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    List<VirtualFile> openFiles = fileEditorManager.getOpenFilesWithRemotes();
    for (VirtualFile openFile : openFiles) {
      FileEditor[] allEditors = fileEditorManager.getAllEditors(openFile);

      PsiFile psiFile = ReadAction.compute(() -> openFile.isValid() ? PsiManager.getInstance(myProject).findFile(openFile) : null);
      if (psiFile == null || !psiFile.isPhysical()) return;

      for (FileEditor fileEditor : allEditors) {
        if (fileEditor instanceof TextEditor textEditor) {
          Editor editor = textEditor.getEditor();
          runTask(editor, () -> show(editor, psiFile));
        }
      }
    }
  }

  private synchronized void runTask(@NotNull Editor editor, Runnable task) {
    Future<?> future = myExecutor.submit(() -> {
      myRequests.remove(editor);
      task.run();
    });
    myRequests.put(editor, future);
  }

  @NotNull
  private CoverageEditorAnnotator getOrCreateAnnotator(Editor editor, PsiFile file, CoverageEngine engine) {
    synchronized (myAnnotationsLock) {
      return myAnnotators.computeIfAbsent(editor, (x) -> engine.createSrcFileAnnotator(file, editor));
    }
  }

  private void clearEditor(Editor editor) {
    CoverageEditorAnnotator annotator;
    synchronized (myAnnotationsLock) {
      annotator = myAnnotators.remove(editor);
    }
    if (annotator != null) {
      Disposer.dispose(annotator);
    }
  }

  private void show(Editor editor, PsiFile psiFile) {
    for (CoverageSuitesBundle bundle : CoverageDataManager.getInstance(myProject).activeSuites()) {
      CoverageEngine engine = bundle.getCoverageEngine();
      if (!engine.coverageEditorHighlightingApplicableTo(psiFile)) return;
      if (!engine.acceptedByFilters(psiFile, bundle)) return;
      if (engine.isInLibraryClasses(editor.getProject(), psiFile.getVirtualFile())) return;

      CoverageEditorAnnotator annotator = getOrCreateAnnotator(editor, psiFile, engine);
      annotator.showCoverage(bundle);
    }
  }

  /**
   * Returns a Future that ensures that all requests in the coverage data annotations manager are completed.
   */
  @TestOnly
  @NotNull
  public Future<?> getAllRequestsCompletion() {
    return myExecutor.submit(() -> {
    });
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
      manager.runTask(editor, () -> manager.show(editor, psiFile));
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
      Editor editor = event.getEditor();
      Project project = editor.getProject();
      if (project == null) return;
      CoverageDataAnnotationsManager manager = project.getServiceIfCreated(CoverageDataAnnotationsManager.class);
      if (manager == null) return;

      Future<?> request = manager.myRequests.remove(editor);
      if (request != null) {
        request.cancel(true);
      }

      manager.myExecutor.execute(() -> manager.clearEditor(editor));
    }
  }
}
