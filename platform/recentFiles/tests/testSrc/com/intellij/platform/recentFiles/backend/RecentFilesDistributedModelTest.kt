// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.frontend.RecentFilesExcluder
import com.intellij.platform.recentFiles.frontend.model.FrontendRecentFilesModel
import com.intellij.platform.recentFiles.shared.FileChangeKind
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class RecentFilesDistributedModelTest {
  private val projectFixture = projectFixture()
  private val project by projectFixture

  @TestDisposable
  lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    RecentFilesExcluder.EP_NAME.point.registerExtension(TestDiffRecentFilesExcluder(), disposable)
  }

  @Test
  fun `regular file open and close are synchronized between frontend and backend models`() = runBlocking {
    withModelSynchronization {
      val file = LightVirtualFile("regular.txt", "content")
      val anchorFile1 = LightVirtualFile("anchor1.txt", "content")
      val anchorFile2 = LightVirtualFile("anchor2.txt", "content")
      val openedFiles = listOf(file, anchorFile1, anchorFile2)
      val frontendModel = FrontendRecentFilesModel.getInstance(project)

      frontendModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED, openedFiles, FileChangeKind.ADDED)
      frontendModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, openedFiles, FileChangeKind.ADDED)

      waitUntilFileIsPresent(file, RecentFileKind.RECENTLY_OPENED)
      waitUntilFileIsPresent(file, RecentFileKind.RECENTLY_OPENED_UNPINNED)

      frontendModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, listOf(file), FileChangeKind.REMOVED)

      waitUntilFileIsAbsent(file, RecentFileKind.RECENTLY_OPENED_UNPINNED)
      assertBothModelsContain(file, RecentFileKind.RECENTLY_OPENED)
    }
  }

  @Test
  fun `diff file open and close are synchronized between frontend and backend models`() = runBlocking {
    withModelSynchronization {
      val file = TestDiffVirtualFile("diff.txt")
      val frontendModel = FrontendRecentFilesModel.getInstance(project)

      frontendModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED, listOf(file), FileChangeKind.ADDED)
      frontendModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, listOf(file), FileChangeKind.ADDED)

      waitUntilFileIsPresent(file, RecentFileKind.RECENTLY_OPENED)
      waitUntilFileIsPresent(file, RecentFileKind.RECENTLY_OPENED_UNPINNED)

      file.isIncludedInHistory = false
      frontendModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, listOf(file), FileChangeKind.REMOVED)
      frontendModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED, listOf(file), FileChangeKind.REMOVED)

      waitUntilFileIsAbsent(file, RecentFileKind.RECENTLY_OPENED)
      waitUntilFileIsAbsent(file, RecentFileKind.RECENTLY_OPENED_UNPINNED)
    }
  }

  private suspend fun withModelSynchronization(action: suspend CoroutineScope.() -> Unit) = coroutineScope {
    val subscriptions = startModelSynchronization()
    try {
      action()
    }
    finally {
      subscriptions.forEach(Job::cancel)
    }
  }

  private fun CoroutineScope.startModelSynchronization(): List<Job> {
    val backendModel = BackendRecentFilesModel.getInstance(project)
    val frontendModel = FrontendRecentFilesModel.getInstance(project)
    return RecentFileKind.entries.flatMap { kind ->
      listOf(
        launch { backendModel.subscribeToBackendRecentFilesUpdates(kind) },
        launch { frontendModel.subscribeToBackendRecentFilesUpdates(kind) }
      )
    }
  }

  private suspend fun waitUntilFileIsPresent(file: VirtualFile, kind: RecentFileKind) {
    waitUntil("Expected ${file.name} to appear in $kind") {
      frontendFiles(kind).contains(file) && backendFiles(kind).contains(file)
    }
  }

  private suspend fun waitUntilFileIsAbsent(file: VirtualFile, kind: RecentFileKind) {
    var frontendFiles = emptyList<VirtualFile>()
    var backendFiles = emptyList<VirtualFile>()
    waitUntil({ "Expected ${file.name} to disappear from $kind, frontend=${frontendFiles.map { it.name }}, backend=${backendFiles.map { it.name }}" }) {
      frontendFiles = frontendFiles(kind)
      backendFiles = backendFiles(kind)
      !frontendFiles.contains(file) && !backendFiles.contains(file)
    }
  }

  private suspend fun assertBothModelsContain(file: VirtualFile, kind: RecentFileKind) {
    val frontendFiles = frontendFiles(kind)
    val backendFiles = backendFiles(kind)
    assertTrue(frontendFiles.contains(file), "Expected frontend $kind to contain ${file.name}, got ${frontendFiles.map { it.name }}")
    assertTrue(backendFiles.contains(file), "Expected backend $kind to contain ${file.name}, got ${backendFiles.map { it.name }}")
  }

  private suspend fun waitUntil(message: String, condition: suspend () -> Boolean) {
    waitUntil({ message }, condition)
  }

  private suspend fun waitUntil(message: () -> String, condition: suspend () -> Boolean) {
    try {
      withTimeout(5.seconds) {
        while (!condition()) {
          delay(50.milliseconds)
        }
      }
    }
    catch (_: TimeoutCancellationException) {
      fail(message())
    }
  }

  private suspend fun frontendFiles(kind: RecentFileKind): List<VirtualFile> {
    return withContext(Dispatchers.EDT) {
      FrontendRecentFilesModel.getInstance(project).getRecentFiles(kind).mapNotNull { it.virtualFile }
    }
  }

  private fun backendFiles(kind: RecentFileKind): List<VirtualFile> {
    return BackendRecentFilesModel.getInstance(project).getFilesByKind(kind)
  }

  private class TestDiffVirtualFile(name: String) : LightVirtualFile(name, "diff"), IdeDocumentHistoryImpl.OptionallyIncluded {
    var isIncludedInHistory: Boolean = true

    override fun isIncludedInDocumentHistory(project: Project): Boolean {
      return isIncludedInHistory
    }
  }

  private class TestDiffRecentFilesExcluder : RecentFilesExcluder {
    override fun isExcludedFromRecentlyOpened(project: Project, file: VirtualFile): Boolean {
      return file is TestDiffVirtualFile && !file.isIncludedInDocumentHistory(project)
    }
  }
}
