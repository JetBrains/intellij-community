// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ChangeListDnDSupport {
  fun moveChangesTo(list: LocalChangeList, changes: List<Change>)
  fun addUnversionedFiles(list: LocalChangeList, unversionedFiles: List<FilePath>)
}