// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class EditorLoadTest extends FileEditorManagerTestCase {
  public void testEditorLoadedStateMustTransitionInOneDirection_Stress() throws Exception {
    VirtualFile virtualFile = createTempFile(getTestName(false) + ".txt", "text".getBytes(StandardCharsets.UTF_8));
    AtomicReference<Future<?>> testStateInvariant = new AtomicReference<>();
    AtomicBoolean run = new AtomicBoolean();
    AtomicBoolean loaded = new AtomicBoolean();
    EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
      @Override
      public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        testStateInvariant.set(ApplicationManager.getApplication().executeOnPooledThread(() -> {
          while (run.get()) {
            boolean markedLoaded = AsyncEditorLoader.Companion.isEditorLoaded(editor);
            boolean oldLoaded = loaded.getAndSet(markedLoaded);
            if (oldLoaded && !markedLoaded) {
              throw new AssertionError("incorrect state transition: loaded->!loaded\nthreaddump:\n" + ThreadDumper.dumpThreadsToString());
            }
          }
        }));
      }
    }, getTestRootDisposable());
    for (int i=0; i<100; i++) {
      testStateInvariant.set(null);
      run.set(true);
      loaded.set(false);
      List<FileEditor> editors = manager.openEditor(new OpenFileDescriptor(getProject(), virtualFile, 0), false);
      LOG.debug(i+": "+editors);
      try {
        while (!loaded.get()) {
          UIUtil.dispatchAllInvocationEvents();
        }
        run.set(false);
        testStateInvariant.get().get();
      }
      finally {
        manager.closeFile(virtualFile);
      }
    }
  }
}
