// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.plugins.gitlab.authentication.CustomServer

internal class GitLabCustomServersViewModel(initial: List<CustomServer>) {
  private val _servers = MutableStateFlow(initial)
  val servers: StateFlow<List<CustomServer>> = _servers.asStateFlow()

  fun add(server: CustomServer) {
    _servers.update { it + server }
  }

  fun replace(index: Int, server: CustomServer) {
    _servers.update { servers -> servers.toMutableList().also { it[index] = server } }
  }

  fun remove(index: Int) {
    _servers.update { servers -> servers.toMutableList().also { it.removeAt(index) } }
  }

  fun reset(servers: List<CustomServer>) {
    _servers.value = servers
  }
}
