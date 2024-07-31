// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.roots.libraries.Library
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap


/**
 * @return corresponding [LibraryEntity] or null if library isn't associated with entity yet
 */
fun Library.findLibraryEntity(entityStorage: EntityStorage): LibraryEntity? {
  return entityStorage.libraryMap.getEntities(this as LibraryBridge).firstOrNull() as LibraryEntity?
}