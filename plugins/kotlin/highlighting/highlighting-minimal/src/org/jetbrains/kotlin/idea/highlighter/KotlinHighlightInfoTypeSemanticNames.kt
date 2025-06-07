// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightInfoType.HighlightInfoTypeImpl
import com.intellij.openapi.editor.colors.TextAttributesKey

/**
 * [HighlightInfoType]s used in Kotlin semantic highlighting
 */
object KotlinHighlightInfoTypeSemanticNames {
  // default keys (mostly syntax elements)
  val KEYWORD: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.KEYWORD)
  val BUILTIN_ANNOTATION: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.BUILTIN_ANNOTATION)
  val NUMBER: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.NUMBER)
  val STRING: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.STRING)
  val FUNCTION_LITERAL_BRACES_AND_ARROW: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.FUNCTION_LITERAL_BRACES_AND_ARROW)
  val COMMA: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.COMMA)
  val SEMICOLON: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.SEMICOLON)
  val COLON: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.COLON)
  val DOT: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.DOT)
  val SAFE_ACCESS: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.SAFE_ACCESS)
  val EXCLEXCL: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.EXCLEXCL)
  val ARROW: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.ARROW)
  val BLOCK_COMMENT: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.BLOCK_COMMENT)
  val KDOC_LINK: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.KDOC_LINK)

  // class kinds
  val CLASS: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.CLASS)
  val TYPE_PARAMETER: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.TYPE_PARAMETER)
  val ABSTRACT_CLASS: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.ABSTRACT_CLASS)
  val TRAIT: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.TRAIT)
  val ANNOTATION: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.ANNOTATION)
  val OBJECT: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.OBJECT)
  val ENUM: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.ENUM)
  val ENUM_ENTRY: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.ENUM_ENTRY)
  val TYPE_ALIAS: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.TYPE_ALIAS)
  val DATA_CLASS: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.DATA_CLASS)
  val DATA_OBJECT: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.DATA_OBJECT)

  // variable kinds
  val MUTABLE_VARIABLE: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.MUTABLE_VARIABLE)
  val LOCAL_VARIABLE: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.LOCAL_VARIABLE)
  val PARAMETER: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.PARAMETER)
  val WRAPPED_INTO_REF: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.WRAPPED_INTO_REF)
  val INSTANCE_PROPERTY: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.INSTANCE_PROPERTY)
  val PACKAGE_PROPERTY: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.PACKAGE_PROPERTY)
  val BACKING_FIELD_VARIABLE: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.BACKING_FIELD_VARIABLE)
  val EXTENSION_PROPERTY: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.EXTENSION_PROPERTY)
  val SYNTHETIC_EXTENSION_PROPERTY: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.SYNTHETIC_EXTENSION_PROPERTY)
  val DYNAMIC_PROPERTY_CALL: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.DYNAMIC_PROPERTY_CALL)
  val ANDROID_EXTENSIONS_PROPERTY_CALL: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.ANDROID_EXTENSIONS_PROPERTY_CALL)
  val INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.INSTANCE_PROPERTY_CUSTOM_PROPERTY_DECLARATION)
  val PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.PACKAGE_PROPERTY_CUSTOM_PROPERTY_DECLARATION)

  // functions
  val FUNCTION_LITERAL_DEFAULT_PARAMETER: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.FUNCTION_LITERAL_DEFAULT_PARAMETER)
  val FUNCTION_DECLARATION: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.FUNCTION_DECLARATION)
  val FUNCTION_CALL: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.FUNCTION_CALL)
  val PACKAGE_FUNCTION_CALL: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.PACKAGE_FUNCTION_CALL)
  val EXTENSION_FUNCTION_CALL: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.EXTENSION_FUNCTION_CALL)
  val CONSTRUCTOR_CALL: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.CONSTRUCTOR_CALL)
  val DYNAMIC_FUNCTION_CALL: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.DYNAMIC_FUNCTION_CALL)
  val SUSPEND_FUNCTION_CALL: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.SUSPEND_FUNCTION_CALL)
  val VARIABLE_AS_FUNCTION_CALL: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.VARIABLE_AS_FUNCTION_CALL)
  val VARIABLE_AS_FUNCTION_LIKE_CALL: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.VARIABLE_AS_FUNCTION_LIKE_CALL)

  // other
  val SMART_CAST_VALUE: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.SMART_CAST_VALUE)
  val SMART_CONSTANT: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.SMART_CONSTANT)
  val SMART_CAST_RECEIVER: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.SMART_CAST_RECEIVER)
  val LABEL: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.LABEL)
  val NAMED_ARGUMENT: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.NAMED_ARGUMENT)
  val ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES: HighlightInfoType = createSymbolTypeInfo(KotlinHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES)

  private fun createSymbolTypeInfo(attributesKey: TextAttributesKey): HighlightInfoType {
    return HighlightInfoTypeImpl(HighlightInfoType.SYMBOL_TYPE_SEVERITY, attributesKey, false)
  }
}
