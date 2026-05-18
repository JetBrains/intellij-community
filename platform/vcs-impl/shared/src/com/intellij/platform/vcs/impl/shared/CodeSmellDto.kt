// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class CodeSmellDto(
  val description: String,
  val filePath: String,
  val fileId: VirtualFileId,
  val line: Int,
  val column: Int,
  val severityName: String,
  val severityValue: Int
)

@ApiStatus.Internal
@Serializable
data class ShowCodeSmellRequest(val smells: List<CodeSmellDto>)

@ApiStatus.Internal
val CODE_SMELL_REMOTE_TOPIC: ProjectRemoteTopic<ShowCodeSmellRequest> = ProjectRemoteTopic("vcs.codeSmell.show", ShowCodeSmellRequest.serializer())
