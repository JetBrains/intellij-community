// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax

import com.intellij.platform.syntax.impl.fastutil.ints.IntArrayList
import com.intellij.platform.syntax.impl.util.BitSet
import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmName

@ApiStatus.Experimental
fun Collection<SyntaxElementType>.asSyntaxElementTypeSet(): SyntaxElementTypeSet {
  if (this is SyntaxElementTypeSet) return this

  if (this.isEmpty()) return emptySet

  val indexes = IntArrayList(size)
  for (type in this) {
    indexes.add(type.index)
  }
  val bitSet = BitSet(indexes)
  return SyntaxElementTypeSet(bitSet, this.toTypedArray())
}

@ApiStatus.Experimental
fun syntaxElementTypeSetOf(vararg tokens: SyntaxElementType): SyntaxElementTypeSet =
  listOf(*tokens).asSyntaxElementTypeSet()

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

  operator fun plus(other: Iterable<SyntaxElementType>): SyntaxElementTypeSet =
    (listOf(*tokens) + other).asSyntaxElementTypeSet()

  operator fun plus(other: SyntaxElementType): SyntaxElementTypeSet =
    (listOf(*tokens) + other).asSyntaxElementTypeSet()
}