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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * @author peter
 */
public class AsyncHighlighterUpdater {
  private static final ExecutorService ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("AsyncEditorLoader Pool", 2);
  private static final Map<Editor, CancellablePromise<?>> ourHighlighterFutures = ContainerUtil.newConcurrentMap();

  public static void updateHighlighters(@NotNull Project project, @NotNull Editor editor, @NotNull VirtualFile file) {
    CancellablePromise<EditorHighlighter> promise = ReadAction
      .nonBlocking(() -> updateHighlighter(project, editor, file))
      .expireWith(project)
      .expireWhen(() -> !file.isValid() || editor.isDisposed())
      .finishOnUiThread(ModalityState.any(), highlighter -> ((EditorEx)editor).setHighlighter(highlighter))
      .submit(ourExecutor);

    CancellablePromise<?> prev = ourHighlighterFutures.put(editor, promise);
    if (prev != null) {
      prev.cancel();
    }
    promise.onProcessed(__ -> ourHighlighterFutures.remove(editor, promise));
  }

  @NotNull
  private static EditorHighlighter updateHighlighter(@NotNull Project project, @NotNull Editor editor, @NotNull VirtualFile file) {
    EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file);
    highlighter.setText(editor.getDocument().getImmutableCharSequence());
    return highlighter;
  }

  @TestOnly
  public static void completeAsyncTasks() {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
  }

}
