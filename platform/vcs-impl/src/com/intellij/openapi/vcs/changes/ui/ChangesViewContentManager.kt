// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.*
import com.intellij.util.Alarm
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.NonNls
import java.util.*
import java.util.function.Predicate
import javax.swing.JPanel

class ChangesViewContentManager(private val project: Project,
                                private val vcsManager: ProjectLevelVcsManager) : ChangesViewContentI, Disposable {

  private val contentManagerListener: MyContentManagerListener

  private var contentManager: ContentManager? = null
  private val vcsChangeAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
  private val addedContents = ArrayList<Content>()

  val componentName: String
    @NonNls
    get() = "ChangesViewContentManager"

  init {
    contentManagerListener = MyContentManagerListener()
    project.messageBus.connect(this).subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, MyVcsListener())
  }

  override fun setUp(toolWindow: ToolWindow) {
    contentManager = toolWindow.contentManager
    contentManager!!.addContentManagerListener(contentManagerListener)
    Disposer.register(this, Disposable { contentManager!!.removeContentManagerListener(contentManagerListener) })

    loadExtensionTabs()

    for (content in addedContents) {
      addIntoCorrectPlace(content)
    }
    addedContents.clear()

    // Ensure that first tab is selected after tabs reordering
    val firstContent = contentManager!!.getContent(0)
    if (firstContent != null) contentManager!!.setSelectedContent(firstContent)
  }

  private fun loadExtensionTabs() {
    for (ep in ChangesViewContentEP.EP_NAME.getExtensions(project)) {
      val predicate = ep.newPredicateInstance(project)
      val shouldShowTab = predicate == null || predicate.`fun`(project)
      if (shouldShowTab) {
        addedContents.add(createExtensionTab(project, ep))
      }
    }
  }

  private fun updateExtensionTabs() {
    if (contentManager == null) return
    for (ep in ChangesViewContentEP.EP_NAME.getExtensions(project)) {
      val predicate = ep.newPredicateInstance(project) ?: continue
      val epContent = ContainerUtil.find(contentManager!!.contents) { content -> content.getUserData(myEPKey) === ep }
      val shouldShowTab = predicate.`fun`(project)
      if (shouldShowTab && epContent == null) {
        val tab = createExtensionTab(project, ep)
        addIntoCorrectPlace(tab)
      }
      else if (!shouldShowTab && epContent != null) {
        contentManager!!.removeContent(epContent, true)
      }
    }
  }

  private fun createExtensionTab(project: Project, ep: ChangesViewContentEP): Content {
    val content = ContentFactory.SERVICE.getInstance().createContent(ContentStub(ep), ep.getTabName(), false)
    content.isCloseable = false
    content.putUserData(myEPKey, ep)

    val preloader = ep.newPreloaderInstance(project)
    preloader?.preloadTabContent(content)

    return content
  }

  private fun updateToolWindowAvailability() {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOLWINDOW_ID) ?: return
    val available = isAvailable
    if (available && !toolWindow.isAvailable) {
      toolWindow.isShowStripeButton = true
    }
    toolWindow.setAvailable(available, null)
  }

  override fun isAvailable(): Boolean {
    return vcsManager.hasAnyMappings()
  }

  override fun dispose() {
    for (content in addedContents) {
      Disposer.dispose(content)
    }
    addedContents.clear()

    vcsChangeAlarm.cancelAllRequests()
  }

  override fun addContent(content: Content) {
    if (contentManager == null) {
      addedContents.add(content)
    }
    else {
      addIntoCorrectPlace(content)
    }
  }

  override fun removeContent(content: Content) {
    if (contentManager == null || contentManager!!.isDisposed) return
    contentManager!!.removeContent(content, true)
  }

  override fun setSelectedContent(content: Content) {
    setSelectedContent(content, false)
  }

  override fun setSelectedContent(content: Content, requestFocus: Boolean) {
    if (contentManager == null) return
    contentManager!!.setSelectedContent(content, requestFocus)
  }

  fun adviseSelectionChanged(listener: ContentManagerListener) {
    contentManager?.let {
      it.addContentManagerListener(listener)
      Disposer.register(this, Disposable { it.removeContentManagerListener(listener) })
    }
  }

  override fun <T> getActiveComponent(aClass: Class<T>): T? {
    if (contentManager == null) return null
    val selectedContent = contentManager!!.selectedContent ?: return null
    return ObjectUtils.tryCast(selectedContent.component, aClass)
  }

  fun isContentSelected(contentName: String): Boolean {
    if (contentManager == null) return false
    val selectedContent = contentManager!!.selectedContent ?: return false
    return Comparing.equal(contentName, selectedContent.tabName)
  }

  override fun selectContent(tabName: String) {
    selectContent(tabName, false)
  }

  fun selectContent(tabName: String, requestFocus: Boolean) {
    if (contentManager == null) return
    val toSelect = ContainerUtil.find(contentManager!!.contents) { content -> content.tabName == tabName }
    if (toSelect != null) {
      contentManager!!.setSelectedContent(toSelect, requestFocus)
    }
  }

  override fun findContents(predicate: Predicate<Content>): List<Content> {
    val contents = if (contentManager != null) listOf(*contentManager!!.contents) else addedContents
    return ContainerUtil.filter(contents) { content -> predicate.test(content) }
  }

  private inner class MyVcsListener : VcsListener {
    override fun directoryMappingChanged() {
      vcsChangeAlarm.cancelAllRequests()
      vcsChangeAlarm.addRequest({
                                  if (project.isDisposed) return@addRequest
                                  updateToolWindowAvailability()
                                  updateExtensionTabs()
                                }, 100, ModalityState.NON_MODAL)
    }
  }

  private class ContentStub constructor(val ep: ChangesViewContentEP) : JPanel()

  private inner class MyContentManagerListener : ContentManagerAdapter() {
    override fun selectionChanged(event: ContentManagerEvent) {
      val content = event.content
      if (content.component is ContentStub) {
        val ep = (content.component as ContentStub).ep
        val provider = ep.getInstance(project)
        provider.initTabContent(content)
      }
    }
  }

  enum class TabOrderWeight(val tabName: String?, val weight: Int) {
    LOCAL_CHANGES(ChangesViewContentManager.LOCAL_CHANGES, 10),
    REPOSITORY(ChangesViewContentManager.REPOSITORY, 20),
    INCOMING(ChangesViewContentManager.INCOMING, 30),
    SHELF(ChangesViewContentManager.SHELF, 40),
    OTHER(null, 100),
    LAST(null, Integer.MAX_VALUE)
  }

  private fun addIntoCorrectPlace(content: Content) {
    val weight = getContentWeight(content)

    val contents = contentManager!!.contents

    var index = -1
    for (i in contents.indices) {
      val oldWeight = getContentWeight(contents[i])
      if (oldWeight > weight) {
        index = i
        break
      }
    }

    if (index == -1) index = contents.size
    contentManager!!.addContent(content, index)
  }

  companion object {
    @JvmField
    val TOOLWINDOW_ID: String = ToolWindowId.VCS
    private val myEPKey = Key.create<ChangesViewContentEP>("ChangesViewContentEP")

    @JvmStatic
    fun getInstance(project: Project): ChangesViewContentI {
      return project.getComponent(ChangesViewContentI::class.java)
    }

    @JvmField
    val ORDER_WEIGHT_KEY = Key.create<Int>("ChangesView.ContentOrderWeight")

    const val LOCAL_CHANGES = "Local Changes"
    const val REPOSITORY = "Repository"
    const val INCOMING = "Incoming"
    const val SHELF = "Shelf"

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
