// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.rd

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
interface ThinClientVcsContextFactory: VcsContextFactory {
  fun createFilePathOn(fileName: @NonNls String, isDirectory: Boolean, virtualFileId: VirtualFileId?): FilePath
}