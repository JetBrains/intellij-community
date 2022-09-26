// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface FileEditorProviderManager {
  companion object {
    @JvmStatic
    fun getInstance(): FileEditorProviderManager = service()
  }

  @Deprecated(message = "Use getProviderList", replaceWith = ReplaceWith("getProviderList(project, file)"))
  fun getProviders(project: Project, file: VirtualFile): Array<FileEditorProvider> = getProviderList(project, file).toTypedArray()

  /**
   * @return All providers that can create editor for the specified `file` or empty array if there are none.
   * Please note that returned array is constructed with respect to editor policies.
   */
  fun getProviderList(project: Project, file: VirtualFile): List<FileEditorProvider>

  suspend fun getProvidersAsync(project: Project, file: VirtualFile): List<FileEditorProvider>

  /**
   * @return `null` if no provider with specified `editorTypeId` exists.
   */
  fun getProvider(editorTypeId: String): FileEditorProvider?
}