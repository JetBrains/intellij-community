// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentEP
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.Content
import com.intellij.util.Consumer
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.VcsLogTabsManager.Companion.generateDisplayName
import com.intellij.vcs.log.impl.VcsLogTabsManager.Companion.onDisplayNameChange
import com.intellij.vcs.log.impl.VcsProjectLog.Companion.getLogProviders
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogPanel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.util.concurrent.ExecutionException
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * Provides the Content tab to the ChangesView log toolwindow.
 *
 * Delegates to the VcsLogManager.
 */
@ApiStatus.Internal
class VcsLogContentProvider(private val project: Project) : ChangesViewContentProvider {
  private val projectLog = VcsProjectLog.getInstance(project)
  private val container = JBPanel<JBPanel<*>>(BorderLayout())

  private var tabContent: Content? = null
  var ui: MainVcsLogUi? = null
    private set
  private var logCreationCallback: SettableFuture<MainVcsLogUi>? = null

  init {
    projectLog.logManager?.let { addMainUi(it) }
  }

  override fun initTabContent(content: Content) {
    if (projectLog.isDisposing) return

    thisLogger<VcsLogContentProvider>().debug("Adding main Log ui container to the content for ${project.name}")

    tabContent = content

    // Display name is always used for presentation, tab name is used as an id.
    // See com.intellij.vcs.log.impl.VcsLogContentUtil.selectMainLog.
    tabContent!!.tabName = TAB_NAME //NON-NLS
    updateDisplayName()

    projectLog.createLogInBackground(true)

    content.component = container
    content.setDisposer {
      disposeContent()
      tabContent = null
    }
  }

  @RequiresEdt
  internal fun addMainUi(logManager: VcsLogManager) {
    ThreadingAssertions.assertEventDispatchThread()
    if (ui == null) {
      thisLogger<VcsLogContentProvider>().debug("Creating main Log ui for ${project.name}")

      ui = logManager.createLogUi(MAIN_LOG_ID, VcsLogTabLocation.TOOL_WINDOW, false)
      val panel = VcsLogPanel(logManager, ui!!)
      container.add(panel, BorderLayout.CENTER)

      updateDisplayName()
      ui!!.onDisplayNameChange { updateDisplayName() }

      if (logCreationCallback != null) {
        logCreationCallback!!.set(ui)
        logCreationCallback = null
      }
    }
  }

  private fun updateDisplayName() {
    if (tabContent != null && ui != null) {
      tabContent!!.displayName = generateDisplayName(ui!!)
    }
  }

  @RequiresEdt
  internal fun disposeMainUi() {
    ThreadingAssertions.assertEventDispatchThread()

    container.removeAll()
    logCreationCallback?.let { callback ->
      logCreationCallback = null
      callback.set(null)
    }
    ui?.let { oldUi ->
      ui = null
      Disposer.dispose(oldUi)
    }
  }

  /**
   * Executes a consumer when a main log ui is created. If main log ui already exists, executes it immediately.
   * Overwrites any consumer that was added previously: only the last one gets executed.
   *
   * @param consumer consumer to execute.
   */
  @RequiresEdt
  fun executeOnMainUiCreated(consumer: Consumer<in MainVcsLogUi>) {
    ThreadingAssertions.assertEventDispatchThread()
    val future = waitMainUiCreation()
    future.addListener({
                         try {
                           val result = future.get()
                           if (result != null) consumer.consume(result)
                         }
                         catch (ignore: InterruptedException) {
                         }
                         catch (ignore: ExecutionException) {
                         }
                       }, MoreExecutors.directExecutor())
  }

  @RequiresEdt
  fun waitMainUiCreation(): ListenableFuture<MainVcsLogUi> {
    ThreadingAssertions.assertEventDispatchThread()
    if (ui != null) {
      return Futures.immediateFuture(ui)
    }

    if (logCreationCallback != null) {
      logCreationCallback!!.set(null)
    }
    val settableFuture = SettableFuture.create<MainVcsLogUi>()
    logCreationCallback = settableFuture
    return settableFuture
  }

  override fun disposeContent() = disposeMainUi()

  internal class VcsLogVisibilityPredicate : Predicate<Project> {
    override fun test(project: Project): Boolean {
      return !getLogProviders(project).isEmpty()
    }
  }

  internal class DisplayNameSupplier : Supplier<String> {
    override fun get(): String = VcsLogBundle.message("vcs.log.tab.name")
  }

  companion object {
    const val TAB_NAME: @NonNls String = "Log" // used as tab id, not user-visible
    const val MAIN_LOG_ID: @NonNls String = "MAIN"
  }
}

internal fun getVcsLogContentProvider(project: Project): VcsLogContentProvider? {
  for (ep in ChangesViewContentEP.EP_NAME.getExtensions(project)) {
    if (ep.getClassName() == VcsLogContentProvider::class.java.name) {
      return ep.cachedInstance as VcsLogContentProvider?
    }
  }
  return null
}
