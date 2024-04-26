// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectImpl
import org.jetbrains.annotations.ApiStatus.Internal


@Suppress("NonDefaultConstructor")
@Internal
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

@Internal
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
