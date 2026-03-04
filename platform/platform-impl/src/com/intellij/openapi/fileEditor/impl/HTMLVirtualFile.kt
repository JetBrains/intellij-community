// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider.Request
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.containers.WeakList


/**
 * This class is responsible for holding htmlRequest associated with virtualFile.
 *
 * The lifetime of htmlRequest should be long enough in case of opening a fileEditor in split or via Recent Files, see IJPL-194278
 */
internal class HTMLVirtualFile private constructor(
  title: @DialogTitle String,
  fileType: FileType,
  internal var htmlRequest: Request,
  private val ignoreJcef: Boolean,
) : LightVirtualFile(title, fileType, /* text = */ "") {

  companion object {
    private val DISPOSED_REQUEST: Request = Request.html("DISPOSED_REQUEST")

    fun createFile(
      project: Project,
      title: @DialogTitle String,
      htmlRequest: Request,
      fileType: FileType,
      ignoreJcef: Boolean,
    ): HTMLVirtualFile {
      val file = HTMLVirtualFile(title, fileType, htmlRequest, ignoreJcef)
      project.service<HTMLVirtualFileManager>().registerFile(file)
      return file
    }
  }

  fun shouldUseMockEditor(): Boolean {
    return ignoreJcef
  }

  fun isJcefSupported(): Boolean {
    return JBCefApp.isSupported() || ignoreJcef
  }

  fun isDisposed(): Boolean {
    return htmlRequest === DISPOSED_REQUEST
  }

  fun dispose() {
    htmlRequest = DISPOSED_REQUEST
  }
}

@Service(Level.PROJECT)
private class HTMLVirtualFileManager : Disposable {
  private val files: WeakList<HTMLVirtualFile> = WeakList(/* initialCapacity = */ 1)

  fun registerFile(file: HTMLVirtualFile) {
    files.add(file)
  }

  override fun dispose() {
    val files = files.copyAndClear()
    for (file in files) {
      file.dispose()
    }
  }
}
