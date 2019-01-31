// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.project.ProjectKt;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.ref.WeakReference;
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

    openProjectPerformTaskCloseProject(dir, project -> {
      Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), false);
      EditorTestUtil.waitForLoading(editor);
      EditorTestUtil.addFoldRegion(editor, 15, 16, ".", true);
      FileEditorManager.getInstance(project).closeFile(virtualFile);
    });

    String threadDumpBefore = ThreadDumper.dumpThreadsToString();

    GCUtil.tryGcSoftlyReachableObjects();

    WeakReference<Object> weakReference = new WeakReference<>(new Object());
    do {
      //noinspection CallToSystemGC
      System.gc();
    }
    while (weakReference.get() != null);

    Document document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
    if (document != null) {
      fail("Document wasn't collected, see heap dump at " + publishHeapDump(EditorHistoryManagerTest.class.getName()));
      System.err.println("Keeping a reference to the document: " + document);
      System.err.println(threadDumpBefore);
    }

    openProjectPerformTaskCloseProject(dir, project -> {});

    openProjectPerformTaskCloseProject(dir, project -> {
      Editor newEditor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), false);
      EditorTestUtil.waitForLoading(newEditor);
      assertEquals("[FoldRegion +(15:16), placeholder='.']", Arrays.toString(newEditor.getFoldingModel().getAllFoldRegions()));
    });
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
      ProjectKt.getStateStore(project).saveComponent(EditorHistoryManager.getInstance(project));
    }
    finally {
      myProjectManager.forceCloseProject(project, true);
    }
    UIUtil.dispatchAllInvocationEvents();
  }
}
