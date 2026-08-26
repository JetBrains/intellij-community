// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.trustedProjects.ExternallyOpenedFiles
import com.intellij.ide.trustedProjects.TrustedFiles
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.asDisposable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

@TestApplication
@SystemProperty("idea.trust.headless.disabled", "false")
class UntrustedFileEditorProvidersTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val tempPath by tempPathFixture()

  @AfterEach
  fun tearDown() {
    // the mark store is application-level and would leak into the next test
    ExternallyOpenedFiles.getInstance().loadState(ExternallyOpenedFiles.State())
  }

  @Test
  fun `only opted-in providers are offered for untrusted files`(): Unit = timeoutRunBlocking {
    Registry.get(TrustedFiles.SAFE_MODE_REGISTRY_KEY).setValue(true, asDisposable())
    val project = projectFixture.get()

    val outsideFile = tempPath.resolve("outside.txt")
    Files.writeString(outsideFile, "text")
    val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outsideFile))

    // a dynamically registered provider has no `allowedInUntrustedFiles` attribute and must be filtered out
    val dummy = object : FileEditorProvider, DumbAware {
      override fun accept(project: Project, file: VirtualFile): Boolean = true
      override fun createEditor(project: Project, file: VirtualFile): FileEditor = throw UnsupportedOperationException()
      override fun getEditorTypeId(): String = "test-untrusted-dummy"
      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE
    }
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.point.registerExtension(dummy, asDisposable())

    val manager = FileEditorProviderManager.getInstance()
    // an unmarked outside file (an IDE-internal file) keeps the full provider list
    assertTrue(manager.getProviderList(project, file).contains(dummy))
    assertTrue(manager.getProvidersAsync(project, file).contains(dummy))

    TrustedFiles.markExternallyOpened(file)
    assertFalse(manager.getProviderList(project, file).contains(dummy))
    assertFalse(manager.getProvidersAsync(project, file).contains(dummy))

    TrustedProjects.setProjectTrusted(outsideFile, true)
    assertTrue(manager.getProviderList(project, file).contains(dummy))
    assertTrue(manager.getProvidersAsync(project, file).contains(dummy))
  }
}
