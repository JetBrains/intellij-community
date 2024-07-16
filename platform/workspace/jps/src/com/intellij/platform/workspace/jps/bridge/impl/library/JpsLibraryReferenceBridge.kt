// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.library

import com.intellij.platform.workspace.jps.bridge.impl.module.JpsModuleReferenceBridge
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import org.jetbrains.jps.model.JpsCompositeElement
import org.jetbrains.jps.model.JpsElementReference
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.impl.JpsGlobalElementReference
import org.jetbrains.jps.model.impl.JpsProjectElementReference
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsLibraryReference
import org.jetbrains.jps.model.module.JpsModule

internal class JpsLibraryReferenceBridge(private val libraryId: LibraryId) 
  : JpsElementBase<JpsLibraryReferenceBridge>(), JpsLibraryReference {
  
  private val parentReference = createParentReference(libraryId.tableId)
  private val resolved by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val parentElement = parentReference.resolve() ?: return@lazy null
    val libraryCollection = when (parentElement) {
      is JpsGlobal -> parentElement.libraryCollection
      is JpsProject -> parentElement.libraryCollection
      is JpsModule -> parentElement.libraryCollection
      else -> error("Unexpected parent element: $parentElement")
    }
    libraryCollection.findLibrary(libraryId.name)
  }

  private fun createParentReference(tableId: LibraryTableId): JpsElementReference<out JpsCompositeElement> {
    val reference: JpsElementReference<out JpsCompositeElement> = when (tableId) {
      is LibraryTableId.GlobalLibraryTableId -> {
        //todo support custom level
        JpsGlobalElementReference()
      }
      LibraryTableId.ProjectLibraryTableId -> JpsProjectElementReference()
      is LibraryTableId.ModuleLibraryTableId -> JpsModuleReferenceBridge(tableId.moduleId.name)
    }
    (reference as JpsElementBase<*>).setParent(this)
    return reference
  }

  override fun resolve(): JpsLibrary? = resolved

  override fun getLibraryName(): String = libraryId.name

  override fun getParentReference(): JpsElementReference<out JpsCompositeElement> = parentReference
}
