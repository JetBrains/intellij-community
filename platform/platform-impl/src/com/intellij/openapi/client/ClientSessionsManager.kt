// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Experimental
@ApiStatus.Internal
sealed class ClientSessionsManager<T : ClientSession> {
  companion object {
    /**
     * Returns a project-level session for a particular client.
     * @see ClientSession
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
     * @param includeLocal specifies whether the local session should be included
     * @see ClientSession
     */
    @JvmStatic
    fun getProjectSessions(project: Project, includeLocal: Boolean): List<ClientProjectSession> {
      return getInstance(project).getSessions(includeLocal)
    }

    /**
     * Returns all application-level sessions.
     * @param includeLocal specifies whether the local session should be included
     * @see ClientSession
     */
    @JvmStatic
    fun getAppSessions(includeLocal: Boolean): List<ClientAppSession> {
      return getInstance().getSessions(includeLocal)
    }

    @ApiStatus.Internal
    fun getInstance() = service<ClientSessionsManager<*>>() as ClientAppSessionsManager

    @ApiStatus.Internal
    fun getInstance(project: Project) = project.service<ClientSessionsManager<*>>() as ClientProjectSessionsManager
  }

  private val sessions = ConcurrentHashMap<ClientId, T>()

  fun getSessions(includeLocal: Boolean): List<T> {
    if (includeLocal) {
      return java.util.List.copyOf(sessions.values)
    }
    else {
      return sessions.values.filter { !it.isLocal }
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

open class ClientAppSessionsManager : ClientSessionsManager<ClientAppSession>() {
  init {
    val application = ApplicationManager.getApplication()
    if (application is ApplicationImpl) {
      @Suppress("LeakingThis")
      registerSession(application, createLocalSession(application))
    }
  }

  /**
   * Used for [ClientId] overriding in JetBrains Client
   */
  protected open fun createLocalSession(application: ApplicationImpl): ClientAppSessionImpl {
    return ClientAppSessionImpl(ClientId.localId, application)
  }
}

open class ClientProjectSessionsManager(project: Project) : ClientSessionsManager<ClientProjectSession>() {
  init {
    if (project is ProjectImpl) {
      registerSession(project, ClientProjectSessionImpl(ClientId.localId, project))
    }
  }
}