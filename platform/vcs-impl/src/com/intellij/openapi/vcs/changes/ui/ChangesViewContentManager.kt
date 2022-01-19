// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ObjectUtils.tryCast
import com.intellij.vcs.commit.CommitModeManager
import org.jetbrains.annotations.NonNls
import java.util.function.Predicate
import kotlin.properties.Delegates.observable

private const val COMMIT_TOOL_WINDOW = "vcs.commit.tool.window"

private val isCommitToolWindowEnabled
  get() = AdvancedSettings.getBoolean(COMMIT_TOOL_WINDOW)

internal val Project.isCommitToolWindowShown: Boolean
  get() = ChangesViewContentManager.isCommitToolWindowShown(this)

internal fun ContentManager.selectFirstContent() {
  val firstContent = getContent(0)
  if (firstContent != null) setSelectedContent(firstContent)
}

private val LOG = logger<ChangesViewContentManager>()

class ChangesViewContentManager(private val project: Project) : ChangesViewContentI, Disposable {
  private val addedContents = mutableListOf<Content>()

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

  private var isCommitToolWindowShown: Boolean
    by observable(shouldUseCommitToolWindow()) { _, oldValue, newValue ->
      if (oldValue == newValue) return@observable

      remapContents()
      project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
    }

  init {
    ApplicationManager.getApplication().messageBus.connect(project)
      .subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
        override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
          if (id == COMMIT_TOOL_WINDOW) {
            updateToolWindowMapping()
          }
        }
      })
    val projectBusConnection = project.messageBus.connect()
    CommitModeManager.subscribeOnCommitModeChange(projectBusConnection, object : CommitModeManager.CommitModeListener {
      override fun commitModeChanged() = updateToolWindowMapping()
    })
  }

  private fun updateToolWindowMapping() {
    isCommitToolWindowShown = shouldUseCommitToolWindow()
  }

  private fun shouldUseCommitToolWindow() = CommitModeManager.getInstance(project).getCurrentCommitMode().useCommitToolWindow() &&
                                            isCommitToolWindowEnabled

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
    contentManagers.firstNotNullOfOrNull { tryCast(it.selectedContent?.component, aClass) }

  fun isContentSelected(tabName: String): Boolean =
    contentManagers.any { it.selectedContent?.tabName == tabName }

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
    return allContents.filter { predicate.test(it) }
  }

  private fun getContentToolWindowId(tabName: String): String? {
    val content = findContents { it.tabName == tabName }.firstOrNull() ?: return null
    return content.resolveToolWindowId()
  }

  fun initLazyContent(content: Content) {
    val provider = content.getUserData(CONTENT_PROVIDER_SUPPLIER_KEY)?.invoke() ?: return
    content.putUserData(CONTENT_PROVIDER_SUPPLIER_KEY, null)
    provider.initTabContent(content)
    IJSwingUtilities.updateComponentTreeUI(content.component)
  }

  private inner class ContentProvidersListener : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      initLazyContent(event.content)
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
    internal const val COMMIT_TOOLWINDOW_ID = ToolWindowId.COMMIT

    @JvmField
    internal val CONTENT_PROVIDER_SUPPLIER_KEY = Key.create<() -> ChangesViewContentProvider>("CONTENT_PROVIDER_SUPPLIER")

    @JvmField
    val IS_IN_COMMIT_TOOLWINDOW_KEY = Key.create<Boolean>("IS_IN_COMMIT_TOOLWINDOW_KEY")

    @JvmStatic
    fun getInstance(project: Project) = project.service<ChangesViewContentI>()

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

    @JvmStatic
    internal fun getToolWindowId(project: Project, contentEp: ChangesViewContentEP): String {
      if (contentEp.isInCommitToolWindow && isCommitToolWindowShown(project)) return COMMIT_TOOLWINDOW_ID
      return TOOLWINDOW_ID
    }

    @JvmStatic
    fun getToolWindowFor(project: Project, tabName: String): ToolWindow? =
      ToolWindowManager.getInstance(project).getToolWindow(getToolWindowIdFor(project, tabName))

    /**
     * Specified tab order in toolwindow.
     *
     * @see ChangesViewContentManager.TabOrderWeight
     */
    @JvmField
    val ORDER_WEIGHT_KEY = Key.create<Int>("ChangesView.ContentOrderWeight")

    const val LOCAL_CHANGES: @NonNls String = "Local Changes"
    const val REPOSITORY: @NonNls String = "Repository"
    const val INCOMING: @NonNls String = "Incoming"
    const val SHELF: @NonNls String = "Shelf"
    const val BRANCHES: @NonNls String = "Branches"
  }
}


private fun getContentWeight(content: Content): Int {
  val userData = content.getUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY)
  if (userData != null) return userData

  val tabName: @NonNls String = content.tabName
  for (value in ChangesViewContentManager.TabOrderWeight.values()) {
    if (value.tabName != null && value.tabName == tabName) {
      return value.weight
    }
  }

  return ChangesViewContentManager.TabOrderWeight.OTHER.weight
}