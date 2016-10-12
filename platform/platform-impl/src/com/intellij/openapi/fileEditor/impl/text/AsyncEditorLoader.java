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
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class AsyncEditorLoader {
  private static final ExecutorService ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(2);
  private static final Key<AsyncEditorLoader> ASYNC_LOADER = Key.create("ASYNC_LOADER");
  private static boolean ourCreatingAsyncEditor;
  @NotNull private final Editor myEditor;
  @NotNull private final Project myProject;
  @NotNull private final TextEditorImpl myTextEditor;
  @NotNull private final TextEditorComponent myEditorComponent;
  @NotNull private final TextEditorProvider myProvider;
  private boolean myLoaded;
  private final List<Runnable> myDelayedActions = new ArrayList<>();
  private TextEditorState myDelayedState;

  AsyncEditorLoader(@NotNull TextEditorImpl textEditor, @NotNull TextEditorComponent component, @NotNull TextEditorProvider provider) {
    myProvider = provider;
    myTextEditor = textEditor;
    myProject = textEditor.myProject;
    myEditorComponent = component;

    myEditor = textEditor.getEditor();
    myEditor.putUserData(ASYNC_LOADER, this);

    myEditorComponent.getContentPanel().setVisible(false);
  }

  void scheduleBackgroundLoading(boolean firstTime) {
    ReadTask task = new ReadTask() {
      PsiDocumentManager pdm = PsiDocumentManager.getInstance(myProject);
      long startStamp = myEditor.getDocument().getModificationStamp();

      @Override
      public Continuation runBackgroundProcess(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
        return pdm.commitAndRunReadAction(() -> {
          if (Disposer.isDisposed(myTextEditor)) return null;

          Runnable applyResults = myTextEditor.loadEditorInBackground();
          return new Continuation(() -> {
            if (Disposer.isDisposed(myTextEditor)) return;

            if (startStamp != myEditor.getDocument().getModificationStamp()) {
              onCanceled(indicator);
              return;
            }

            applyResults.run();
            loadingFinished();
          }, ModalityState.any());
        });
      }

      @Override
      public void onCanceled(@NotNull ProgressIndicator indicator) {
        if (!Disposer.isDisposed(myTextEditor) && !myProject.isDisposed()) {
          scheduleBackgroundLoading(false);
        }
      }
    };

    if (!firstTime || !loadImmediately(task)) {
      myEditorComponent.startLoading();
      ProgressIndicatorUtils.scheduleWithWriteActionPriority(ourExecutor, task);
    }
  }

  /**
   * Possible alternatives:
   * 1. show "Loading" from the beginning, then it'll be always noticeable at least in fade-out phase
   * 2. show a gray screen for some time and then "Loading" if it's still loading; it'll produce quick background blinking for all editors
   * 3. show non-highlighted and unfolded editor as "Loading" background and allow it to relayout at the end of loading phase
   * 4. freeze EDT a bit and hope that for small editors it'll suffice and for big ones show "Loading" after that.
   * This strategy seems to produce minimal blinking annoyance.
   */
  private boolean loadImmediately(ReadTask task) {
    if (PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments() ||
        ApplicationManager.getApplication().isWriteAccessAllowed()) {
      return false; // cannot perform commitAndRunReadAction in parallel to EDT waiting
    }

    Semaphore semaphore = new Semaphore();
    semaphore.down();
    Ref<ReadTask.Continuation> continuationRef = Ref.create();
    ProgressIndicatorBase indicator = new ProgressIndicatorBase();
    ourExecutor.submit(() -> {
      try {
        ProgressIndicatorUtils.runWithWriteActionPriority(() -> continuationRef.set(task.runBackgroundProcess(indicator)),
                                                          indicator);
      }
      finally {
        semaphore.up();
      }
    });
    ReadTask.Continuation applyImmediately = semaphore.waitFor(200) ? continuationRef.get() : null;
    if (applyImmediately != null) {
      applyImmediately.getAction().run();
      return true;
    }

    indicator.cancel();
    return false;
  }

  private void loadingFinished() {
    myLoaded = true;
    myEditor.putUserData(ASYNC_LOADER, null);
    myEditorComponent.stopLoading();
    myEditorComponent.getContentPanel().setVisible(true);

    if (myDelayedState != null) {
      TextEditorState state = new TextEditorState();
      state.setFoldingState(myDelayedState.getFoldingState());
      myProvider.setStateImpl(myProject, myEditor, state);
      myDelayedState = null;
    }

    for (Runnable runnable : ObjectUtils.assertNotNull(myDelayedActions)) {
      myEditor.getScrollingModel().disableAnimation();
      runnable.run();
    }
    myEditor.getScrollingModel().enableAnimation();

    if (FileEditorManager.getInstance(myProject).getSelectedTextEditor() == myEditor) {
      IdeFocusManager.getInstance(myProject).requestFocus(myTextEditor.getPreferredFocusedComponent(), true);
    }
  }

  public static void performWhenLoaded(@NotNull Editor editor, @NotNull Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    AsyncEditorLoader loader = editor.getUserData(ASYNC_LOADER);
    if (loader == null) {
      runnable.run();
    } else {
      loader.myDelayedActions.add(runnable);
    }
  }

  @NotNull
  TextEditorState getEditorState(@NotNull FileEditorStateLevel level) {
    ApplicationManager.getApplication().assertIsDispatchThread();


    TextEditorState state = myProvider.getStateImpl(myProject, myEditor, level);
    if (!myLoaded && myDelayedState != null) {
      state.setDelayedFoldState(myDelayedState::getFoldingState);
    }
    return state;
  }

  void setEditorState(@NotNull final TextEditorState state) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (!myLoaded) {
      myDelayedState = state;
    }

    myProvider.setStateImpl(myProject, myEditor, state);
  }

  public static boolean isEditorLoaded(@NotNull Editor editor) {
    return editor.getUserData(ASYNC_LOADER) == null;
  }
}