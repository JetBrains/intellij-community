// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl

import com.intellij.platform.workspace.jps.bridge.impl.library.JpsLibraryCollectionsCache
import com.intellij.platform.workspace.jps.bridge.impl.library.JpsLibraryReferenceBridge
import com.intellij.platform.workspace.jps.bridge.impl.module.JpsModuleReferenceBridge
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleId
import org.jetbrains.jps.model.JpsCompositeElement
import org.jetbrains.jps.model.JpsElementReference
import org.jetbrains.jps.model.ex.JpsReferenceCustomFactory
import org.jetbrains.jps.model.impl.JpsGlobalElementReference
import org.jetbrains.jps.model.impl.JpsProjectElementReference
import org.jetbrains.jps.model.library.JpsLibraryReference
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.model.serialization.impl.JpsSerializationViaWorkspaceModel

internal class JpsReferenceCustomFactoryImpl : JpsReferenceCustomFactory {
  override fun isEnabled(): Boolean = JpsSerializationViaWorkspaceModel.IS_ENABLED

  override fun createModuleReference(moduleName: String): JpsModuleReference {
    return JpsModuleReferenceBridge(moduleName)
  }

  override fun createLibraryReference(libraryName: String, parentReference: JpsElementReference<out JpsCompositeElement>): JpsLibraryReference {
    val tableId = when (parentReference) {
      is JpsGlobalElementReference -> JpsLibraryCollectionsCache.GLOBAL_LIBRARY_TABLE_ID
      is JpsProjectElementReference -> LibraryTableId.ProjectLibraryTableId
      is JpsModuleReference -> LibraryTableId.ModuleLibraryTableId(ModuleId(parentReference.moduleName))
      else -> throw UnsupportedOperationException("Reference to library in $parentReference is not supported") //todo support custom library tables
    }
    return JpsLibraryReferenceBridge(LibraryId(libraryName, tableId))
  }
}
