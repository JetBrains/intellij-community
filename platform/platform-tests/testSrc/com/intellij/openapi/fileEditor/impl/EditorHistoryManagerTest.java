// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectServiceContainerCustomizer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.io.PathKt;
import com.intellij.util.ref.GCWatcher;
import com.intellij.util.ui.UIUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class EditorHistoryManagerTest extends HeavyPlatformTestCase {
  public void testSavingStateForNotOpenedEditors() throws Exception {
    Path dir = createTempDir("foo").toPath();
    Path file = dir.resolve("some.txt");
    Files.write(file, "first line\nsecond line".getBytes(StandardCharsets.UTF_8));
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathKt.getSystemIndependentPath(file));
    assertThat(virtualFile).isNotNull();

    useRealFileEditorManager();

    openProjectPerformTaskCloseProject(dir, project -> {
      Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile), false);
      EditorTestUtil.waitForLoading(editor);
      EditorTestUtil.addFoldRegion(editor, 15, 16, ".", true);
      FileEditorManager.getInstance(project).closeFile(virtualFile);
    });

    String threadDumpBefore = ThreadDumper.dumpThreadsToString();

    GCWatcher.tracking(FileDocumentManager.getInstance().getCachedDocument(virtualFile)).tryGc();

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
    ProjectServiceContainerCustomizer.getEp().maskAll(Collections.singletonList(project -> {
      ServiceContainerUtil.registerComponentImplementation(project, FileEditorManager.class, PsiAwareFileEditorManagerImpl.class, false);
    }), getTestRootDisposable(), false);
  }

  private void openProjectPerformTaskCloseProject(Path projectDir, Consumer<Project> task) {
    Project project = Files.exists(projectDir.resolve(Project.DIRECTORY_STORE_FOLDER))
                      ? myProjectManager.loadProject(projectDir)
                      : myProjectManager.createProject(null, projectDir.toString());
    try {
      assertThat(myProjectManager.openProject(project)).isTrue();
      task.accept(project);
      ProjectKt.getStateStore(project).saveComponent(EditorHistoryManager.getInstance(project));
    }
    finally {
      myProjectManager.forceCloseProject(project, true);
    }
    UIUtil.dispatchAllInvocationEvents();
  }
}
