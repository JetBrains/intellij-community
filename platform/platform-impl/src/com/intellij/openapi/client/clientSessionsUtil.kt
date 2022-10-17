// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project

@Deprecated("Use overload accepting ClientKind")
fun broadcastAllClients(includeLocal: Boolean = false, action: () -> Unit) {
  broadcastAllClients(if (includeLocal) ClientKind.ALL else ClientKind.REMOTE, action)
}

/**
 * Executes given action for each client connected to all projects opened in IDE
 */
fun broadcastAllClients(kind: ClientKind, action: () -> Unit) {
  for (session in ClientSessionsManager.getAppSessions(kind)) {
    withClientId(session.clientId) {
      logger<ClientSessionsManager<*>>().runAndLogException(action)
    }
  }
}

@Deprecated("Use overload accepting ClientKind")
fun broadcastAllClients(project: Project, includeLocal: Boolean = false, action: () -> Unit) {
  broadcastAllClients(project, if (includeLocal) ClientKind.ALL else ClientKind.REMOTE, action)
}

/**
 * Executes given action for each client connected to this [project]
 */
fun broadcastAllClients(project: Project, kind: ClientKind, action: () -> Unit) {
  for (session in ClientSessionsManager.getProjectSessions(project, kind)) {
    withClientId(session.clientId) {
      logger<ClientSessionsManager<*>>().runAndLogException(action)
    }
  }
}

val currentSession: ClientAppSession get() = ClientSessionsManager.getAppSession()!!

val Project.currentSession: ClientProjectSession get() = ClientSessionsManager.getProjectSession(this)!!