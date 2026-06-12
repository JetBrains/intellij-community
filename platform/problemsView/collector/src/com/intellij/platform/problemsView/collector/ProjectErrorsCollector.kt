// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.collector

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.HighlightingDuplicateProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsListener
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.analysis.problemsView.toolWindow.SetUpdateState
import com.intellij.analysis.problemsView.toolWindow.splitApi.HighlightingBaseProblem
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Internal
class ProjectErrorsCollector(val project: Project) : ProblemsCollector {
  private val providerClassFilter = Registry.stringValue("ide.problems.view.provider.class.filter").split(" ,/|").toSet()
  private val fileProblems = mutableMapOf<VirtualFile, MutableSet<FileProblem>>()
  private val otherProblems = mutableSetOf<Problem>()
  private val problemCount = AtomicInteger()

  private val problemEvents = MutableSharedFlow<ProblemEvent>(replay = 0, extraBufferCapacity = 100)

  init {
    VirtualFileManager.getInstance().addAsyncFileListener({ onVfsChanges(it); null }, project)
  }

  fun getProblemEventsFlow(): Flow<ProblemEvent> {
    return problemEvents.onStart {
      val allProblems = synchronized(fileProblems) { fileProblems.values.flatten() } +
                        synchronized(otherProblems) { otherProblems.toList() }

      allProblems.forEach { problem ->
        emit(ProblemEvent.Appeared(problem))
      }
    }
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
          // do not add HighlightingDuplicate if there is any highlighting problem
          problem is HighlightingDuplicateProblem && set.any { it is HighlightingBaseProblem } -> SetUpdateState.IGNORED
          else -> SetUpdateState.add(problem, set)
        }
      }
      else -> synchronized(otherProblems) { SetUpdateState.add(problem, otherProblems) }
    })
    if (!ignored && problem is FileProblem && problem is HighlightingBaseProblem) {
      synchronized(fileProblems) {
        fileProblems[problem.file]?.filterIsInstance<HighlightingDuplicateProblem>()
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

  private fun isIgnored(provider: ProblemsProvider): Boolean =
    provider.project != project || providerClassFilter.contains(provider.javaClass.name)

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
      return
    }
    when (state) {
      SetUpdateState.ADDED -> {
        project.messageBus.syncPublisher(ProblemsListener.TOPIC).problemAppeared(problem)
        emitProblemAppeared(problem)
        problemCount.getAndIncrement()
      }
      SetUpdateState.REMOVED -> {
        project.messageBus.syncPublisher(ProblemsListener.TOPIC).problemDisappeared(problem)
        emitProblemDisappeared(problem)
        problemCount.decrementAndGet()
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
    problemEvents.tryEmit(ProblemEvent.Appeared(problem))
  }

  private fun emitProblemDisappeared(problem: Problem) {
    problemEvents.tryEmit(ProblemEvent.Disappeared(problem))
  }

  private fun emitProblemUpdated(problem: Problem) {
    problemEvents.tryEmit(ProblemEvent.Updated(problem))
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
}
