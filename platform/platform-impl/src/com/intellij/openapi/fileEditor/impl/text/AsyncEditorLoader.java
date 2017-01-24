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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class AsyncEditorLoader {
  private static final ExecutorService ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("AsyncEditorLoader pool", 2);
  private static final Key<AsyncEditorLoader> ASYNC_LOADER = Key.create("ASYNC_LOADER");
  private static final int SYNCHRONOUS_LOADING_WAITING_TIME_MS = 200;
  private static final int RETRY_TIME_MS = 10;
  @NotNull private final Editor myEditor;
  @NotNull private final Project myProject;
  @NotNull private final TextEditorImpl myTextEditor;
  @NotNull private final TextEditorComponent myEditorComponent;
  @NotNull private final TextEditorProvider myProvider;
  private final List<Runnable> myDelayedActions = new ArrayList<>();
  private TextEditorState myDelayedState;
  private final CompletableFuture<?> myLoadingFinished = new CompletableFuture<>();

  AsyncEditorLoader(@NotNull TextEditorImpl textEditor, @NotNull TextEditorComponent component, @NotNull TextEditorProvider provider) {
    myProvider = provider;
    myTextEditor = textEditor;
    myProject = textEditor.myProject;
    myEditorComponent = component;

    myEditor = textEditor.getEditor();
    myEditor.putUserData(ASYNC_LOADER, this);

    myEditorComponent.getContentPanel().setVisible(false);
  }

  @NotNull
  Future<?> start() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Future<Runnable> continuationFuture = scheduleLoading();
    boolean showProgress = true;
    if (worthWaiting()) {
      /*
       * Possible alternatives:
       * 1. show "Loading" from the beginning, then it'll be always noticeable at least in fade-out phase
       * 2. show a gray screen for some time and then "Loading" if it's still loading; it'll produce quick background blinking for all editors
       * 3. show non-highlighted and unfolded editor as "Loading" background and allow it to relayout at the end of loading phase
       * 4. freeze EDT a bit and hope that for small editors it'll suffice and for big ones show "Loading" after that.
       * This strategy seems to produce minimal blinking annoyance.
       */
      Runnable continuation = resultInTimeOrNull(continuationFuture, SYNCHRONOUS_LOADING_WAITING_TIME_MS);
      if (continuation != null) {
        showProgress = false;
        loadingFinished(continuation);
      }
    }
    if (showProgress) myEditorComponent.startLoading();
    return myLoadingFinished;
  }

  private Future<Runnable> scheduleLoading() {
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
    long startStamp = myEditor.getDocument().getModificationStamp();
    return ourExecutor.submit(() -> {
      Ref<Runnable> ref = new Ref<>();
      try {
        while (!myEditorComponent.isDisposed()) {
          ProgressIndicatorUtils.runWithWriteActionPriority(
            () -> ref.set(psiDocumentManager.commitAndRunReadAction(() -> myProject.isDisposed() ? EmptyRunnable.INSTANCE
                                                                                                 : myTextEditor.loadEditorInBackground())),
            new ProgressIndicatorBase()
          );
          Runnable continuation = ref.get();
          if (continuation != null) {
            invokeLater(() -> {
              if (startStamp == myEditor.getDocument().getModificationStamp()) loadingFinished(continuation);
              else if (!myProject.isDisposed() && !myEditorComponent.isDisposed()) scheduleLoading();
            });
            return continuation;
          }
          ProgressIndicatorUtils.yieldToPendingWriteActions();
        }
      }
      finally {
        if (ref.isNull()) invokeLater(() -> loadingFinished(null));
      }
      return null;
    });
  }

  private static void invokeLater(Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
  }

  private boolean worthWaiting() {
    // cannot perform commitAndRunReadAction in parallel to EDT waiting
    return !PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments() &&
           !ApplicationManager.getApplication().isWriteAccessAllowed();
  }

  private static <T> T resultInTimeOrNull(Future<T> future, long timeMs) {
    try {
      return future.get(timeMs, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException | TimeoutException ignored) {}
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  private void loadingFinished(Runnable continuation) {
    if (myLoadingFinished.isDone()) return;
    myLoadingFinished.complete(null);
    myEditor.putUserData(ASYNC_LOADER, null);

    if (myEditorComponent.isDisposed()) return;

    if (continuation != null) {
      continuation.run();
    }

    if (myEditorComponent.isLoading()) {
      myEditorComponent.stopLoading();
    }
    myEditorComponent.getContentPanel().setVisible(true);

    if (myDelayedState != null) {
      TextEditorState state = new TextEditorState();
      state.setFoldingState(myDelayedState.getFoldingState());
      myProvider.setStateImpl(myProject, myEditor, state);
      myDelayedState = null;
    }

    for (Runnable runnable : myDelayedActions) {
      myEditor.getScrollingModel().disableAnimation();
      runnable.run();
    }
    myEditor.getScrollingModel().enableAnimation();

    if (FileEditorManager.getInstance(myProject).getSelectedTextEditor() == myEditor) {
      IdeFocusManager.getInstance(myProject).requestFocus(myTextEditor.getPreferredFocusedComponent(), true);
    }
    EditorNotifications.getInstance(myProject).updateNotifications(myTextEditor.myFile);
  }

  public static void performWhenLoaded(@NotNull Editor editor, @NotNull Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    AsyncEditorLoader loader = editor.getUserData(ASYNC_LOADER);
    if (loader == null) {
      runnable.run();
    }
    else {
      loader.myDelayedActions.add(runnable);
    }
  }

  @NotNull
  TextEditorState getEditorState(@NotNull FileEditorStateLevel level) {
    ApplicationManager.getApplication().assertIsDispatchThread();


    TextEditorState state = myProvider.getStateImpl(myProject, myEditor, level);
    if (!myLoadingFinished.isDone() && myDelayedState != null) {
      state.setDelayedFoldState(myDelayedState::getFoldingState);
    }
    return state;
  }

  void setEditorState(@NotNull final TextEditorState state) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (!myLoadingFinished.isDone()) {
      myDelayedState = state;
    }

    myProvider.setStateImpl(myProject, myEditor, state);
  }

  public static boolean isEditorLoaded(@NotNull Editor editor) {
    return editor.getUserData(ASYNC_LOADER) == null;
  }
}