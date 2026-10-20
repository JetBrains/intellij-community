// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.ExceptionUtil

private const val FAILURE_MESSAGE = "file editor provider fails on purpose"

class FileEditorProviderListFailureTest : HeavyPlatformTestCase() {
  fun testFailingProviderIsSkippedInsteadOfBreakingTheList() {
    FileEditorProviderBean.EP_NAME.point.registerExtension(FileEditorProviderBean(FailingProvider()), testRootDisposable)
    FileEditorProviderBean.EP_NAME.point.registerExtension(FileEditorProviderBean(AcceptingProvider()), testRootDisposable)

    var providers: List<FileEditorProvider> = emptyList()
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      providers = FileEditorProviderManager.getInstance().getProviderList(project, LightVirtualFile("test.txt", "content"))
    }

    assertEquals(FAILURE_MESSAGE, ExceptionUtil.getRootCause(error).message)
    assertTrue("Providers after the failing one were lost: $providers", providers.any { it is AcceptingProvider })
  }
}

private class FailingProvider : FileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean = throw IllegalStateException(FAILURE_MESSAGE)
  override fun createEditor(project: Project, file: VirtualFile): FileEditor = throw UnsupportedOperationException()
  override fun getEditorTypeId(): String = "failing"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE
}

private class AcceptingProvider : FileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean = true
  override fun createEditor(project: Project, file: VirtualFile): FileEditor = throw UnsupportedOperationException()
  override fun getEditorTypeId(): String = "accepting"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE
}
