// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")
package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectServiceContainerCustomizer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.project.stateStore
import com.intellij.testFramework.*
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.write
import com.intellij.util.ref.GCWatcher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Fail.fail
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.util.*

class EditorHistoryManagerTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val tempDir = TemporaryDirectory()

  @Rule
  @JvmField
  val disposable = DisposableRule()

  @Test
  fun testSavingStateForNotOpenedEditors() {
    val dir = tempDir.newPath("foo")
    val file = dir.resolve("some.txt")
    file.write("first line\nsecond line")

    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.systemIndependentPath)!!
    useRealFileEditorManager()
    openProjectPerformTaskCloseProject(dir) { project ->
      val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, virtualFile), false)!!
      try {
        EditorTestUtil.waitForLoading(editor)
        EditorTestUtil.addFoldRegion(editor, 15, 16, ".", true)
      }
      finally {
        FileEditorManager.getInstance(project).closeFile(virtualFile)
      }
    }

    val threadDumpBefore = ThreadDumper.dumpThreadsToString()
    GCWatcher.tracking(FileDocumentManager.getInstance().getCachedDocument(virtualFile)).ensureCollected()
    val document = FileDocumentManager.getInstance().getCachedDocument(virtualFile)
    if (document != null) {
      fail<Any>("Document wasn't collected, see heap dump at ${HeavyPlatformTestCase.publishHeapDump(EditorHistoryManagerTest::class.java.name)}")
      System.err.println("Keeping a reference to the document: $document")
      System.err.println(threadDumpBefore)
    }
    openProjectPerformTaskCloseProject(dir) { }
    openProjectPerformTaskCloseProject(dir) { project ->
      val newEditor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, virtualFile), false)!!
      EditorTestUtil.waitForLoading(newEditor)
      assertThat(Arrays.toString(newEditor.foldingModel.allFoldRegions)).isEqualTo("[FoldRegion +(15:16), placeholder='.']")
    }
  }

  private fun useRealFileEditorManager() {
    ProjectServiceContainerCustomizer.getEp().maskAll(listOf(object : ProjectServiceContainerCustomizer {
      override fun serviceRegistered(project: Project) {
        project.registerComponentImplementation(FileEditorManager::class.java, PsiAwareFileEditorManagerImpl::class.java, false)
      }
    }), disposable.disposable, false)
  }
}

private fun openProjectPerformTaskCloseProject(projectDir: Path, task: (Project) -> Unit) {
  val project = ProjectManagerEx.getInstanceEx().openProject(projectDir, createTestOpenProjectOptions())!!
  try {
    runInEdtAndWait {
      task(project)
      project.stateStore.saveComponent(EditorHistoryManager.getInstance(project))
    }
  }
  finally {
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)
  }
}