// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.HighlightingDuplicateProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsListener
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.analysis.problemsView.toolWindow.HighlightingProblem
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewIconUpdater
import com.intellij.analysis.problemsView.toolWindow.SetUpdateState
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEvent
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEventDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.build.FlowWithHistory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicInteger


internal class ProjectErrorsCollector(val project: Project, coroutineScope: CoroutineScope) : ProblemsCollector {
  private val providerClassFilter = Registry.stringValue("ide.problems.view.provider.class.filter").split(" ,/|").toSet()
  private val fileProblems = mutableMapOf<VirtualFile, MutableSet<FileProblem>>()
  private val otherProblems = mutableSetOf<Problem>()
  private val problemCount = AtomicInteger()

  private val problemEvents = ProjectErrorsEventFlow(coroutineScope)
  private val lifetime: ProblemLifetime = ProblemLifetime(coroutineScope)

  init {
    VirtualFileManager.getInstance().addAsyncFileListener({ onVfsChanges(it);null }, project)
  }

  fun getProjectErrorsFlow(): Flow<List<ProblemEventDto>> {
    return problemEvents.getFlowWithHistory()
      .batchEvents()
      .map { batch -> buildChangelistFromEventsBatch(batch, project, lifetime) }
  }

  override fun getProblemCount(): Int = problemCount.get()

  override fun getProblemFiles(): Set<VirtualFile> = synchronized(fileProblems) {
    fileProblems.keys.toSet()
  }

  override fun getFileProblemCount(file: VirtualFile): Int = synchronized(fileProblems) {
    fileProblems[file]?.size ?: 0
  }

  override fun getFileProblems(file: VirtualFile): Set<FileProblem> = synchronized(fileProblems) {
    fileProblems[file]?.toSet() ?: emptySet()
  }

  override fun getOtherProblemCount(): Int = synchronized(otherProblems) {
    otherProblems.size
  }

  override fun getOtherProblems(): Set<Problem> = synchronized(otherProblems) {
    otherProblems.toSet()
  }

  override fun problemAppeared(problem: Problem) {
    val ignored = isIgnored(problem.provider)
    notify(problem, when {
      ignored -> SetUpdateState.IGNORED
      problem is FileProblem -> process(problem, true) { set ->
        when {
          // do not add HighlightingDuplicate if there is any HighlightingProblem
          problem is HighlightingDuplicateProblem && set.any { it is HighlightingProblem } -> SetUpdateState.IGNORED
          else -> SetUpdateState.add(problem, set)
        }
      }
      else -> synchronized(otherProblems) { SetUpdateState.add(problem, otherProblems) }
    })
    if (!ignored && problem is HighlightingProblem) {
      // remove any HighlightingDuplicate if HighlightingProblem is appeared
      synchronized(fileProblems) {
        fileProblems[problem.file]?.filter { it is HighlightingDuplicateProblem }
      }?.forEach { problemDisappeared(it) }
    }
  }

  override fun problemDisappeared(problem: Problem) {
    notify(problem, when {
      isIgnored(problem.provider) -> SetUpdateState.IGNORED
      problem is FileProblem -> process(problem, false) { SetUpdateState.remove(problem, it) }
      else -> synchronized(otherProblems) { SetUpdateState.remove(problem, otherProblems) }
    })
  }

  override fun problemUpdated(problem: Problem) {
    notify(problem, when {
      isIgnored(problem.provider) -> SetUpdateState.IGNORED
      problem is FileProblem -> process(problem, false) { SetUpdateState.update(problem, it) }
      else -> synchronized(otherProblems) { SetUpdateState.update(problem, otherProblems) }
    })
  }

  private fun isIgnored(provider: ProblemsProvider): Boolean = provider.project != project || providerClassFilter.contains(provider.javaClass.name)

  private fun process(problem: FileProblem, create: Boolean, function: (MutableSet<FileProblem>) -> SetUpdateState): SetUpdateState {
    val file = problem.file
    synchronized(fileProblems) {
      val set = when (create) {
        true -> fileProblems.computeIfAbsent(file) { mutableSetOf() }
        else -> fileProblems[file] ?: return SetUpdateState.IGNORED
      }
      val state = function(set)
      if (set.isEmpty()) fileProblems.remove(file)
      return state
    }
  }

