// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus

interface ProjectLibraryTableBridge : ProjectLibraryTable {
  @ApiStatus.Internal
  fun getModifiableModel(diff: MutableEntityStorage): LibraryTable.ModifiableModel
}