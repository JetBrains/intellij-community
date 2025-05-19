// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ObjectUtils.tryCast
import com.intellij.util.messages.MessageBusConnection
import com.intellij.vcs.commit.CommitMode
import com.intellij.vcs.commit.CommitModeManager
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.NonNls
import java.util.function.Predicate


internal val Project.isCommitToolWindowShown: Boolean
  get() = ChangesViewContentManager.isCommitToolWindowShown(this)

internal fun ContentManager.selectFirstContent() {
  val firstContent = getContent(0)
  if (firstContent != null && selectedContent != firstContent) {
    setSelectedContent(firstContent)
  }
}

private val LOG = logger<ChangesViewContentManager>()

class ChangesViewContentManager private constructor(private val project: Project, coroutineScope: CoroutineScope) : ChangesViewContentI, Disposable {
  private val addedContents = mutableListOf<Content>()
  private var selectedAddedContent: Content? = null

  private val toolWindows = mutableSetOf<ToolWindow>()
  private val contentManagers: Collection<ContentManager> get() = toolWindows.map { it.contentManager }

  private fun Content.resolveToolWindowId(): String {
    val isInCommitToolWindow = IS_IN_COMMIT_TOOLWINDOW_KEY.get(this) == true
    if (isInCommitToolWindow && isCommitToolWindowShown) return COMMIT_TOOLWINDOW_ID
    return TOOLWINDOW_ID
  }

  private fun Content.resolveContentManager(): ContentManager? {
    val toolWindowId = resolveToolWindowId()
    val toolWindow = toolWindows.find { it.id == toolWindowId }
    return toolWindow?.contentManager
  }

  private var isCommitToolWindowShown: Boolean = shouldUseCommitToolWindow()

