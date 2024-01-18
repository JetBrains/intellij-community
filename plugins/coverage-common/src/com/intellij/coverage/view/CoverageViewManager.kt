// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageBundle
import com.intellij.coverage.CoverageOptionsProvider
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.view.CoverageViewManager.StateBean
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.NonNls

@State(name = "CoverageViewManager", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class CoverageViewManager(private val myProject: Project) : PersistentStateComponent<StateBean?>, Disposable.Default {
  var stateBean = StateBean()
    private set
  private val myViews: MutableMap<CoverageSuitesBundle, CoverageView> = HashMap()

  @Volatile
  private var myContentManager: ContentManager? = null

  @RequiresEdt
  private fun initializeToolWindow(): ContentManager? {
    if (myProject.isDisposed || !myProject.isOpen) return null
    val toolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(TOOLWINDOW_ID) {
      sideTool = true
      icon = AllIcons.Toolwindows.ToolWindowCoverage
      anchor = ToolWindowAnchor.RIGHT
      stripeTitle = CoverageBundle.messagePointer("coverage.view.title")
    }
    toolWindow.helpId = CoverageView.HELP_ID
    return toolWindow.contentManager
  }

  override fun getState(): StateBean {
    if (!myViews.isEmpty()) {
      val view = myViews.values.iterator().next()
      view.saveSize()
    }
    return stateBean
  }

  override fun loadState(state: StateBean) {
    stateBean = state
  }

  @Synchronized
  fun getView(suitesBundle: CoverageSuitesBundle): CoverageView? {
    return myViews[suitesBundle]
  }

  val openedSuite: CoverageSuitesBundle?
    get() {
      val manager = myContentManager ?: return null
      val selectedContent = manager.selectedContent
      if (selectedContent == null) return null
      return myViews.firstNotNullOfOrNull { (suite, view) -> suite.takeIf { selectedContent === manager.getContent(view) } }
    }

  @RequiresEdt
  fun activateToolwindow(view: CoverageView) {
    val manager = myContentManager ?: return
    manager.setSelectedContent(manager.getContent(view))
    val toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOLWINDOW_ID)
                     ?: error("Coverage toolwindow is not registered")
    toolWindow.activate(null, false)
  }

  @RequiresEdt
  fun createView(suitesBundle: CoverageSuitesBundle, activate: Boolean) {
    var coverageView = myViews[suitesBundle]
    val manager = getContentManager() ?: return
    val content = if (coverageView == null) {
      coverageView = CoverageView(myProject, suitesBundle, stateBean)
      myViews[suitesBundle] = coverageView
      manager.factory.createContent(coverageView, getDisplayName(suitesBundle), false)
        .also { manager.addContent(it) }
    }
    else {
      manager.getContent(coverageView)
    }
    manager.setSelectedContent(content)
    if (CoverageOptionsProvider.getInstance(myProject).activateViewOnRun() && activate) {
      activateToolwindow(coverageView)
    }
  }

  fun closeView(suitesBundle: CoverageSuitesBundle) {
    val oldView = myViews.remove(suitesBundle)
    if (oldView != null) {
      oldView.saveSize()
      runInEdt {
        val manager = myContentManager ?: return@runInEdt
        val content = manager.getContent(oldView)
        if (content != null) {
          manager.removeContent(content, false)
        }
      }
    }
  }


  @Deprecated("Use getView(CoverageSuitesBundle) instead", ReplaceWith("getView(suitesBundle)"))
  fun getToolwindow(suitesBundle: CoverageSuitesBundle): CoverageView? {
    return getView(suitesBundle)
  }

  @Deprecated("Use activateToolwindow(CoverageView) instead", ReplaceWith("if (activate) activateToolwindow(view)"))
  fun activateToolwindow(view: CoverageView, activate: Boolean) {
    if (activate) {
      runInEdt {
        activateToolwindow(view)
      }
    }
  }

  @Deprecated("Use createView instead", ReplaceWith("createView(suitesBundle, activate)"))
  @RequiresEdt
  fun createToolWindow(suitesBundle: CoverageSuitesBundle, activate: Boolean) {
    createView(suitesBundle, activate)
  }

  /**
   * Call to this method initializes the coverage tool window.
   */
  @RequiresEdt
  private fun getContentManager(): ContentManager? {
    myContentManager?.also { return it }
    return initializeToolWindow()?.also { myContentManager = it }
  }

  class StateBean {
    private var myFlattenPackages = false

    @JvmField
    var myAutoScrollToSource: Boolean = false

    @JvmField
    var myAutoScrollFromSource: Boolean = false

    @JvmField
    var myColumnSize: List<Int>? = null

    @JvmField
    var myAscendingOrder: Boolean = true

    @JvmField
    var mySortingColumn: Int = 0

    private var myHideFullyCovered = false
    private var myShowOnlyModified = true
    var isDefaultFilters: Boolean = true
      private set

    private val myListeners = DisposableWrapperList<CoverageViewSettingsListener>()

    var isFlattenPackages: Boolean
      get() = myFlattenPackages
      set(flattenPackages) {
        if (myFlattenPackages != flattenPackages) {
          myFlattenPackages = flattenPackages
          fireChanged()
        }
      }

    var isHideFullyCovered: Boolean
      get() = myHideFullyCovered
      set(hideFullyCovered) {
        if (myHideFullyCovered != hideFullyCovered) {
          myHideFullyCovered = hideFullyCovered
          isDefaultFilters = false
          fireChanged()
        }
      }

    var isShowOnlyModified: Boolean
      get() = myShowOnlyModified
      set(showOnlyModified) {
        if (myShowOnlyModified != showOnlyModified) {
          myShowOnlyModified = showOnlyModified
          isDefaultFilters = false
          fireChanged()
        }
      }

    fun addListener(disposable: Disposable, listener: CoverageViewSettingsListener) {
      myListeners.add(listener, disposable)
    }


    private fun fireChanged() {
      for (listener in myListeners) {
        listener.onSettingsChanged(this)
      }
    }
  }

  fun interface CoverageViewSettingsListener {
    fun onSettingsChanged(stateBean: StateBean?)
  }

  companion object {
    const val TOOLWINDOW_ID: @NonNls String = "Coverage"

    @JvmStatic
    fun getInstance(project: Project): CoverageViewManager = project.service()

    @JvmStatic
    fun getInstanceIfCreated(project: Project): CoverageViewManager? = project.serviceIfCreated()

    fun getDisplayName(suitesBundle: CoverageSuitesBundle): @NlsSafe String? {
      val configuration = suitesBundle.runConfiguration
      return configuration?.name ?: suitesBundle.presentableName
    }
  }
}
