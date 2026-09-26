// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.fastutil.ints.Int2ObjectOpenHashMap
import org.jetbrains.annotations.ApiStatus

/**
 * Mapping between [IElementType] and [SyntaxElementType] for a given language.
 *
 * In most cases, it's recommended to use [elementTypeConverterOf] instead of implementing this interface directly.
 * In this case, the conversion will be done in a more optimized way.
 * The only case when you want to implement this interface directly is when you need to support a language with dynamically created
 * element types.
 *
 * @see ElementTypeConverterFactory
 * @see elementTypeConverterOf
 */
interface ElementTypeConverter {
  /**
   * Converts an [IElementType] to a [SyntaxElementType]. Returns `null` if the conversion is not possible.
   */
  fun convert(type: IElementType): SyntaxElementType?

  /**
   * Converts an [SyntaxElementType] to a [IElementType]. Returns `null` if the conversion is not possible.
   */
  fun convert(type: SyntaxElementType): IElementType?

  /**
   * Converts an array of [SyntaxElementType] to an array of [IElementType].
   * The result array has the same length as the input array and contains `null`s for elements that are not convertible.
   */
  fun convert(types: Array<SyntaxElementType>): Array<IElementType?>
}

/**
 * Register an implementation as a language extension point `com.intellij.syntax.elementTypeConverter`
 *
 * @see ElementTypeConverter
 * @see elementTypeConverterOf
 */
@ApiStatus.OverrideOnly
interface ElementTypeConverterFactory {
  /**
   * Provides an [ElementTypeConverter] for the language this factory is registered for.
   */
  fun getElementTypeConverter(): ElementTypeConverter
}

/**
 * Creates an [ElementTypeConverter] from the given mapping.
 */
fun elementTypeConverterOf(vararg syntaxToOld: Pair<SyntaxElementType, IElementType>): ElementTypeConverter {
  return ElementTypeConverterImpl(syntaxToOld.asList())
}

internal class ElementTypeConverterImpl(
  syntaxToOld: List<Pair<SyntaxElementType, IElementType>>,
) : ElementTypeConverter {
  private val syntaxToOld = Int2ObjectOpenHashMap<IElementType>(syntaxToOld.size).apply {
    for ((new, old) in syntaxToOld) {
      this[new.index] = old
    }
  }
  private val oldToSyntax = Int2ObjectOpenHashMap<SyntaxElementType>(syntaxToOld.size).apply {
    for ((new, old) in syntaxToOld) {
      this[old.index.toInt()] = new
    }
  }

  override fun convert(type: IElementType): SyntaxElementType? =
    oldToSyntax[type.index.toInt()]

  override fun convert(type: SyntaxElementType): IElementType? =
    syntaxToOld[type.index]

  override fun convert(types: Array<SyntaxElementType>): Array<IElementType?> {
    val result = arrayOfNulls<IElementType>(types.size)
    for (i in 0 until types.size) {
      result[i] = syntaxToOld[types[i].index]
    }
    return result
  }

  internal fun reconstruct(): List<Pair<SyntaxElementType, IElementType>> {
    val result = ArrayList<Pair<SyntaxElementType, IElementType>>(oldToSyntax.size)
    val iterator = oldToSyntax.values
    while (iterator.hasNext()) {
      val next = iterator.next()
      result.add(next to syntaxToOld[next.index]!!)
    }
    return result
  }
}

fun ElementTypeConverter.convertNotNull(type: IElementType): SyntaxElementType {
  return convert(type)
         ?: throw IllegalArgumentException("No SyntaxElementType found for elementType: '${type.debugName}' (${type.javaClass.getName()}, $this)")
}

fun ElementTypeConverter.convertNotNull(type: SyntaxElementType): IElementType {
  return convert(type)
         ?: throw IllegalArgumentException("No IElementType found for elementType: '${type}', $this")
}

fun Set<SyntaxElementType>.asTokenSet(converter: ElementTypeConverter): TokenSet {
  val array = map { type -> converter.convertNotNull(type) }.toTypedArray()
  return TokenSet.create(*array)
}

fun compositeElementTypeConverter(converters: List<ElementTypeConverter>): ElementTypeConverter? {
  if (converters.isEmpty()) {
    return null
  }

  if (converters.size == 1) {
    return converters.first()
  }

  if (converters.all { it is ElementTypeConverterImpl }) {
    val pairs = converters
      .map { it as ElementTypeConverterImpl }
      .flatMap { it.reconstruct() }

    return ElementTypeConverterImpl(pairs)
  }

  return CompositeElementTypeConverter(converters)
}

private class CompositeElementTypeConverter(
  private val converters: List<ElementTypeConverter>
) : ElementTypeConverter {

  override fun convert(type: IElementType): SyntaxElementType? =
    converters.firstNotNullOfOrNull { converter -> converter.convert(type) }

  override fun convert(type: SyntaxElementType): IElementType? =
    converters.firstNotNullOfOrNull { converter -> converter.convert(type) }

  override fun convert(types: Array<SyntaxElementType>): Array<IElementType?> {
    val result = arrayOfNulls<IElementType>(types.size)
    for (i in 0 until types.size) {
      result[i] = this.convert(types[i])
    }
    return result
  }

  override fun toString(): String =
    converters.joinToString(", ", "CompositeElementTypeConverter(", ")")
}