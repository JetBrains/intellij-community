// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Experimental
@ApiStatus.Internal
sealed class ClientSessionsManager<T : ClientSession> {
  companion object {

    /**
     * @return a project session for specified app-level session
     */
    @JvmStatic
    fun getProjectSession(project: Project, session: ClientAppSession): ClientProjectSession? {
      return getInstance(project).getSession(session.clientId)
    }

    /**
     * Clients may not have access to certain projects
     * @return a list of project-level sessions available to a certain [ClientAppSession]
     */
    fun getAllProjectSession(session: ClientAppSession): List<ClientProjectSession> {
      return ProjectManager.getInstance().openProjects.mapNotNull { getProjectSession(it, session) }
    }

    /**
     * Returns a project-level session for a particular client.
     * @see ClientProjectSession
     */
    @JvmStatic
    @JvmOverloads
    fun getProjectSession(project: Project, clientId: ClientId = ClientId.current): ClientProjectSession? {
      return getInstance(project).getSession(clientId)
    }

    /**
     * Returns an application-level session for a particular client.
     * @see ClientSession
     */
    @JvmStatic
    @JvmOverloads
    fun getAppSession(clientId: ClientId = ClientId.current): ClientAppSession? {
      return getInstance().getSession(clientId)
    }

    /**
     * Returns all project-level sessions.
     * @param kind specifies what sessions should be included
     * @see ClientSession
     */
    @JvmStatic
    fun getProjectSessions(project: Project, kind: ClientKind): List<ClientProjectSession> {
      return getInstance(project).getSessions(kind)
    }

    @ApiStatus.ScheduledForRemoval
    @JvmStatic
    @Deprecated("Use overload accepting ClientKind")
    fun getProjectSessions(project: Project, includeLocal: Boolean): List<ClientProjectSession> {
      return getProjectSessions(project, if (includeLocal) ClientKind.ALL else ClientKind.REMOTE)
    }

    /**
     * Returns all application-level sessions.
     * @param kind specifies what sessions should be included
     * @see ClientSession
     */
    @JvmStatic
    fun getAppSessions(kind: ClientKind): List<ClientAppSession> {
      return getInstance().getSessions(kind)
    }

    @Deprecated("Use overload accepting ClientKind")
    @JvmStatic
    fun getAppSessions(includeLocal: Boolean): List<ClientAppSession> {
      return getAppSessions(if (includeLocal) ClientKind.ALL else ClientKind.REMOTE)
    }

    @ApiStatus.Internal
    fun getInstance(): ClientAppSessionsManager = service<ClientSessionsManager<*>>() as ClientAppSessionsManager

    @ApiStatus.Internal
    fun getInstance(project: Project): ClientProjectSessionsManager = project.service<ClientSessionsManager<*>>() as ClientProjectSessionsManager
  }

  private val sessions = ConcurrentHashMap<ClientId, T>()

  @Deprecated("Use overload accepting ClientKind")
  fun getSessions(includeLocal: Boolean): List<T> {
    return getSessions(if (includeLocal) ClientKind.ALL else ClientKind.REMOTE)
  }

  fun getSessions(kind: ClientKind): List<T> {
    if (kind == ClientKind.ALL) {
      return java.util.List.copyOf(sessions.values)
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

@Suppress("NonDefaultConstructor")
open class ClientAppSessionsManager(application: Application) : ClientSessionsManager<ClientAppSession>() {
  init {
    @Suppress("LeakingThis")
    registerLocalSession(application)
  }

  /**
   * Used for [ClientId] overriding in JetBrains Client
   */
  protected open fun registerLocalSession(application: Application) {
    if (application is ApplicationImpl) {
      registerSession(application, LocalAppSessionImpl(application))
    }
  }
}

open class ClientProjectSessionsManager(project: Project) : ClientSessionsManager<ClientProjectSession>() {
  init {
    @Suppress("LeakingThis")
    registerLocalSession(project)
  }

  protected open fun registerLocalSession(project: Project) {
    if (project is ProjectImpl) {
      registerSession(project, LocalProjectSessionImpl(project))
    }
    else if (project.isDefault) {
      (project.actualComponentManager as? ClientAwareComponentManager)?.let { componentManager ->
        registerSession(project, LocalProjectSessionImpl(componentManager, project))
      }
    }
  }
}