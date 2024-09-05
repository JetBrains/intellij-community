// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical

/**
 * Provides logical model [C] for some physical object [P]
 */
abstract class ConvertElementsProvider<P, C> : LogicalStructureElementsProvider<P, C> {
  abstract fun convert(p: P): C?

  override fun getElements(parent: P): List<C> {
    val t = convert(parent)
    return if (t != null) listOf(t) else emptyList()
  }

  companion object {
    fun <P> convert(p: P): Sequence<*> {
      val convertProviders = LogicalStructureElementsProvider.getProviders(p).filterIsInstance<ConvertElementsProvider<P, Any>>()
      if (convertProviders.count() == 0) return sequenceOf(p)

      val converted = convertProviders.map { it.convert(p) }.filterNotNull()
      return if(converted.count() == 0)  sequenceOf(p) else converted
    }
  }
}