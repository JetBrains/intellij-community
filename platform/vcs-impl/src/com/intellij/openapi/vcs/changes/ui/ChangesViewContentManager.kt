// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import java.util.*
import java.util.function.Predicate

class ChangesViewContentManager : ChangesViewContentI, Disposable {

  private var contentManager: ContentManager? = null
  private val addedContents = ArrayList<Content>()

  override fun setContentManager(manager: ContentManager) {
    contentManager = manager.also {
      val contentProvidersListener = ContentProvidersListener()
      it.addContentManagerListener(contentProvidersListener)
      Disposer.register(this, Disposable { it.removeContentManagerListener(contentProvidersListener) })
    }

    for (content in addedContents) {
      addIntoCorrectPlace(manager, content)
      IJSwingUtilities.updateComponentTreeUI(content.component)
    }
    addedContents.clear()

    // Ensure that first tab is selected after tabs reordering
    val firstContent = manager.getContent(0)
    if (firstContent != null) manager.setSelectedContent(firstContent)
  }

  override fun dispose() {
    for (content in addedContents) {
      Disposer.dispose(content)
    }
    addedContents.clear()
  }

  override fun addContent(content: Content) {
    val contentManager = contentManager
    if (contentManager == null) {
      addedContents.add(content)
    }
    else {
      addIntoCorrectPlace(contentManager, content)
    }
  }

  override fun removeContent(content: Content) {
    val contentManager = contentManager
    if (contentManager == null || contentManager.isDisposed) {
      addedContents.remove(content)
      Disposer.dispose(content)
    }
    else {
      contentManager.removeContent(content, true)
    }
  }

  override fun setSelectedContent(content: Content) {
    setSelectedContent(content, false)
  }

  override fun setSelectedContent(content: Content, requestFocus: Boolean) {
    val contentManager = contentManager ?: return
    contentManager.setSelectedContent(content, requestFocus)
  }

  override fun <T> getActiveComponent(aClass: Class<T>): T? {
    val selectedContent = contentManager?.selectedContent ?: return null
    return ObjectUtils.tryCast(selectedContent.component, aClass)
  }

  fun isContentSelected(contentName: String): Boolean {
    val selectedContent = contentManager?.selectedContent ?: return false
    return Comparing.equal(contentName, selectedContent.tabName)
  }

  override fun selectContent(tabName: String) {
    selectContent(tabName, false)
  }

  fun selectContent(tabName: String, requestFocus: Boolean) {
    val contentManager = contentManager ?: return
    val toSelect = ContainerUtil.find(contentManager.contents) { content -> content.tabName == tabName }
    if (toSelect != null) {
      contentManager.setSelectedContent(toSelect, requestFocus)
    }
  }

  override fun findContents(predicate: Predicate<Content>): List<Content> {
    val contents = contentManager?.contents?.let { listOf(*it) } ?: addedContents
    return ContainerUtil.filter(contents) { content -> predicate.test(content) }
  }

  private inner class ContentProvidersListener : ContentManagerAdapter() {
    override fun selectionChanged(event: ContentManagerEvent) {
      val content = event.content
      val provider = content.getUserData(CONTENT_PROVIDER_SUPPLIER_KEY)?.invoke() ?: return
      provider.initTabContent(content)
      IJSwingUtilities.updateComponentTreeUI(content.component)
      content.putUserData(CONTENT_PROVIDER_SUPPLIER_KEY, null)
    }
  }

  enum class TabOrderWeight(val tabName: String?, val weight: Int) {
    LOCAL_CHANGES(ChangesViewContentManager.LOCAL_CHANGES, 10),
    REPOSITORY(ChangesViewContentManager.REPOSITORY, 20),
    INCOMING(ChangesViewContentManager.INCOMING, 30),
    SHELF(ChangesViewContentManager.SHELF, 40),
    BRANCHES(ChangesViewContentManager.BRANCHES, 50),
    OTHER(null, 100),
    LAST(null, Integer.MAX_VALUE)
  }

  private fun addIntoCorrectPlace(contentManager: ContentManager, content: Content) {
    val weight = getContentWeight(content)

    val contents = contentManager.contents

    var index = -1
    for (i in contents.indices) {
      val oldWeight = getContentWeight(contents[i])
      if (oldWeight > weight) {
        index = i
        break
      }
    }

    if (index == -1) index = contents.size
    contentManager.addContent(content, index)
  }

  companion object {
    @JvmField
    val TOOLWINDOW_ID: String = ToolWindowId.VCS
    @JvmField
    val CONTENT_PROVIDER_SUPPLIER_KEY = Key.create<() -> ChangesViewContentProvider>("CONTENT_PROVIDER_SUPPLIER")

    @JvmStatic
    fun getInstance(project: Project): ChangesViewContentI {
      return project.getService(ChangesViewContentI::class.java)
    }

    @JvmField
    val ORDER_WEIGHT_KEY = Key.create<Int>("ChangesView.ContentOrderWeight")

    const val LOCAL_CHANGES = "Local Changes"
    const val REPOSITORY = "Repository"
    const val INCOMING = "Incoming"
    const val SHELF = "Shelf"
    const val BRANCHES = "Branches"

    private fun getContentWeight(content: Content): Int {
      val userData = content.getUserData(ORDER_WEIGHT_KEY)
      if (userData != null) return userData

      val tabName = content.tabName
      for (value in TabOrderWeight.values()) {
        if (value.tabName != null && value.tabName == tabName) {
          return value.weight
        }
      }

      return TabOrderWeight.OTHER.weight
    }
  }
}
