// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.libraries.libraries

import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.workspace.api.TypedEntityStorageBuilder
import org.jetbrains.annotations.ApiStatus

interface LegacyBridgeProjectLibraryTable: ProjectLibraryTable {
  @ApiStatus.Internal
  fun getModifiableModel(diff: TypedEntityStorageBuilder): LibraryTable.ModifiableModel
}