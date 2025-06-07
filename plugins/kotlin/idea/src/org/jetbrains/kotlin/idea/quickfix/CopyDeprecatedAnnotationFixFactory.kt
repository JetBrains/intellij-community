// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.quickfix.CopyDeprecatedAnnotationFix.ArgumentsData
import org.jetbrains.kotlin.idea.quickfix.CopyDeprecatedAnnotationFix.ArgumentsData.ReplaceWithData
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.constants.AnnotationValue
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * A factory for `OVERRIDE_DEPRECATION` warning. It provides an action that copies the `@Deprecated` annotation
 * from the ancestor's deprecated function/property to the overriding function/property in the derived class.
 */
internal object CopyDeprecatedAnnotationFixFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        if (diagnostic.factory != Errors.OVERRIDE_DEPRECATION) return emptyList()

        val deprecation = Errors.OVERRIDE_DEPRECATION.cast(diagnostic)
        val declaration = deprecation.psiElement
        return deprecation.c.mapNotNull {
            val annotation = it.target.annotations.findAnnotation(StandardNames.FqNames.deprecated) ?: return@mapNotNull null
            val sourceName = renderName(it.target)
            val destinationName = renderName(deprecation.b)
            val argumentsData = prepareArgumentsData(annotation) ?: return emptyList()

            CopyDeprecatedAnnotationFix(
                declaration,
                StandardClassIds.Annotations.Deprecated,
                AddAnnotationFix.Kind.Copy(sourceName, destinationName),
                argumentsData,
            ).asIntention()
        }
    }

    private fun prepareArgumentsData(annotation: AnnotationDescriptor): ArgumentsData? {
        val message = annotation.allValueArguments[MESSAGE_ARGUMENT]?.safeAs<StringValue>()
            ?.toString()
            ?.removeSurrounding("\"")
            ?.let { "\"${StringUtil.escapeStringCharacters(it)}\"" } ?: return null

        val replaceWithAnnotation = annotation.allValueArguments[REPLACE_WITH_ARGUMENT]?.safeAs<AnnotationValue>()?.value
        val replaceWithData = if (replaceWithAnnotation != null) {
            val expression = replaceWithAnnotation.allValueArguments[EXPRESSION_ARGUMENT]?.safeAs<StringValue>()?.toString() ?: return null
            val imports = replaceWithAnnotation.allValueArguments[IMPORTS_ARGUMENT]?.safeAs<ArrayValue>()?.value?.map { it.toString() }
            ReplaceWithData(expression, imports)
        } else {
            null
        }

        val level = annotation.allValueArguments[LEVEL_ARGUMENT]?.safeAs<EnumValue>()?.toString()

        return ArgumentsData(
            message,
            replaceWithData,
            level,
        )
    }

    // A renderer for function/property names: uses qualified names when available to disambiguate names of overrides
    private fun renderName(descriptor: DeclarationDescriptor): String {
        val containerPrefix = descriptor.containingDeclaration?.let { "${it.name.render()}." } ?: ""
        val name = descriptor.name.render()
        return containerPrefix + name
    }
}
