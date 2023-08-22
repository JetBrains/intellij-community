// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Executes given action for each client connected to all projects opened in IDE
 */
fun forEachClient(kind: ClientKind, action: (ClientAppSession) -> Unit) {
  for (session in ClientSessionsManager.getAppSessions(kind)) {
    ClientId.withClientId(session.clientId) {
      logger<ClientSessionsManager<*>>().runAndLogException {
        action(session)
      }
    }
  }
}

/**
 * Executes given action for each client connected to this [Project]
 */
fun Project.forEachClient(kind: ClientKind, action: (ClientProjectSession) -> Unit) {
  for (session in ClientSessionsManager.getProjectSessions(this, kind)) {
    ClientId.withClientId(session.clientId) {
      logger<ClientSessionsManager<*>>().runAndLogException {
        action(session)
      }
    }
  }
}

val currentSessionOrNull: ClientAppSession?
  get() = ClientSessionsManager.getAppSession()

val currentSession: ClientAppSession
  get() = currentSessionOrNull ?: error("Application-level session is not set")

val Project.currentSessionOrNull: ClientProjectSession?
  get() = ClientSessionsManager.getProjectSession(this)

val Project.currentSession: ClientProjectSession
  get() = currentSessionOrNull ?: error("Project-level session is not set")

// region Deprecated
@ApiStatus.ScheduledForRemoval
@Deprecated("Use forEachClient")
fun broadcastAllClients(includeLocal: Boolean = false, action: () -> Unit) {
  forEachClient(if (includeLocal) ClientKind.ALL else ClientKind.REMOTE) { action() }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use project.forEachClient")
fun broadcastAllClients(project: Project, includeLocal: Boolean = false, action: () -> Unit) {
  project.forEachClient(if (includeLocal) ClientKind.ALL else ClientKind.REMOTE) { action() }
}

// endregion