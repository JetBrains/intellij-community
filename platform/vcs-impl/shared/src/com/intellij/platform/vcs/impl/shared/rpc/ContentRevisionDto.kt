// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
data class ContentRevisionDto(
  val revisionString: String,
  val filePath: FilePathDto,
  @Transient private val localValue: ContentRevision? = null,
) {
  val contentRevision: ContentRevision by lazy {
    localValue ?: FrontendContentRevision(
      revision = TextRevisionNumber(revisionString),
      filePath = filePath.filePath,
    )
  }
}

private class FrontendContentRevision(
  val revision: TextRevisionNumber,
  val filePath: FilePath,
) : ContentRevision {
  override fun getContent(): String? = null
  override fun getFile(): FilePath = filePath
  override fun getRevisionNumber() = revision
}