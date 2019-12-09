// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import javax.swing.JPanel

internal class ChangesViewExtensionsManager(private val project: Project,
                                            private val contentManager: ChangesViewContentI,
                                            parentDisposable: Disposable) {

  init {
    project.messageBus.connect(parentDisposable).subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
                                                           VcsListener { runInEdt { updateExtensionContents() } })
    updateExtensionContents()
  }

  private fun updateExtensionContents() {
    for (ep in ChangesViewContentEP.EP_NAME.getExtensions(project)) {
      val predicate = ep.newPredicateInstance(project)
      val shouldShowTab = predicate?.`fun`(project) ?: true
      val epContent = contentManager.findContents { content -> content.getUserData(EPKey) === ep }.firstOrNull()
      if (shouldShowTab && epContent == null) {
        val tab = createExtensionContent(project, ep)
        contentManager.addContent(tab)
      }
      else if (!shouldShowTab && epContent != null) {
        contentManager.removeContent(epContent)
      }
    }
  }

  private fun createExtensionContent(project: Project, ep: ChangesViewContentEP): Content {
    val content = ContentFactory.SERVICE.getInstance().createContent(JPanel(null), ep.getTabName(), false)
    content.isCloseable = false
    content.putUserData(EPKey, ep)
    content.putUserData(ChangesViewContentManager.CONTENT_PROVIDER_SUPPLIER_KEY) { ep.getInstance(project) }

    val preloader = ep.newPreloaderInstance(project)
    preloader?.preloadTabContent(content)

    return content
  }

  companion object {
    private val EPKey = Key.create<ChangesViewContentEP>("ChangesViewContentEP")
  }
}