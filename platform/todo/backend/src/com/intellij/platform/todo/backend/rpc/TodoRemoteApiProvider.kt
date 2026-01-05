// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.todo.backend.rpc


import com.intellij.ide.todo.rpc.TodoRemoteApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class TodoRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<TodoRemoteApi>()) { TodoRemoteApiImpl() }
  }
}