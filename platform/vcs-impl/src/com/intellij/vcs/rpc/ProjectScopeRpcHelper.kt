// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.rpc

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.messages.SimpleMessageBusConnection
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus

/**
 * RPC calls are handled in the application scope, which can result in exceptions thrown while interacting
 * with a project that can be disposed at any moment.
 * These errors are not relevant to the "prod" execution; however, they are producing randomly flaking tests.
 * While it's possible to delicately interact with the project in read actions adding excessive disposal checks,
 * it's much easier to simply ignore exceptions.
 *
 * Should be removed once a project-scoped RPC is implemented.
 * See [IJPL-202832](https://youtrack.jetbrains.com/issue/IJPL-202832)
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
object ProjectScopeRpcHelper {
  private val LOG = Logger.getInstance(ProjectScopeRpcHelper::class.java)

  suspend fun projectScoped(projectId: ProjectId, action: suspend (project: Project) -> Unit): Unit =
    getProjectScoped(projectId, action) ?: Unit

  /**
   * Note that connection is not closed automatically when encountering [AlreadyDisposedException] in [producerBuilder].
   * It's expected to be closed by the client.
   */
  suspend fun <T> projectScopedCallbackFlow(
    projectId: ProjectId,
    producerBuilder: suspend ProducerScope<T>.(project: Project, projectMessageBus: SimpleMessageBusConnection) -> Unit,
  ): Flow<T> = getProjectScoped(projectId) { project ->
    callbackFlow {
      var messageBusConnection: SimpleMessageBusConnection? = null
      try {
        messageBusConnection = project.messageBus.simpleConnect()
        producerBuilder(project, messageBusConnection)
      }
      catch (@Suppress("IncorrectCancellationExceptionHandling") _: AlreadyDisposedException) {
        LOG.debug("Project $project was disposed while building callback flow, waiting for client to cancel")
      }

      awaitClose {
        LOG.trace { "Connection closed" }
        messageBusConnection?.disconnect()
      }
    }
  } ?: emptyFlow()

  suspend fun <T : Any> getProjectScoped(projectId: ProjectId, action: suspend (project: Project) -> T?): T? {
    val projectOrNull = projectId.findProjectOrNull()
    if (projectOrNull == null) {
      LOG.debug { "Project $projectId was not found" }
    }
    if (projectOrNull?.isDisposed ?: false) {
      LOG.debug { "Project $projectId already disposed" }
    }

    val project = projectOrNull ?: return null
    return try {
      action(project)
    }
    catch (@Suppress("IncorrectCancellationExceptionHandling") _: AlreadyDisposedException) {
      LOG.debug { "Project $project was disposed while evaluating return value" }
      null
    }
  }
}