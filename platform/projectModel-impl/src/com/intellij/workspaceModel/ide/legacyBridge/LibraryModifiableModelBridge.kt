// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.platform.workspace.jps.entities.LibraryId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface LibraryModifiableModelBridge : LibraryEx.ModifiableModelEx, LibraryEx {
  fun prepareForCommit()
  val libraryId: LibraryId
}