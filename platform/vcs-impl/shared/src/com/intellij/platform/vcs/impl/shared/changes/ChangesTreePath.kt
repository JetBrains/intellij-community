// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class ChangesTreePath(
  val filePath: FilePathDto,
  val changeId: ChangeId?,
) {
  companion object {
    fun create(userObject: Any): ChangesTreePath? {
      val filePath = VcsTreeModelData.mapUserObjectToFilePath(userObject) ?: return null
      return ChangesTreePath(
        filePath = FilePathDto.toDto(filePath),
        changeId = (userObject as? Change)?.let { ChangeId.getId(it) },
      )
    }
  }
}