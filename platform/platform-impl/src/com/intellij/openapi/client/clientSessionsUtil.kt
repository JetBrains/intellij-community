// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project

/**
 * Executes given action for each client connected to all projects opened in IDE
 */
fun forEachClient(kind: ClientKind, action: (ClientAppSession) -> Unit) {
  for (session in ClientSessionsManager.getAppSessions(kind)) {
    withClientId(session.clientId) {
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
    withClientId(session.clientId) {
      logger<ClientSessionsManager<*>>().runAndLogException {
        action(session)
      }
    }
  }
}

val currentSession: ClientAppSession get() = ClientSessionsManager.getAppSession()!!

val Project.currentSession: ClientProjectSession get() = ClientSessionsManager.getProjectSession(this)!!

// region Deprecated
@Deprecated("Use forEachClient")
fun broadcastAllClients(includeLocal: Boolean = false, action: () -> Unit) {
  forEachClient(if (includeLocal) ClientKind.ALL else ClientKind.REMOTE) { action() }
}

@Deprecated("Use project.forEachClient")
fun broadcastAllClients(project: Project, includeLocal: Boolean = false, action: () -> Unit) {
  project.forEachClient(if (includeLocal) ClientKind.ALL else ClientKind.REMOTE) { action() }
}

// endregion