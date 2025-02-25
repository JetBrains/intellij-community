// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.ide.FileSelectInContext
import com.intellij.ide.IdeBundle
import com.intellij.ide.SelectInManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal

@Service(Service.Level.PROJECT)
internal class ProjectFileNavigatorImpl(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectFileNavigatorImpl = project.service()
  }

  fun scheduleNavigateInProjectView(file: VirtualFile, requestFocus: Boolean) {
    cs.launch(CoroutineName("navigate to $file in project view")) {
      navigateInProjectView(file = file, requestFocus = requestFocus, project = project)
    }
  }
}

@Internal
suspend fun navigateInProjectView(file: VirtualFile, requestFocus: Boolean, project: Project) {
  val context = FileSelectInContext(project, file, null)
  for (target in project.serviceAsync<SelectInManager>().targetList) {
    if (readAction { target.canSelect(context) }) {
      withContext(Dispatchers.EDT) {
        target.selectIn(context, requestFocus)
      }
      return
    }
  }
  val message = IdeBundle.message("error.files.of.this.type.cannot.be.opened", ApplicationNamesInfo.getInstance().productName)
  Messages.showErrorDialog(project, message, IdeBundle.message("title.cannot.open.file"))
}
