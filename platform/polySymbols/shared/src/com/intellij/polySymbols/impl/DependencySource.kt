// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.model.Pointer

/**
 * Carrier of a materialized symbol's dependency roots. Flips from
 * [FromSpecs] (initial instance, still in its creation read action) to
 * [FromPointers] the first time a pointer is needed, and subsequent
 * cross-read-action instances carry [FromPointers] onward.
 */
internal sealed interface DependencySource {
  val isEmpty: Boolean

  fun asFromPointers(): FromPointers

  interface FromSpecs : DependencySource {
    fun snapshot(): List<Any>
  }

  interface FromPointers : DependencySource {
    fun snapshot(): List<Any>?
  }

  private class FromSpecsImpl(val specs: List<DepSpec<*>>) : FromSpecs {
    override val isEmpty: Boolean get() = specs.isEmpty()
    override fun snapshot(): List<Any> {
      val values = ArrayList<Any>(specs.size)
      for (spec in specs) values += spec.currentValue()
      return values
    }

    private val lazyPointers: List<Pointer<out Any>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
      specs.map { it.toPointer() }
    }

    override fun asFromPointers(): FromPointers =
      FromPointersImpl(lazyPointers)
  }

  private class FromPointersImpl(private val pointers: List<Pointer<out Any>>) : FromPointers {
    override val isEmpty: Boolean get() = pointers.isEmpty()

    override fun snapshot(): List<Any>? {
      val values = ArrayList<Any>(pointers.size)
      for (pointer in pointers) values += pointer.dereference() ?: return null
      return values
    }

    override fun asFromPointers(): FromPointers = this
  }


  companion object {
    private val EMPTY_DEPENDENCY_SOURCE: FromSpecs = FromSpecsImpl(emptyList())

    fun fromSpecs(specs: List<DepSpec<*>>): FromSpecs {
      return if (specs.isEmpty()) EMPTY_DEPENDENCY_SOURCE else FromSpecsImpl(specs)
    }
  }
}
