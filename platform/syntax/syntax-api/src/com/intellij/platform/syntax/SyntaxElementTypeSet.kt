// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax

import com.intellij.platform.syntax.impl.util.BitSet
import com.intellij.util.fastutil.ints.IntArrayList
import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmName

@ApiStatus.Experimental
fun Collection<SyntaxElementType>.asSyntaxElementTypeSet(): SyntaxElementTypeSet {
  if (this is SyntaxElementTypeSet) return this

  if (this.isEmpty()) return emptySet

  val distinctElementTypes = this as? Set ?: this.toSet()
  ensureNoTransientTypes(distinctElementTypes)

  val indexes = IntArrayList(distinctElementTypes.size)
  for (type in distinctElementTypes) {
    indexes.add(type.index)
  }

  val bitSet = BitSet(indexes)
  return SyntaxElementTypeSet(bitSet, distinctElementTypes.toTypedArray())
}

@ApiStatus.Experimental
fun syntaxElementTypeSetOf(vararg tokens: SyntaxElementType): SyntaxElementTypeSet =
  setOf(*tokens).asSyntaxElementTypeSet()

private val emptySet = SyntaxElementTypeSet(BitSet(IntArrayList()), emptyArray())

@ApiStatus.Experimental
fun emptySyntaxElementTypeSet(): SyntaxElementTypeSet = emptySet

@ApiStatus.Experimental
class SyntaxElementTypeSet internal constructor(
  private val bitSet: BitSet,
  private val tokens: Array<SyntaxElementType>,
) : Set<SyntaxElementType> {

  @JvmName("containsNullable")
  fun contains(element: SyntaxElementType?): Boolean = element != null && contains(element)

  override fun contains(element: SyntaxElementType): Boolean =
    bitSet.contains(element.index)

  override fun containsAll(elements: Collection<SyntaxElementType>): Boolean =
    elements.all { contains(it) }

  override fun isEmpty(): Boolean =
    bitSet.isEmpty()

  override fun iterator(): Iterator<SyntaxElementType> =
    tokens.iterator()

  override val size: Int
    get() = tokens.size

  operator fun plus(other: Iterable<SyntaxElementType>): SyntaxElementTypeSet {
    val newSet = tokens.toSet() + other
    if (newSet.size == size) return this // no new elements
    return newSet.asSyntaxElementTypeSet()
  }

  operator fun plus(other: SyntaxElementType): SyntaxElementTypeSet {
    if (other in this) return this
    return setOf(*tokens, other).asSyntaxElementTypeSet()
  }

  operator fun minus(other: Iterable<SyntaxElementType>): SyntaxElementTypeSet {
    val newSet = tokens.toSet() - other.toSet()
    if (newSet.size == size) return this // no removed elements
    return newSet.asSyntaxElementTypeSet()
  }

  operator fun minus(other: SyntaxElementType): SyntaxElementTypeSet {
    if (other !in this) return this
    return (setOf(*tokens) - other).asSyntaxElementTypeSet()
  }

  fun intersect(other: SyntaxElementTypeSet): SyntaxElementTypeSet {
    val newSet = tokens.toSet().intersect(other)
    if (newSet.size == size) return this // no removed elements
    return newSet.asSyntaxElementTypeSet()
  }
}

private fun ensureNoTransientTypes(types: Set<SyntaxElementType>) {
  var transientTypes: MutableList<SyntaxElementType>? = null
  for (type in types) {
    if (type.index < 0) {
      if (transientTypes == null) {
        transientTypes = mutableListOf()
      }
      transientTypes.add(type)
    }
  }

  if (transientTypes != null) {
    throw IllegalArgumentException("Transient $transientTypes are not allowed to be stored in SyntaxElementTypeSet")
  }
}

fun flattenSyntaxElementTypeSets(vararg sets: SyntaxElementTypeSet): SyntaxElementTypeSet = sets.asList().flatten().asSyntaxElementTypeSet()