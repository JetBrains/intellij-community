// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class ShowCommitCheckTodosRequest(val title: @NlsContexts.TabTitle String, val fileIds: List<VirtualFileId>)

@ApiStatus.Internal
val SHOW_COMMIT_CHECK_TODOS_REMOTE_TOPIC: ProjectRemoteTopic<ShowCommitCheckTodosRequest> =
  ProjectRemoteTopic("todo.commitCheck.show", ShowCommitCheckTodosRequest.serializer())