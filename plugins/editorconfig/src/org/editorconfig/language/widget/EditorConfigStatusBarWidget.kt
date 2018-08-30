// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.widget

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import icons.EditorconfigIcons
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.services.EditorConfigNotificationService
import org.editorconfig.language.services.EditorConfigNotificationTopic
import org.editorconfig.language.services.EditorConfigServiceLoaded
import org.editorconfig.language.services.EditorConfigServiceLoading

class EditorConfigStatusBarWidget(project: Project) : EditorBasedStatusBarPopup(project), EditorConfigNotificationServiceListener {
  private val service = EditorConfigNotificationService.getInstance(project)

  init {
    ApplicationManager
      .getApplication()
      .messageBus
      .connect()
      .subscribe(EditorConfigNotificationTopic, this)
  }

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    file ?: return WidgetState.HIDDEN
    val serviceAnswer = service.getApplicableFiles(file)
    return when (serviceAnswer) {
      is EditorConfigServiceLoading -> WidgetState.HIDDEN
      is EditorConfigServiceLoaded -> {
        if (serviceAnswer.list.isEmpty()) WidgetState.HIDDEN
        else EditorConfigWidgetState()
      }
    }
  }

  override fun editorConfigChanged() = update()

  // Should I register service callback here?
  override fun registerCustomListeners() = Unit

  override fun ID() = "edeitorconfig.affects.codestyle.status.bar.widget"

  override fun createPopup(context: DataContext): ListPopup? {
    val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(context) ?: return null
    val serviceAnswer = service.getApplicableFiles(virtualFile)
    val files = when (serviceAnswer) {
      is EditorConfigServiceLoading -> return null
      is EditorConfigServiceLoaded -> serviceAnswer.list
    }

    if (files.isEmpty()) return null
    val step = EditorConfigPopupStep(files, virtualFile)
    val factory = JBPopupFactory.getInstance()
    return factory.createListPopup(step)
  }

  override fun createInstance(project: Project) =
    EditorConfigStatusBarWidget(project)

  private class EditorConfigWidgetState
    : WidgetState(EditorConfigBundle["widget.editorconfig.affected"], null, true) {
    init {
      setIcon(EditorconfigIcons.Editorconfig)
    }
  }
}
