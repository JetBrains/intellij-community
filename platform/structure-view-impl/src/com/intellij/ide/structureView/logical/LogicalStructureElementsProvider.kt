// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Basic interface to provide elements for logical structure
 * Do not implement it itself, there are three extensions, use them depending on logical meaning:
 *  [ContainerElementsProvider] - to provide children for parent (e.g. entity attributes for an entity)
 *  [PropertyElementProvider] - to provide property for element (e.g. scope for a spring bean)
 *  [ConvertElementsProvider] - to provide logical view for element (e.g. entity/bean for a class)
*  To build full model for some element, see [com.intellij.ide.structureView.logical.model.LogicalStructureAssembledModel]
 */
@Experimental
interface LogicalStructureElementsProvider<P, C> {

  fun getElements(parent: P): List<C>

  companion object {
    fun <P> getProviders(p: P): Sequence<LogicalStructureElementsProvider<P, Any>> {
      return EP_NAME.extensionList.asSequence()
        .filter { it.forLogicalModelClass().isInstance(p) } as Sequence<LogicalStructureElementsProvider<P, Any>>
    }
  }
  fun forLogicalModelClass(): Class<P>
}

/**
 * Provides logical children with type [C] for logical object [P]
 */
interface ContainerElementsProvider<P, C> : LogicalStructureElementsProvider<P, C> {
  val containerName: String?
}

/**
 * Provides logical properties with type [C] for logical object [P]
 */
interface PropertyElementProvider<P, C> : LogicalStructureElementsProvider<P, C> {
  val propertyName: String?
}

private const val EP_FQN = "com.intellij.lang.logicalStructureElementsProvider"

private val EP_NAME = ExtensionPointName.create<LogicalStructureElementsProvider<*, *>>(EP_FQN)