// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow.splitApi

import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.vfs.VirtualFileId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed class ProblemDto {
  abstract val id: String
}

@ApiStatus.Internal
@Serializable
data class GenericProblemDto(
  override val id: String,
  val text: String,
  val description: String,
  val icon: IconId?
) : ProblemDto()

@ApiStatus.Internal
@Serializable
data class FileProblemDto(
  override val id: String,
  val text: String,
  val description: String,
  val icon: IconId?,
  val filePath: String,
  val fileId: VirtualFileId,
  val line: Int,
  val column: Int
) : ProblemDto()

@ApiStatus.Internal
@Serializable
sealed class ProblemEventDto {
  @Serializable
  data class Appeared(val problemDto: ProblemDto) : ProblemEventDto()

  @Serializable
  data class Disappeared(val problemId: String) : ProblemEventDto()

  @Serializable
  data class Updated(val problemDto: ProblemDto) : ProblemEventDto()
}
