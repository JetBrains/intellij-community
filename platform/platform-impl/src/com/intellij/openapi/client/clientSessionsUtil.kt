// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project

/**
 * Executes given action for each client connected to all projects opened in IDE
 * @param includeLocal pass true to execute the action for [com.intellij.codeWithMe.ClientId.localId]
 */
fun broadcastAllClients(includeLocal: Boolean = false, action: () -> Unit) {
  for (session in ClientSessionsManager.getInstance().getSessions(includeLocal)) {
    withClientId(session.clientId) {
      logger<ClientSessionsManager<*>>().runAndLogException(action)
    }
  }
}

/**
 * Executes given action for each client connected to this [project]
 * @param includeLocal pass true to execute the action for [com.intellij.codeWithMe.ClientId.localId]
 */
fun broadcastAllClients(project: Project, includeLocal: Boolean = false, action: () -> Unit) {
  for (session in ClientSessionsManager.getInstance(project).getSessions(includeLocal)) {
    withClientId(session.clientId) {
      logger<ClientSessionsManager<*>>().runAndLogException(action)
    }
  }
}

val currentSession: ClientAppSession get() = ClientSessionsManager.getAppSession()!!

val Project.currentSession: ClientProjectSession get() = ClientSessionsManager.getProjectSession(this)!!