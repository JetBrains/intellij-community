// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.LineColumn
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.annotations.NonNls
import java.nio.file.Paths

object JBCefPsiNavigationUtils {
  @NonNls
  private val PSI_ELEMENT_COORDINATES = "${JBCefSourceSchemeHandlerFactory.SOURCE_SCHEME}://(.+):(\\d+)".toRegex()
  private const val FILE_PATH_GROUP = 1
  private const val OFFSET_GROUP = 2

  fun navigateTo(requestLink: String): Boolean {
    val (filePath, offset) = parsePsiElementCoordinates(requestLink) ?: return false
    return navigateTo(filePath, offset)
  }

  fun navigateTo(filePath: String, lineColumn: LineColumn): Boolean {
    return navigateTo(filePath, null, lineColumn)
  }
  
  private fun navigateTo(filePath: String, offset: Int?, lineColumn: LineColumn? = null): Boolean {
    DataManager.getInstance().dataContextFromFocusAsync.onSuccess { dataContext ->
      val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return@onSuccess
      val virtualFile = ProjectRootManager.getInstance(project)
                          .contentRoots.asSequence()
                          .map { Paths.get(it.path, filePath) }
                          .mapNotNull(VirtualFileManager.getInstance()::findFileByNioPath)
                          .firstOrNull() ?: return@onSuccess

      val descriptor = offset?.let { OpenFileDescriptor(project, virtualFile, offset) }
                       ?: lineColumn?.let { OpenFileDescriptor(project, virtualFile, lineColumn.line, lineColumn.column) }
                       ?: return@onSuccess

      ApplicationManager.getApplication().invokeLater {
        FileEditorManager.getInstance(project).openEditor(descriptor, true)
      }
    }
    return true
  }

  private fun parsePsiElementCoordinates(rawCoordinates: String): Coordinates? {
    val groups = PSI_ELEMENT_COORDINATES.matchEntire(rawCoordinates)?.groups ?: return null
    val filePath = groups[FILE_PATH_GROUP]?.value ?: return null
    val offset = groups[OFFSET_GROUP]?.value?.toInt() ?: return null
    return Coordinates(filePath, offset)
  }

  private data class Coordinates(val filePath: String, val offset: Int)
}