// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.widget

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import icons.EditorconfigIcons
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.services.*

class EditorConfigStatusBarWidget(project: Project) : EditorBasedStatusBarPopup(project), EditorConfigNotificationServiceListener {
  private val service = EditorConfigFileHierarchyService.getInstance(project)

  init {
    ApplicationManager
      .getApplication()
      .messageBus
      .connect()
      .subscribe(EditorConfigNotificationTopic, this)
  }

  private fun getApplicableFiles(file: VirtualFile): EditorConfigServiceResult {
    val start = System.currentTimeMillis()
    val result = service.getParentEditorConfigFiles(file)
    val end = System.currentTimeMillis()
    val duration = end - start
    if (duration > 50) {
      Log.warn("Performance warning: EditorConfigFileHierarchyService took too long to answer ($duration ms)")
    }

    return result
  }

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    file ?: return WidgetState.HIDDEN
    val serviceAnswer = getApplicableFiles(file)
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
    val serviceAnswer = getApplicableFiles(virtualFile)
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

  private companion object {
    private val Log = logger<EditorConfigStatusBarWidget>()
  }
}