  init {
    ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      .subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
        override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
          if (id == CommitMode.NonModalCommitMode.COMMIT_TOOL_WINDOW_SETTINGS_KEY) {
            updateToolWindowMappings()
          }
        }
      })
    val projectBusConnection = project.messageBus.connect(coroutineScope)
    CommitModeManager.subscribeOnCommitModeChange(projectBusConnection, object : CommitModeManager.CommitModeListener {
      override fun commitModeChanged() {
        updateToolWindowMappings()
      }
    })
  }

  private fun updateToolWindowMappings() {
    isCommitToolWindowShown = shouldUseCommitToolWindow()
    remapContents()

    project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
    contentManagers.forEach { it.selectFirstContent() }
  }

  private fun shouldUseCommitToolWindow(): Boolean {
    return CommitModeManager.getInstance(project).getCurrentCommitMode().useCommitToolWindow()
  }

  private fun remapContents() {
    val remapped = findContents { it.resolveContentManager() != it.manager }
    remapped.forEach { removeContent(it, false) }
    remapped.forEach { addContent(it) }
  }

  override fun attachToolWindow(toolWindow: ToolWindow) {
    toolWindows.add(toolWindow)
    initContentManager(toolWindow)
  }

  private fun initContentManager(toolWindow: ToolWindow) {
    val contentManager = toolWindow.contentManager
    val listener = ContentProvidersListener(toolWindow)
    contentManager.addContentManagerListener(listener)
    Disposer.register(this, Disposable { contentManager.removeContentManagerListener(listener) })
    project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, listener)

    val contents = addedContents.filter { it.resolveContentManager() === contentManager }
    for (content in contents) {
      addIntoCorrectPlace(contentManager, content)
      IJSwingUtilities.updateComponentTreeUI(content.component)
    }
    addedContents.removeAll(contents)

    val toSelect = selectedAddedContent
    if (toSelect != null && contents.contains(toSelect)) {
      contentManager.setSelectedContent(toSelect)
    }
    else {
      // ensure that the first tab is selected after tab reordering
      contentManager.selectFirstContent()
    }
    selectedAddedContent = null
  }

  override fun dispose() {
    for (content in addedContents) {
      Disposer.dispose(content)
    }
    addedContents.clear()
    selectedAddedContent = null
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

  override fun removeContent(content: Content) {
    removeContent(content, true)
  }

  private fun removeContent(content: Content, dispose: Boolean) {
    val contentManager = content.manager
    if (contentManager == null || contentManager.isDisposed) {
      addedContents.remove(content)
      if (selectedAddedContent == content) selectedAddedContent = null
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
    LOG.debug { "select content: ${content.tabName}" }
    val contentManager = content.manager
    if (contentManager != null) {
      contentManager.setSelectedContent(content, requestFocus)
      selectedAddedContent = null
    }
    else if (addedContents.contains(content)) {
      selectedAddedContent = content
    }
  }

  override fun <T : Any> getActiveComponent(aClass: Class<T>): T? =
    contentManagers.firstNotNullOfOrNull { tryCast(it.selectedContent?.component, aClass) }

  fun isContentSelected(tabName: String): Boolean =
    contentManagers.any { it.selectedContent?.tabName == tabName }

  override fun selectContent(tabName: String) {
    selectContent(tabName, false)
  }

  fun selectContent(tabName: String, requestFocus: Boolean) {
    LOG.debug("select content: $tabName")
    val content = findContent(tabName) ?: return
    setSelectedContent(content, requestFocus)
  }

  override fun findContents(predicate: Predicate<Content>): List<Content> {
    val allContents = contentManagers.flatMap { it.contents.asList() } + addedContents
    return allContents.filter { predicate.test(it) }
  }

  override fun findContent(tabName: String): Content? {
    return findContents { it.tabName == tabName }.firstOrNull()
  }

  private fun getContentToolWindowId(tabName: String): String? {
    val content = findContent(tabName) ?: return null
    return content.resolveToolWindowId()
  }

  fun initLazyContent(content: Content) {
    val provider = content.getUserData(CONTENT_PROVIDER_SUPPLIER_KEY)?.invoke() ?: return
    content.putUserData(CONTENT_PROVIDER_SUPPLIER_KEY, null)
    provider.initTabContent(content)
    IJSwingUtilities.updateComponentTreeUI(content.component)
  }

  private inner class ContentProvidersListener(val toolWindow: ToolWindow) : ContentManagerListener, ToolWindowManagerListener {
    override fun stateChanged(toolWindowManager: ToolWindowManager) {
      if (toolWindow.isVisible) {
        val content = toolWindow.contentManager.selectedContent ?: return
        initLazyContent(content)
      }
    }

    override fun selectionChanged(event: ContentManagerEvent) {
      if (toolWindow.isVisible) {
        initLazyContent(event.content)
      }
    }
  }

  enum class TabOrderWeight(val tabName: String?, val weight: Int) {
    LOCAL_CHANGES(ChangesViewContentManager.LOCAL_CHANGES, 10),
    REPOSITORY(ChangesViewContentManager.REPOSITORY, 20),
    INCOMING(ChangesViewContentManager.INCOMING, 30),
    SHELF(ChangesViewContentManager.SHELF, 40),
    BRANCHES(ChangesViewContentManager.BRANCHES, 50),
    VCS_LOG(ChangesViewContentManager.VCS_LOG, 50), // main tab
    CONSOLE(ChangesViewContentManager.CONSOLE, 60),
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
    const val TOOLWINDOW_ID: String = ToolWindowId.VCS
    internal const val COMMIT_TOOLWINDOW_ID = ToolWindowId.COMMIT

    @JvmField
    internal val CONTENT_PROVIDER_SUPPLIER_KEY = Key.create<() -> ChangesViewContentProvider?>("CONTENT_PROVIDER_SUPPLIER")

    /**
     * Whether [Content] should be shown in [ToolWindowId.COMMIT] toolwindow.
     */
    @JvmField
    val IS_IN_COMMIT_TOOLWINDOW_KEY: Key<Boolean> = Key.create<Boolean>("ChangesViewContentManager.IS_IN_COMMIT_TOOLWINDOW_KEY")

    @JvmField
    val CONTENT_TAB_NAME_KEY: DataKey<@NonNls String> = DataKey.create<@NonNls String>("ChangesViewContentManager.CONTENT_TAB_KEY")

    @JvmStatic
    fun getInstance(project: Project): ChangesViewContentI = project.service<ChangesViewContentI>()

    fun getInstanceImpl(project: Project): ChangesViewContentManager? =
      getInstance(project) as? ChangesViewContentManager

    @JvmStatic
    fun isCommitToolWindowShown(project: Project): Boolean = getInstanceImpl(project)?.isCommitToolWindowShown == true

    @JvmStatic
    fun getToolWindowIdFor(project: Project, tabName: String): String {
      val manager = getInstanceImpl(project) ?: return TOOLWINDOW_ID

      val toolWindowId = manager.getContentToolWindowId(tabName)
      if (toolWindowId != null) {
        return toolWindowId
      }

      val extension = ChangesViewContentEP.EP_NAME.getExtensions(project).find { it.tabName == tabName }
      if (extension != null) return getToolWindowId(project, extension)

      return TOOLWINDOW_ID
    }

    internal fun getToolWindowId(project: Project, contentEp: ChangesViewContentEP): String {
      return if (contentEp.isInCommitToolWindow && isCommitToolWindowShown(project)) COMMIT_TOOLWINDOW_ID else TOOLWINDOW_ID
    }

    @JvmStatic
    fun getToolWindowFor(project: Project, tabName: String): ToolWindow? {
      return ToolWindowManager.getInstance(project).getToolWindow(getToolWindowIdFor(project, tabName))
    }

    /**
     * @see subscribeOnVcsToolWindowLayoutChanges
     */
    @JvmStatic
    fun isToolWindowTabVertical(project: Project, tabName: String): Boolean {
      val toolWindow = getToolWindowFor(project, tabName)
      return toolWindow != null && !toolWindow.anchor.isHorizontal
             && toolWindow.type != ToolWindowType.FLOATING
             && toolWindow.type != ToolWindowType.WINDOWED
    }

    @JvmStatic
    fun shouldHaveSplitterDiffPreview(project: Project, isContentVertical: Boolean): Boolean {
      return !isContentVertical || !isCommitToolWindowShown(project)
    }

    /**
     * Specified tab order in the toolwindow.
     *
     * @see ChangesViewContentManager.TabOrderWeight
     */
    @JvmField
    val ORDER_WEIGHT_KEY: Key<Int> = Key.create<Int>("ChangesView.ContentOrderWeight")

    const val LOCAL_CHANGES: @NonNls String = "Local Changes"
    const val CONSOLE: @NonNls String = "Console"
    const val REPOSITORY: @NonNls String = "Repository"
    const val INCOMING: @NonNls String = "Incoming"
    const val SHELF: @NonNls String = "Shelf"
    const val BRANCHES: @NonNls String = "Branches"
    const val VCS_LOG: @NonNls String = "Log"
  }
}

private fun getContentWeight(content: Content): Int {
  val userData = content.getUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY)
  if (userData != null) return userData

  val tabName: @NonNls String = content.tabName
  for (value in ChangesViewContentManager.TabOrderWeight.entries) {
    if (value.tabName != null && value.tabName == tabName) {
      return value.weight
    }
  }

  return ChangesViewContentManager.TabOrderWeight.OTHER.weight
}

fun MessageBusConnection.subscribeOnVcsToolWindowLayoutChanges(updateLayout: Runnable) {
  subscribe(ChangesViewContentManagerListener.TOPIC, object : ChangesViewContentManagerListener {
    override fun toolWindowMappingChanged() = updateLayout.run()
  })
  subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
    override fun stateChanged(toolWindowManager: ToolWindowManager) = updateLayout.run()
  })
}
