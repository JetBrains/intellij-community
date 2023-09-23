// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.messages.MessageBus
import org.jetbrains.annotations.ApiStatus


/**
 * An object associated with each participant working inside an IDE.
 *
 * There's always the session of the local owner of the IDE, this session lives as long as the [Application] or [Project] itself.
 * In case of Code With Me, there can also be other client sessions corresponding to the joined guests.
 * In case of Gateway, there can also be the client working with the IDE remotely.
 *
 * Manage services registered with `<applicationService client="..."/>` and `<projectService client="..."/>`.
 *
 * One can create per-client services when it's needed to
 * 1) alter the behavior between the local, controller, and guests
 * 2) to have the data kept on a per-client basis
 *
 * Getting a service with [Application.getService] or [Project.getService] searches through per-client services of the current [ClientId],
 * there's also [Application.getServices] or [Project.getServices] for getting all services, that should be enough for simple cases.
 * If you need more control over client sessions or per-client services, take a look at the API of [ClientSessionsManager].
 *
 * Avoid exposing [ClientSession] and its inheritors in the public API.
 * Use sessions and per-client services internally in your code instead of relaying on [ClientId] implicitly stored in the context of execution.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface ClientSession : ComponentManager {
  val clientId: ClientId
  val type: ClientType
  val name: @NlsSafe String

  @Deprecated("sessions don't have their own message bus", level = DeprecationLevel.ERROR)
  override fun getMessageBus(): MessageBus {
    error("Not supported")
  }

  val isLocal: Boolean get() = type.isLocal
  val isController: Boolean get() = type.isController
  val isGuest: Boolean get() = type.isGuest
  val isOwner: Boolean get() = type.isOwner
  val isRemote: Boolean get() = type.isRemote
}

/**
 * Application level [ClientSession]
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface ClientAppSession : ClientSession {
  /**
   * Clients may have access only to some of the projects.
   * Currently, there's a limitation in Code With Me (CWM-2149) because of the complexity with permissions and calls.
   *
   * For remote-dev having several projects, or not having one open is natural same as in a local setup
   */
  val projectSessions: List<ClientProjectSession>

  fun getProjectSession(project: Project): ClientProjectSession? {
    return projectSessions.find { it.project == project }
  }
}

/**
 * Project level [ClientSession]
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface ClientProjectSession : ClientSession {
  /**
   * Project this session belongs to. Unlike sessions there's only one project,
   * independently of how many active users is operating with it.
   */
  val project: Project

  /**
   * Some features live on app-level (e.g., UI, actions, popups) this allows to get from project-level to app-level.
   * It's recommended to get session instead on manual [ClientId] manipulation
   */
  val appSession: ClientAppSession
}
