// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.openapi.vcs.FilePath
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
sealed interface InclusionDto {
  @Serializable
  data class Change(val changeId: ChangeId) : InclusionDto
  @Serializable
  data class File(val path: FilePathDto) : InclusionDto

  companion object {
    fun toDto(inclusionObject: Any): InclusionDto = when (inclusionObject) {
      is com.intellij.openapi.vcs.changes.Change -> Change(ChangeId.getId(inclusionObject))
      is FilePath -> File(FilePathDto.toDto(inclusionObject))
      else -> error("${inclusionObject.javaClass} can't be converted to InclusionDto")
    }
  }
}