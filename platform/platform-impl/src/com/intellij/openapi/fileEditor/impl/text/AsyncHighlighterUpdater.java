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
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.*;

/**
 * @author peter
 */
public class AsyncHighlighterUpdater extends ReadTask {
  private static final ExecutorService ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("AsyncEditorLoader pool", 2);
  private static final Map<Editor, Future<?>> ourHighlighterFutures = ContainerUtil.newConcurrentMap();
  private final Project myProject;
  private final Editor myEditor;
  private final VirtualFile myFile;

  private AsyncHighlighterUpdater(Project project, Editor editor, VirtualFile file) {
    myProject = project;
    myEditor = editor;
    myFile = file;
  }

  @Override
  public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
    if (!isEverythingValid()) return null;

    EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, myFile);
    highlighter.setText(myEditor.getDocument().getImmutableCharSequence());
    return new Continuation(() -> ((EditorEx)myEditor).setHighlighter(highlighter));
  }

  @Override
  public void onCanceled(@NotNull ProgressIndicator indicator) {
    updateHighlighters(myProject, myEditor, myFile);
  }

  private boolean isEverythingValid() {
    return !myProject.isDisposed() && !myEditor.isDisposed() && myFile.isValid();
  }

  public static void updateHighlighters(@NotNull Project project, @NotNull Editor editor, @NotNull VirtualFile file) {
    AsyncHighlighterUpdater task = new AsyncHighlighterUpdater(project, editor, file);
    if (task.isEverythingValid()) {
      CompletableFuture<?> future = ProgressIndicatorUtils.scheduleWithWriteActionPriority(ourExecutor, task);
      Future<?> prev = ourHighlighterFutures.put(editor, future);
      if (prev != null) {
        prev.cancel(false);
      }
      future.whenComplete((a, b) -> ourHighlighterFutures.remove(editor, future));
    }
  }

  @TestOnly
  public static void completeAsyncTasks() {
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    ApplicationManager.getApplication().invokeAndWait(() -> ourHighlighterFutures.values().forEach(AsyncHighlighterUpdater::waitForFuture));
    UIUtil.dispatchAllInvocationEvents();
  }

  @TestOnly
  private static void waitForFuture(Future<?> future) {
    int iteration = 0;
    while (!future.isDone() && iteration++ < 1000) {
      UIUtil.dispatchAllInvocationEvents();
      try {
        future.get(10, TimeUnit.MILLISECONDS);
        return;
      }
      catch (TimeoutException ignore) {
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    assert future.isDone() : "Too long async highlighter";
  }
}
