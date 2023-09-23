// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ClientSessionsUtil")

package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.util.application

/**
 * Executes given action for each client connected to all projects opened in IDE
 */
inline fun Application.forEachSession(kind: ClientKind, action: (ClientAppSession) -> Unit) {
  for (session in this.service<ClientSessionsManager<*>>().getSessions(kind)) {
    ClientId.withClientId(session.clientId) {
      logger<ClientSessionsManager<*>>().runAndLogException {
        action(session as ClientAppSession)
      }
    }
  }
}

/**
 * Executes given action for each client connected to this [Project]
 */
inline fun Project.forEachSession(kind: ClientKind, action: (ClientProjectSession) -> Unit) {
  @Suppress("UNCHECKED_CAST")
  for (session in this.service<ClientSessionsManager<*>>().getSessions(kind) as List<ClientProjectSession>) {
    ClientId.withClientId(session.clientId) {
      logger<ClientSessionsManager<*>>().runAndLogException {
        action(session)
      }
    }
  }
}

val Application.currentSession: ClientAppSession
  get() = this.currentSessionOrNull ?: error("Application-level session is not set. ${ClientId.current}")

val Application.currentSessionOrNull: ClientAppSession?
  get() = ClientSessionsManager.getAppSession()

val Project.currentSessionOrNull: ClientProjectSession?
  get() = ClientSessionsManager.getProjectSession(this)

val Project.currentSession: ClientProjectSession
  get() = currentSessionOrNull ?: error("Project-level session is not set. ${ClientId.current}")


@Deprecated("use get app-level session from application", ReplaceWith("application.currentSession", "com.intellij.util.application"))
val currentSession: ClientAppSession get() = application.currentSession

@Deprecated("use get app-level session from application", ReplaceWith("application.currentSessionOrNull", "com.intellij.util.application"))
val currentSessionOrNull: ClientAppSession?
  get() = application.currentSessionOrNull
