// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.function.Consumer;

public class EditorHistoryManagerTest extends PlatformTestCase {
  public void testSavingStateForNotOpenedEditors() throws Exception {
    File dir = createTempDir("foo");
    File file = new File(dir, "some.txt");
    Files.write(file.toPath(), "first line\nsecond line".getBytes(StandardCharsets.UTF_8));
    VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://" + file.getAbsolutePath());
    assertNotNull(virtualFile);

    useRealFileEditorManager();
    allowComponentStateSaving();

    openProjectPerformTaskCloseProject(dir, project -> {
      Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), false);
      EditorTestUtil.addFoldRegion(editor, 15, 16, ".", true);
      FileEditorManager.getInstance(project).closeFile(virtualFile);
    });

    GCUtil.tryForceGC();
    assertNull(FileDocumentManager.getInstance().getCachedDocument(virtualFile));

    openProjectPerformTaskCloseProject(dir, project -> {});

    openProjectPerformTaskCloseProject(dir, project -> {
      Editor newEditor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), false);
      assertEquals("[FoldRegion +(15:16), placeholder='.']", Arrays.toString(newEditor.getFoldingModel().getAllFoldRegions()));
    });
  }

  private void allowComponentStateSaving() {
    boolean saveAllowedBefore = ApplicationManagerEx.getApplicationEx().isSaveAllowed();
    ApplicationManagerEx.getApplicationEx().setSaveAllowed(true);
    Disposer.register(getTestRootDisposable(), () -> ApplicationManagerEx.getApplicationEx().setSaveAllowed(saveAllowedBefore));
  }

  private void useRealFileEditorManager() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener() {
      @Override
      public void projectComponentsRegistered(@NotNull Project project) {
        ((ComponentManagerImpl)project).registerComponentImplementation(FileEditorManager.class, PsiAwareFileEditorManagerImpl.class);
      }
    });
  }

  private void openProjectPerformTaskCloseProject(File projectDir, Consumer<Project> task) throws Exception {
    Project project = new File(projectDir, Project.DIRECTORY_STORE_FOLDER).exists()
                      ? myProjectManager.loadProject(projectDir.getPath())
                      : myProjectManager.createProject(null, projectDir.getPath());
    try {
      assertTrue(myProjectManager.openProject(project));
      task.accept(project);
    }
    finally {
      myProjectManager.closeAndDispose(project);
    }
    UIUtil.dispatchAllInvocationEvents();
  }
}
