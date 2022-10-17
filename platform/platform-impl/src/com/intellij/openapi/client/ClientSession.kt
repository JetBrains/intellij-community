// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.util.messages.MessageBus
import org.jetbrains.annotations.ApiStatus


/**
 * An object associated with each participant working inside an IDE.
 * There's always a session of a local owner of the IDE that lives as long as [Application]/[Project] itself,
 * in case of Code With Me there also can be other client sessions corresponding to the joined guests,
 * in case of Gateway there also can be the client working with the IDE remotely.
 *
 * Manage services registered with <applicationService client="..."/> and <projectService client="..."/>.
 *
 * One can create per-client services when it's needed to
 * 1) alter the behavior between the local, controller, and guests
 * 2) to have the data kept on a per-client basis
 *
 * Getting a service with [Application.getService]/[Project.getService] will search through per-client services of the current [ClientId],
 * there's also [Application.getServices]/[Project.getServices] for getting all services, that should be enough for simple cases.
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
interface ClientAppSession : ClientSession

/**
 * Project level [ClientSession]
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface ClientProjectSession : ClientSession {
  val project: ProjectEx
  val appSession: ClientAppSession
}
