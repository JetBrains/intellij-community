// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement

val MESSAGE_ARGUMENT = Name.identifier("message")
val REPLACE_WITH_ARGUMENT = Name.identifier("replaceWith")
val LEVEL_ARGUMENT = Name.identifier("level")
val EXPRESSION_ARGUMENT = Name.identifier("expression")
val IMPORTS_ARGUMENT = Name.identifier("imports")

class CopyDeprecatedAnnotationFix(
    element: KtElement,
    annotationClassId: ClassId,
    kind: Kind = Kind.Self,
    argumentsData: ArgumentsData,
) : AddAnnotationFix(
    element,
    annotationClassId,
    kind,
    annotationInnerText = argumentsData.toAnnotationInnerText(),
) {
    override fun renderArgumentsForIntentionName(): String = ""

    data class ArgumentsData(
        val message: String,
        val replaceWithData: ReplaceWithData?,
        val level: String?,
    ) {
        fun toAnnotationInnerText(): String = listOfNotNull(
            message,
            replaceWithData?.let { "replaceWith = ReplaceWith(${it.toListOfStrings().joinToString()})" },
            level?.let { "level = $it" },
        ).joinToString()

        data class ReplaceWithData(
            val expression: String,
            val imports: List<String>?,
        ) {
            fun toListOfStrings(): List<String> = listOf(expression) + (imports ?: emptyList())
        }
    }
}
