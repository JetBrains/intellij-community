// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.diagnostic.ThreadDumper.dumpThreadsToString
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.observation.Observation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.concurrency.waitForPromiseAndPumpEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.concurrency.asPromise
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object TestObservation {

  @JvmStatic
  fun waitForConfiguration(project: Project, timeout: Long = DEFAULT_TEST_TIMEOUT.inWholeMilliseconds) {
    val coroutineScope = CoroutineScopeService.getCoroutineScope(project)
    val job = coroutineScope.launch {
      awaitConfiguration(project, timeout.milliseconds)
    }
    val promise = job.asPromise()
    promise.waitForPromiseAndPumpEdt(Duration.INFINITE)
  }

  suspend fun awaitConfiguration(project: Project, timeout: Duration = DEFAULT_TEST_TIMEOUT) {
    val operationLog = StringJoiner("\n")
    try {
      withTimeout(timeout) {
        Observation.awaitConfiguration(project, operationLog::add)
      }
    }
    catch (_: TimeoutCancellationException) {
      val activityDump = Observation.dumpAwaitedActivitiesToString()
      val uncommitedDocuments = dumpUncommitedDocumentsWithTracesToString(project)
      val coroutineDump = dumpCoroutines()
      val threadDump = dumpThreadsToString()

      System.err.println("""
        |The waiting takes too long. Expected to take no more than $timeout.
        |------ Operation log begin ------
        |$operationLog
        |------- Operation log end -------
        |--- Uncommited documents begin --
        |$uncommitedDocuments
        |---- Uncommited documents end ---
        |------ Activity dump begin ------
        |$activityDump
        |------- Activity dump end -------
        |------- Thread dump begin -------
        |$threadDump
        |-------- Thread dump end --------
        |------ Coroutine dump begin -----
        |$coroutineDump
        |------- Coroutine dump end ------
      """.trimMargin())
      throw AssertionError("The waiting takes too long. Expected to take no more than $timeout.")
    }
  }

  private fun dumpUncommitedDocumentsWithTracesToString(project: Project): String {
    if (!Registry.`is`("ide.activity.tracking.enable.debug")) {
      return "Enable 'ide.activity.tracking.enable.debug' registry option to collect uncommited document traces"
    }
    val psiDocumentManager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
    return psiDocumentManager.uncommitedDocumentsWithTraces.entries
      .joinToString("\n") {
        it.key.toString() + ": " + it.value.stackTraceToString()
      }
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(private val coroutineScope: CoroutineScope) {
    companion object {
      fun getCoroutineScope(project: Project): CoroutineScope {
        return project.service<CoroutineScopeService>().coroutineScope
      }
    }
  }
}