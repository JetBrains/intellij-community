// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.SHELF
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ObjectUtils.tryCast
import com.intellij.vcs.commit.CommitWorkflowManager
import com.intellij.vcs.commit.CommitWorkflowManager.Companion.isNonModalInSettings
import org.jetbrains.annotations.NonNls
import java.util.function.Predicate
import kotlin.properties.Delegates.observable

private val isCommitToolWindowRegistryValue
  get() = Registry.get("vcs.commit.tool.window")

private val COMMIT_TOOL_WINDOW_CONTENT_FILTER: (String) -> Boolean = { it == LOCAL_CHANGES || it == SHELF }

internal val Project.isCommitToolWindow: Boolean
  get() = ChangesViewContentManager.getInstanceImpl(this)?.isCommitToolWindow == true

internal fun ContentManager.selectFirstContent() {
  val firstContent = getContent(0)
  if (firstContent != null) setSelectedContent(firstContent)
}

private val LOG = logger<ChangesViewContentManager>()

class ChangesViewContentManager(private val project: Project) : ChangesViewContentI, Disposable {
  private val toolWindows = mutableSetOf<ToolWindow>()
  private val addedContents = mutableListOf<Content>()

  private val contentManagers: Collection<ContentManager> get() = toolWindows.map { it.contentManager }

  private fun getToolWindowIdFor(contentName: String): String {
    return if (isCommitToolWindow && COMMIT_TOOL_WINDOW_CONTENT_FILTER(contentName)) COMMIT_TOOLWINDOW_ID else TOOLWINDOW_ID
  }

  private fun Content.resolveToolWindowId() = getToolWindowIdFor(tabName)

  private fun Content.resolveToolWindow(): ToolWindow? {
    val toolWindowId = resolveToolWindowId()
    return toolWindows.find { it.id == toolWindowId }
  }

  private fun Content.resolveContentManager() = resolveToolWindow()?.contentManager

  var isCommitToolWindow: Boolean
    by observable(isCommitToolWindowRegistryValue.asBoolean() && isNonModalInSettings()) { _, oldValue, newValue ->
      if (oldValue == newValue) return@observable

      remapContents()
      project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
    }

  init {
    isCommitToolWindowRegistryValue.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) = updateToolWindowMapping()
    }, this)
  }

  fun updateToolWindowMapping() {
    isCommitToolWindow = CommitWorkflowManager.getInstance(project).isNonModal() && isCommitToolWindowRegistryValue.asBoolean()
  }

  private fun remapContents() {
    val remapped = findContents { it.resolveContentManager() != it.manager }
    remapped.forEach { removeContent(it, false) }
    remapped.forEach { addContent(it) }
  }

  override fun attachToolWindow(toolWindow: ToolWindow) {
    toolWindows.add(toolWindow)
    initContentManager(toolWindow.contentManager)
  }

  private fun initContentManager(contentManager: ContentManager) {
    val listener = ContentProvidersListener()
    contentManager.addContentManagerListener(listener)
    Disposer.register(this, Disposable { contentManager.removeContentManagerListener(listener) })

    val contents = addedContents.filter { it.resolveContentManager() === contentManager }
    contents.forEach {
      addIntoCorrectPlace(contentManager, it)
      IJSwingUtilities.updateComponentTreeUI(it.component)
    }
    addedContents.removeAll(contents)

    // Ensure that first tab is selected after tabs reordering
    contentManager.selectFirstContent()
  }

  override fun dispose() {
    for (content in addedContents) {
      Disposer.dispose(content)
    }
    addedContents.clear()
  }

  override fun addContent(content: Content) {
    val contentManager = content.resolveContentManager()
    if (contentManager == null) {
      addedContents.add(content)
    }
    else {
      addIntoCorrectPlace(contentManager, content)
    }
  }

  override fun removeContent(content: Content) = removeContent(content, true)

  private fun removeContent(content: Content, dispose: Boolean) {
    val contentManager = content.manager
    if (contentManager == null || contentManager.isDisposed) {
      addedContents.remove(content)
      if (dispose) Disposer.dispose(content)
    }
    else {
      contentManager.removeContent(content, dispose)
    }
  }

  override fun setSelectedContent(content: Content) {
    setSelectedContent(content, false)
  }

  override fun setSelectedContent(content: Content, requestFocus: Boolean) {
    content.manager?.setSelectedContent(content, requestFocus)
  }

  override fun <T : Any> getActiveComponent(aClass: Class<T>): T? =
    contentManagers.mapNotNull { tryCast(it.selectedContent?.component, aClass) }.firstOrNull()

  fun isContentSelected(contentName: String): Boolean =
    contentManagers.any { it.selectedContent?.tabName == contentName }

  override fun selectContent(tabName: String) {
    selectContent(tabName, false)
  }

  fun selectContent(tabName: String, requestFocus: Boolean) {
    LOG.debug("select content: $tabName")
    val content = contentManagers.flatMap { it.contents.asList() }.find { it.tabName == tabName } ?: return
    content.manager?.setSelectedContent(content, requestFocus)
  }

  override fun findContents(predicate: Predicate<Content>): List<Content> {
    val allContents = contentManagers.flatMap { it.contents.asList() } + addedContents
    return allContents.filter { predicate.test(it) }.toList()
  }

  private inner class ContentProvidersListener : ContentManagerListener {
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
    const val TOOLWINDOW_ID = ToolWindowId.VCS
    internal const val COMMIT_TOOLWINDOW_ID = "Commit"

    @JvmField
    val CONTENT_PROVIDER_SUPPLIER_KEY = Key.create<() -> ChangesViewContentProvider>("CONTENT_PROVIDER_SUPPLIER")

    @JvmStatic
    fun getInstance(project: Project) = project.service<ChangesViewContentI>()

    internal fun getInstanceImpl(project: Project): ChangesViewContentManager? =
      getInstance(project) as? ChangesViewContentManager

    @JvmStatic
    fun getToolWindowIdFor(project: Project, contentName: String): String? =
      getInstanceImpl(project)?.getToolWindowIdFor(contentName)

    @JvmStatic
    fun getToolWindowFor(project: Project, contentName: String): ToolWindow? =
      ToolWindowManager.getInstance(project).getToolWindow(getToolWindowIdFor(project, contentName))

    @JvmField
    val ORDER_WEIGHT_KEY = Key.create<Int>("ChangesView.ContentOrderWeight")

    @NonNls
    const val LOCAL_CHANGES = "Local Changes"

    @NonNls
    const val REPOSITORY = "Repository"

    @NonNls
    const val INCOMING = "Incoming"

    @NonNls
    const val SHELF = "Shelf"

    @NonNls
    const val BRANCHES = "Branches"
  }
}


private fun getContentWeight(content: Content): Int {
  val userData = content.getUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY)
  if (userData != null) return userData

  val tabName = content.tabName
  for (value in ChangesViewContentManager.TabOrderWeight.values()) {
    if (value.tabName != null && value.tabName == tabName) {
      return value.weight
    }
  }

  return ChangesViewContentManager.TabOrderWeight.OTHER.weight
}