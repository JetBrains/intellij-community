// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import org.jetbrains.annotations.ApiStatus


/**
 * An object associated with each participant working inside an IDE.
 * There's always a session of a local owner of the IDE that lives as long as [Application]/[Project] itself,
 * in case of Code With Me there also can be other client sessions corresponding to the joined guests.
 *
 * Manage services registered with <applicationService client="..."/> and <projectService client="..."/>.
 *
 * One can create per-client services when it's needed to
 * 1) alter the behavior between the local owner and guests
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
  val isLocal: Boolean
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
