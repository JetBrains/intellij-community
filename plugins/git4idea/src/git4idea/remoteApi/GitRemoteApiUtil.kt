// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.dvcs.repo.Repository
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.util.messages.MessageBusConnection
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun Repository.rpcId(): RepositoryId = RepositoryId(projectId = project.projectId(), rootPath = root.rpcId())

internal fun <T> flowWithMessageBus(
  project: Project,
  operation: suspend ProducerScope<T>.(connection: MessageBusConnection) -> Unit,
): Flow<T> = callbackFlow<T> {
  val connection = project.messageBus.connect()
  operation(connection)
  awaitClose { connection.disconnect() }
}
