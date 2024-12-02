// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.model

import com.intellij.ide.structureView.logical.LogicalStructureElementsProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface LogicalContainer<C> {

  fun getElements(): List<C>

}

@ApiStatus.Internal
class ProvidedLogicalContainer<C>(
  val provider: LogicalStructureElementsProvider<*, C>,
  private val elements: () -> List<C>
) : LogicalContainer<C> {

  //constructor(elements: List<C>) : this({ elements })

  override fun getElements(): List<C> = elements()

}