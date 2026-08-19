// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
@DisplayName("ToolWindowEditorTabFileRegistry Specification")
internal class ToolWindowEditorTabFileRegistryTest {

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInit = { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )

  private val project: Project get() = projectFixture.get()

  private val registry: ToolWindowEditorTabFileRegistry
    get() = ToolWindowEditorTabFileRegistry.getInstance()

  private val validToolWindowId = "TestToolWindow"
  private val unknownToolWindowId = "UnknownToolWindow"

  private var testDisposable: Disposable? = null

  /**
   * Project location hashes handed out by [tabPath].
   *
   * [ToolWindowEditorTabFileRegistry] is an application service, so it outlives a single test. Every hash a test
   * touches is recorded here and evicted in [tearDown], which keeps each test from observing another one's files.
   */
  private val usedProjectHashes = mutableSetOf<String>()

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val disposable = Disposer.newDisposable("ToolWindowEditorTabFileRegistryTest")
    testDisposable = disposable

    // The registry only consults the persistence provider, so no ToolWindowEditorTabSupport is registered here.
    registerFakeToolWindowEditorTabPersistenceProvider(
      validToolWindowId,
      FakeToolWindowEditorTabPersistenceProvider(),
      disposable,
    )
  }

  @AfterEach
  fun tearDown() {
    try {
      usedProjectHashes.forEach { registry.removeFilesForProject(it) }
      usedProjectHashes.clear()
    }
    finally {
      testDisposable?.let { Disposer.dispose(it) }
      testDisposable = null
    }
  }

  /**
   * Builds a persistent tab path and records its project hash so [tearDown] evicts whatever the test created.
   */
  private fun tabPath(
    projectLocationHash: String,
    toolWindowId: String = validToolWindowId,
    persistenceId: String = "persist1",
    name: String = "name",
  ): PersistentToolWindowEditorTabPath {
    usedProjectHashes += projectLocationHash
    return PersistentToolWindowEditorTabPath(
      projectLocationHash = projectLocationHash,
      toolWindowId = toolWindowId,
      persistenceId = persistenceId,
      name = name,
    )
  }

  @Nested
  @DisplayName("Contract: Creation & Identity")
  inner class CreationAndIdentity {

    @Test
    @DisplayName("Returns identical VirtualFile instance for equal paths")
    fun `returns identical instance for equal paths`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val path = tabPath("identity-hash")

      val firstCallFile = registry.getOrCreatePersistentFile(path)
      val secondCallFile = registry.getOrCreatePersistentFile(path)

      assertThat(firstCallFile)
        .describedAs("Registry must create a file for a registered tool window ID")
        .isNotNull()

      assertThat(secondCallFile)
        .describedAs("Registry must guarantee VirtualFile identity by returning the exact same instance")
        .isSameAs(firstCallFile)
    }

    @Test
    @DisplayName("Identity resolution strictly ignores the 'name' property")
    fun `resolves to same instance even if paths differ only by name`(): Unit =
      timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
        val pathWithOldName = tabPath("rename-hash", name = "Old Name")
        val pathWithNewName = tabPath("rename-hash", name = "New Name")

        val file1 = registry.getOrCreatePersistentFile(pathWithOldName)
        val file2 = registry.getOrCreatePersistentFile(pathWithNewName)

        assertThat(file1)
          .describedAs("Files differing only by name must resolve to the same underlying VirtualFile")
          .isSameAs(file2)
      }

    @Test
    @DisplayName("Replaces invalidated files with fresh valid instances")
    fun `replaces invalid files with fresh instances`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val path = tabPath("invalidation-hash")

      val originalFile = requireNotNull(registry.getOrCreatePersistentFile(path))

      originalFile.invalidate()
      assertThat(originalFile.isValid).isFalse()

      val newFile = requireNotNull(registry.getOrCreatePersistentFile(path))

      assertThat(newFile)
        .describedAs("Registry must evict the invalid file and create a new one")
        .isNotSameAs(originalFile)

      assertThat(newFile.isValid).isTrue()
    }

    @Test
    @DisplayName("Returns null if no persistence provider is registered")
    fun `returns null for unsupported tool window IDs`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val path = tabPath("no-provider-hash", toolWindowId = unknownToolWindowId)

      val file = registry.getOrCreatePersistentFile(path)

      assertThat(file)
        .describedAs("Must not create files for unregistered tool window IDs")
        .isNull()
    }
  }

  @Nested
  @DisplayName("Contract: Lookup Semantics")
  inner class LookupSemantics {

    @Test
    @DisplayName("Returns null for completely unknown paths")
    fun `returns null for unknown path`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val path = tabPath("unknown-lookup-hash")

      assertThat(registry.findFile(path)).isNull()
    }

    @Test
    @DisplayName("Returns null for invalidated files, preventing their resurrection")
    fun `hides invalidated files from lookup`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val path = tabPath("invalidated-lookup-hash")
      val file = requireNotNull(registry.getOrCreatePersistentFile(path))

      file.invalidate()

      assertThat(registry.findFile(path))
        .describedAs("findFile must actively check isValid and return null for invalid files")
        .isNull()
    }
  }

  @Nested
  @DisplayName("Contract: Cache Eviction")
  inner class CacheEviction {

    @Test
    @DisplayName("removeFile prevents ABA problem by checking strict reference identity")
    fun `removeFile evicts strictly by instance to prevent ABA concurrency issues`(): Unit =
      timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
        val path = tabPath("aba-hash")

        val staleFile = requireNotNull(registry.getOrCreatePersistentFile(path))
        staleFile.invalidate()

        val freshFile = requireNotNull(registry.getOrCreatePersistentFile(path))
        assertThat(freshFile).isNotSameAs(staleFile)

        registry.removeFile(staleFile)

        assertThat(registry.findFile(path))
          .describedAs("removeFile must use ConcurrentHashMap.remove(key, value) so late eviction events don't destroy fresh files")
          .isSameAs(freshFile)
      }

    @Test
    @DisplayName("removeFilesForProject strictly isolates eviction by project location hash")
    fun `removeFilesForProject evicts strictly by project hash`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
      val pathA1 = tabPath("project-a-hash", persistenceId = "p1")
      val pathA2 = tabPath("project-a-hash", persistenceId = "p2")
      val pathB1 = tabPath("project-b-hash", persistenceId = "p1")

      registry.getOrCreatePersistentFile(pathA1)
      registry.getOrCreatePersistentFile(pathA2)
      val fileB1 = requireNotNull(registry.getOrCreatePersistentFile(pathB1))

      registry.removeFilesForProject("project-a-hash")

      assertThat(registry.findFile(pathA1)).isNull()
      assertThat(registry.findFile(pathA2)).isNull()

      assertThat(registry.findFile(pathB1))
        .describedAs("Files belonging to Project B must survive Project A's eviction")
        .isSameAs(fileB1)
    }
  }

  @Nested
  @DisplayName("Integration: ToolWindowEditorTabFileRegistryCleaner")
  inner class CleanerIntegration {

    @Test
    @DisplayName("Cleaner correctly routes project closing event to registry eviction")
    fun `cleaner triggers removeFilesForProject with correct project hash`(): Unit =
      timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
        val path = tabPath(project.locationHash)

        registry.getOrCreatePersistentFile(path)
        assertThat(registry.findFile(path)).isNotNull()

        ToolWindowEditorTabFileRegistryCleaner().projectClosed(project)

        assertThat(registry.findFile(path))
          .describedAs("Cleaner must evict files associated with the closed project's locationHash")
          .isNull()
      }
  }
}
