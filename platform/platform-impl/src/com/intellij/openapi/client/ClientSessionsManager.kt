// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Experimental
@ApiStatus.Internal
sealed class ClientSessionsManager<T : ClientSession> {
  companion object {
    /**
     * Returns an application-level session for a particular client.
     * @see ClientSession
     */
    @JvmStatic
    @JvmOverloads
    fun getAppSession(clientId: ClientId = ClientId.current): ClientAppSession? {
      return ApplicationManager.getApplication().service<ClientSessionsManager<ClientAppSession>>().getSession(clientId)
    }

    /**
     * Returns all application-level sessions.
     * @param kind specifies what sessions should be included
     * @see ClientSession
     */
    @JvmStatic
    fun getAppSessions(kind: ClientKind): List<ClientAppSession> {
      return ApplicationManager.getApplication().service<ClientSessionsManager<ClientAppSession>>().getSessions(kind)
    }

    /**
     * Returns a project-level session for a particular client.
     * @see ClientProjectSession
     */
    @JvmStatic
    @JvmOverloads
    fun getProjectSession(project: Project, clientId: ClientId = ClientId.current): ClientProjectSession? {
      return project.service<ClientSessionsManager<ClientProjectSession>>().getSession(clientId)
    }

    /**
     * Returns all project-level sessions.
     * @param kind specifies what sessions should be included
     * @see ClientSession
     */
    @JvmStatic
    fun getProjectSessions(project: Project, kind: ClientKind): List<ClientProjectSession> {
      return project.service<ClientSessionsManager<ClientProjectSession>>().getSessions(kind)
    }

    /**
     * @return a project session for specified app-level session
     */
    @JvmStatic
    @Deprecated("Use projectSessions list from app session",
                ReplaceWith("session.projectSessions.find { it.project == project }"),
                DeprecationLevel.ERROR)
    fun getProjectSession(project: Project, session: ClientAppSession): ClientProjectSession? {
      return session.projectSessions.find { it.project == project }
    }

    /**
     * Clients may not have access to certain projects
     * @return a list of project-level sessions available to a certain [ClientAppSession]
     */
    @JvmStatic
    @Deprecated("Use projectSessions list from app session",
                ReplaceWith("session.projectSessions"),
                DeprecationLevel.ERROR)
    fun getAllProjectSession(session: ClientAppSession): List<ClientProjectSession> {
      return session.projectSessions
    }

    @ApiStatus.ScheduledForRemoval
    @JvmStatic
    @Deprecated("Use overload accepting ClientKind",
                ReplaceWith("getProjectSessions(project, if (includeLocal) ClientKind.ALL else ClientKind.REMOTE)",
                            "com.intellij.openapi.client.ClientSessionsManager.Companion.getProjectSessions"),
                DeprecationLevel.ERROR)
    fun getProjectSessions(project: Project, includeLocal: Boolean): List<ClientProjectSession> {
      return getProjectSessions(project, if (includeLocal) ClientKind.ALL else ClientKind.REMOTE)
    }

    @Deprecated("Use overload accepting ClientKind",
                ReplaceWith("getAppSessions(if (includeLocal) ClientKind.ALL else ClientKind.REMOTE)",
                            "com.intellij.openapi.client.ClientSessionsManager.Companion.getAppSessions"),
                DeprecationLevel.ERROR)
    @JvmStatic
    fun getAppSessions(includeLocal: Boolean): List<ClientAppSession> {
      return getAppSessions(if (includeLocal) ClientKind.ALL else ClientKind.REMOTE)
    }

    @Deprecated("Use application.currentSession, application.forEachSession")
    @ApiStatus.Internal
    fun getInstance(): ClientAppSessionsManager = service<ClientSessionsManager<*>>() as ClientAppSessionsManager

    @Deprecated("Use project.currentSession, project.forEachSession")
    @ApiStatus.Internal
    fun getInstance(project: Project): ClientProjectSessionsManager = project.service<ClientSessionsManager<*>>() as ClientProjectSessionsManager
  }

  private val sessions = ConcurrentHashMap<ClientId, T>()

  @Deprecated("Use overload accepting ClientKind",
              ReplaceWith("getSessions(if (includeLocal) ClientKind.ALL else ClientKind.REMOTE)"),
              DeprecationLevel.ERROR)
  fun getSessions(includeLocal: Boolean): List<T> {
    return getSessions(if (includeLocal) ClientKind.ALL else ClientKind.REMOTE)
  }

  fun getSessions(kind: ClientKind): List<T> {
    if (kind == ClientKind.ALL) {
      return java.util.ArrayList(sessions.values)
    }
    else {
      return sessions.values.filter { it.type.matches(kind) }
    }
  }

  fun getSession(clientId: ClientId): T? {
    return sessions[clientId]
  }

  fun registerSession(disposable: Disposable, session: T) {
    val clientId = session.clientId
    if (sessions.putIfAbsent(clientId, session) != null) {
      logger<ClientSessionsManager<*>>().error("Session with '$clientId' is already registered")
    }
    Disposer.register(disposable, session)
    Disposer.register(disposable) {
      sessions.remove(clientId)
    }
  }
}