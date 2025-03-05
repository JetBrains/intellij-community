// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi

import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageExtensionWithAny
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.ApiStatus

/**
 * Mapping between [IElementType] and [SyntaxElementType] for a given language.
 *
 * Register an implementation as a language extension point `com.intellij.syntax.elementTypeConverter`
 */
@ApiStatus.OverrideOnly
interface ElementTypeConverter {
  fun convert(type: IElementType): SyntaxElementType?
  fun convert(type: SyntaxElementType): IElementType?
}

@ApiStatus.OverrideOnly
abstract class ElementTypeConverterBase(
  private val syntaxToOld: Map<SyntaxElementType, IElementType>,
) : ElementTypeConverter {

  private val oldToSyntax = syntaxToOld.entries.associateBy({ it.value }) { it.key }

  final override fun convert(type: IElementType): SyntaxElementType? =
    oldToSyntax[type]

  final override fun convert(type: SyntaxElementType): IElementType? =
    syntaxToOld[type]
}

internal fun ElementTypeConverter.convertNotNull(type: IElementType): SyntaxElementType {
  return convert(type)
         ?: throw IllegalArgumentException("No converter found for elementType: '${type.debugName}' (${type.javaClass.getName()})")
}

internal fun ElementTypeConverter.convertNotNull(type: SyntaxElementType): IElementType {
  return convert(type)
         ?: throw IllegalArgumentException("No converter found for elementType: '${type}' (${type.javaClass.getName()})")
}

@ApiStatus.Internal
object ElementTypeConverters {
  @JvmStatic
  val instance: LanguageExtension<ElementTypeConverter> = LanguageExtensionWithAny("com.intellij.syntax.elementTypeConverter")
}
