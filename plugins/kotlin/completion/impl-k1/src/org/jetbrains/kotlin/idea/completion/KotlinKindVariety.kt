// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.platform.ml.impl.turboComplete.KindVariety
import org.jetbrains.kotlin.idea.KotlinLanguage

/**
 * Represents categories of completion variants in Kotlin K1 code completion.
 *
 * The names are used later to create [com.intellij.platform.ml.impl.turboComplete.KindCollector]s,
 * which are later reordered via [com.intellij.turboComplete.SortingExecutor].
 */
enum class KotlinCompletionKindName {
  DSL_FUNCTION,
  SMART_ADDITIONAL_ITEM,
  REFERENCE_BASIC,
  REFERENCE_EXTENSION,
  PACKAGE_NAME,
  NAMED_ARGUMENT,
  EXTENSION_FUNCTION_TYPE_VALUE,
  CONTEXT_VARIABLE_TYPE_SC,
  CONTEXT_VARIABLE_TYPE_REFERENCE,
  STATIC_MEMBER_FROM_IMPORTS,
  NON_IMPORTED,
  DEBUGGER_VARIANTS,
  STATIC_MEMBER_OBJECT_MEMBER,
  STATIC_MEMBER_EXPLICIT_INHERITED,
  STATIC_MEMBER_INACCESSIBLE,
  KEYWORD_ONLY,
  OPERATOR_NAME,
  DECLARATION_NAME,
  TOP_LEVEL_CLASS_NAME,
  SUPER_QUALIFIER,
  DECLARATION_NAME_FROM_UNRESOLVED_OVERRIDE,
  PARAMETER_OR_VAR_NAME_AND_TYPE,
}

object KotlinKindVariety : KindVariety {
    override fun kindsCorrespondToParameters(parameters: CompletionParameters): Boolean {
    return parameters.position.language == KotlinLanguage.INSTANCE
  }

  override val actualCompletionContributorClass: Class<*>
    get() = KotlinCompletionContributor::class.java
}