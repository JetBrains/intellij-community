// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.FileEditorManagerTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.fileEditorManagerFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class EditorLoadTest {
  private static final Logger LOG = Logger.getInstance(EditorLoadTest.class);

  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<FileEditorManagerImpl> managerFixture = fileEditorManagerFixture(projectFixture, false);

  @TestDisposable
  private Disposable testRootDisposable;

  @Test
  public void testEditorLoadedStateMustTransitionInOneDirection_Stress(TestInfo testInfo) throws Exception {
    Project project = projectFixture.get();
    FileEditorManagerImpl manager = managerFixture.get();
    String testName = testInfo.getTestMethod().orElseThrow().getName().substring("test".length());
    VirtualFile virtualFile = FileEditorManagerTestUtil.createTempFile(testName + ".txt", "text".getBytes(StandardCharsets.UTF_8));
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
    }, testRootDisposable);
    for (int i=0; i<100; i++) {
      testStateInvariant.set(null);
      run.set(true);
      loaded.set(false);
      int iteration = i;
      EdtTestUtil.runInEdtAndWait(() -> {
        List<FileEditor> editors = manager.openEditor(new OpenFileDescriptor(project, virtualFile, 0), false);
        LOG.debug(iteration+": "+editors);
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
      });
    }
  }
}
