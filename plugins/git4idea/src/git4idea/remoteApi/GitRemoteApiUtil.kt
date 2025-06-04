// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.util.messages.SimpleMessageBusConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal suspend fun <T> flowWithMessageBus(
  project: Project,
  scope: CoroutineScope,
  operation: suspend ProducerScope<T>.(connection: SimpleMessageBusConnection) -> Unit,
): Flow<T> {
  val connection = readAction {
    if (!scope.isActive || project.isDisposed) null else project.messageBus.connect(scope)
  } ?: return emptyFlow()

  return callbackFlow {
    scope.launch {
      operation(connection)
    }
    awaitClose { connection.disconnect() }
  }
}
