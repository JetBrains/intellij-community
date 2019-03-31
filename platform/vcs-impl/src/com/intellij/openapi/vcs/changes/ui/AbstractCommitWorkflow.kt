// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.PseudoMap
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction.addUnversionedFilesToVcs
import com.intellij.openapi.vcs.checkin.BaseCheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.impl.CheckinHandlersManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EventDispatcher
import com.intellij.util.NullableFunction
import com.intellij.util.PairConsumer
import com.intellij.util.containers.ContainerUtil.newUnmodifiableList
import com.intellij.util.containers.ContainerUtil.unmodifiableOrEmptySet
import java.util.*

internal fun CommitOptions.saveState() = allOptions.forEach { it.saveState() }
internal fun CommitOptions.restoreState() = allOptions.forEach { it.restoreState() }
internal fun CommitOptions.refresh() = allOptions.forEach { it.refresh() }

interface CommitWorkflowListener : EventListener {
  fun vcsesChanged()

  fun beforeCommitChecksStarted()
  fun beforeCommitChecksEnded(isDefaultCommit: Boolean, result: CheckinHandler.ReturnResult)

  fun customCommitSucceeded()
}

abstract class AbstractCommitWorkflow(val project: Project) {
  protected val eventDispatcher = EventDispatcher.create(CommitWorkflowListener::class.java)

  val commitContext: CommitContext = CommitContext()

  abstract val isDefaultCommitEnabled: Boolean

  // TODO Probably unify with "CommitContext"
  private val _additionalData = PseudoMap<Any, Any>()
  val additionalDataConsumer: PairConsumer<Any, Any> get() = _additionalData
  protected val additionalData: NullableFunction<Any, Any> get() = _additionalData

  private val _vcses = mutableSetOf<AbstractVcs<*>>()
  val vcses: Set<AbstractVcs<*>> get() = unmodifiableOrEmptySet(_vcses.toSet())

  private val _commitHandlers = mutableListOf<CheckinHandler>()
  val commitHandlers: List<CheckinHandler> get() = newUnmodifiableList(_commitHandlers)

  private val _commitOptions = MutableCommitOptions()
  val commitOptions: CommitOptions get() = _commitOptions.toUnmodifiableOptions()

  protected fun updateVcses(vcses: Set<AbstractVcs<*>>) {
    if (_vcses != vcses) {
      _vcses.clear()
      _vcses += vcses

      eventDispatcher.multicaster.vcsesChanged()
    }
  }

  internal fun initCommitHandlers(handlers: List<CheckinHandler>) {
    _commitHandlers.clear()
    _commitHandlers += handlers
  }

  internal fun initCommitOptions(options: CommitOptions) {
    _commitOptions.clear()
    _commitOptions.add(options)
  }

  fun addListener(listener: CommitWorkflowListener, parent: Disposable) = eventDispatcher.addListener(listener, parent)

  fun addUnversionedFiles(changeList: LocalChangeList, unversionedFiles: List<VirtualFile>, callback: (List<Change>) -> Unit): Boolean {
    if (unversionedFiles.isEmpty()) return true

    FileDocumentManager.getInstance().saveAllDocuments()
    return addUnversionedFilesToVcs(project, changeList, unversionedFiles, callback, null)
  }

  companion object {
    @JvmStatic
    fun getCommitHandlerFactories(project: Project): List<BaseCheckinHandlerFactory> =
      CheckinHandlersManager.getInstance().getRegisteredCheckinHandlerFactories(ProjectLevelVcsManager.getInstance(project).allActiveVcss)

    // TODO Seems, it is better to get handlers/factories for workflow.vcses, but not allActiveVcss
    @JvmStatic
    fun getCommitHandlers(commitPanel: CheckinProjectPanel, commitContext: CommitContext) =
      getCommitHandlerFactories(commitPanel.project)
        .map { it.createHandler(commitPanel, commitContext) }
        .filter { it != CheckinHandler.DUMMY }
  }
}