  private fun notify(problem: Problem, state: SetUpdateState, later: Boolean = true) {
    if (state == SetUpdateState.IGNORED || project.isDisposed) return
    if (later && Registry.`is`("ide.problems.view.notify.later")) {
      ApplicationManager.getApplication().invokeLater { notify(problem, state, false) }
      return // notify listeners later on EDT
    }
    when (state) {
      SetUpdateState.ADDED -> {
        project.messageBus.syncPublisher(ProblemsListener.TOPIC).problemAppeared(problem)
        emitProblemAppeared(problem)
        val emptyBefore = problemCount.getAndIncrement() == 0
        if (emptyBefore) ProblemsViewIconUpdater.update(project)
      }
      SetUpdateState.REMOVED -> {
        project.messageBus.syncPublisher(ProblemsListener.TOPIC).problemDisappeared(problem)
        emitProblemDisappeared(problem)
        val emptyAfter = problemCount.decrementAndGet() == 0
        if (emptyAfter) ProblemsViewIconUpdater.update(project)
      }
      SetUpdateState.UPDATED -> {
        project.messageBus.syncPublisher(ProblemsListener.TOPIC).problemUpdated(problem)
        emitProblemUpdated(problem)
      }
      SetUpdateState.IGNORED -> {
      }
    }
  }

  private fun emitProblemAppeared(problem: Problem) {
    problemEvents.problemAppeared(problem)
  }

  private fun emitProblemDisappeared(problem: Problem) {
    problemEvents.problemDisappeared(problem)
  }

  private fun emitProblemUpdated(problem: Problem) {
    problemEvents.problemUpdated(problem)
  }

  private fun onVfsChanges(events: List<VFileEvent>) {
    events
      .filter { it is VFileDeleteEvent || it is VFileMoveEvent }
      .mapNotNull { it.file }
      .distinct()
      .flatMap { getFileProblems(it) }
      .forEach { problemDisappeared(it) }
  }

  companion object {
    fun getInstance(project: Project): ProjectErrorsCollector {
      return ProblemsCollector.getInstance(project) as ProjectErrorsCollector
    }
  }

  private class ProjectErrorsEventFlow(scope: CoroutineScope) : FlowWithHistory<ProblemEvent>(scope) {
    private val fileProblems = mutableMapOf<VirtualFile, MutableSet<FileProblem>>()
    private val otherProblems = mutableSetOf<Problem>()

    override fun getHistory(): List<ProblemEvent> {
      return fileProblems.values.flatten().map { ProblemEvent.Appeared(it) } +
             otherProblems.map { ProblemEvent.Appeared(it) }
    }

    fun problemAppeared(problem: Problem) =
      problemAppearedOrUpdated(
        problem,
        "ProblemEvent.Appeared",
        shouldAlreadyExist = false
      ) { ProblemEvent.Appeared(it) }

    fun problemDisappeared(problem: Problem) = updateHistoryAndEmit {
      if (removeProblem(problem)) {
        ProblemEvent.Disappeared(problem)
      }
      else {
        logDroppedEvent("ProblemEvent.Disappeared", problem, "the problem is not in history")
        null
      }
    }

    fun problemUpdated(problem: Problem) =
      problemAppearedOrUpdated(
        problem,
        "ProblemEvent.Updated",
        shouldAlreadyExist = true
      ) { ProblemEvent.Updated(it) }

    private fun problemAppearedOrUpdated(
      problem: Problem,
      eventName: String,
      shouldAlreadyExist: Boolean,
      eventFactory: (Problem) -> ProblemEvent,
    ) = updateHistoryAndEmit {
      if (containsProblem(problem) != shouldAlreadyExist) {
        val reason = when (shouldAlreadyExist) {
          true -> "the problem is not in history"
          false -> "the problem is already in history"
        }
        logDroppedEvent(eventName, problem, reason)
        return@updateHistoryAndEmit null
      }
      removeProblem(problem)
      addProblem(problem)
      eventFactory(problem)
    }

    private fun logDroppedEvent(eventName: String, problem: Problem, reason: String) {
      thisLogger().debug("Dropping $eventName event because $reason: $problem")
    }

    private fun containsProblem(problem: Problem): Boolean {
      return when (problem) {
        is FileProblem -> fileProblems[problem.file]?.contains(problem) == true
        else -> otherProblems.contains(problem)
      }
    }

    private fun addProblem(problem: Problem) {
      when (problem) {
        is FileProblem -> fileProblems.computeIfAbsent(problem.file) { mutableSetOf() }.add(problem)
        else -> otherProblems.add(problem)
      }
    }

    private fun removeProblem(problem: Problem): Boolean {
      return when (problem) {
        is FileProblem -> {
          var removed = false
          fileProblems[problem.file]?.let { problems ->
            removed = problems.remove(problem)
            if (problems.isEmpty()) {
              fileProblems.remove(problem.file)
            }
          }
          removed
        }
        else -> otherProblems.remove(problem)
      }
    }
  }
}