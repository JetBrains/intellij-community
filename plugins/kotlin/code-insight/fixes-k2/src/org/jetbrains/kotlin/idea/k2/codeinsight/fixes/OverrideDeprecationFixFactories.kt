// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.renderAsSourceCode
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.*
import org.jetbrains.kotlin.idea.quickfix.CopyDeprecatedAnnotationFix.ArgumentsData
import org.jetbrains.kotlin.idea.quickfix.CopyDeprecatedAnnotationFix.ArgumentsData.ReplaceWithData
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal object OverrideDeprecationFixFactories {

    val copyDeprecatedAnnotationFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.OverrideDeprecation ->
        val overriddenSymbol = diagnostic.overridenSymbol as? KaAnnotated ?: return@ModCommandBased emptyList()
        val deprecatedAnnotationClassId = StandardClassIds.Annotations.Deprecated

        val deprecatedAnnotation = overriddenSymbol.annotations.find {
            it.classId == deprecatedAnnotationClassId
        } ?: return@ModCommandBased emptyList()

        val sourceName = renderName(diagnostic.overridenSymbol) ?: return@ModCommandBased emptyList()
        val destinationName = renderName(diagnostic.psi.symbol) ?: return@ModCommandBased emptyList()

        val argumentsData = prepareArgumentsData(deprecatedAnnotation) ?: return@ModCommandBased emptyList()

        listOf(
            CopyDeprecatedAnnotationFix(
                diagnostic.psi,
                deprecatedAnnotationClassId,
                AddAnnotationFix.Kind.Copy(sourceName, destinationName),
                argumentsData,
            )
        )
    }
}

private fun KaSession.renderName(symbol: KaSymbol): String? {
    val containerPrefix = symbol.containingDeclaration?.name?.let { "${it.render()}." } ?: ""
    val name = symbol.name?.render() ?: return null
    return containerPrefix + name
}

private fun prepareArgumentsData(annotation: KaAnnotation): ArgumentsData? {
    val message = annotation.argumentValueByName(MESSAGE_ARGUMENT).safeAs<KaAnnotationValue.ConstantValue>()
        ?.value
        ?.safeAs<KaConstantValue.StringValue>()
        ?.render()
        ?.removeSurrounding("\"")
        ?.let { "\"${StringUtil.escapeStringCharacters(it)}\"" } ?: return null

    val replaceWithAnnotation = annotation
        .argumentValueByName(REPLACE_WITH_ARGUMENT)
        .safeAs<KaAnnotationValue.NestedAnnotationValue>()
        ?.annotation

    val replaceWithData = if (replaceWithAnnotation != null) {
        val expression = replaceWithAnnotation
            .argumentValueByName(EXPRESSION_ARGUMENT)
            .safeAs<KaAnnotationValue.ConstantValue>()
            ?.value
            ?.safeAs<KaConstantValue.StringValue>()
            ?.render() ?: return null

        val imports = replaceWithAnnotation
            .argumentValueByName(IMPORTS_ARGUMENT)
            .safeAs<KaAnnotationValue.ArrayValue>()
            ?.values
            ?.map { it.renderAsSourceCode() }

        ReplaceWithData(expression, imports)
    } else {
        null
    }

    val level = annotation.argumentValueByName(LEVEL_ARGUMENT)?.safeAs<KaAnnotationValue.EnumEntryValue>()?.let {
        it.callableId?.asSingleFqName()?.asString() ?: return null
    }

    return ArgumentsData(
        message,
        replaceWithData,
        level,
    )
}

private fun KaAnnotation.argumentValueByName(name: Name): KaAnnotationValue? {
    return arguments.find { it.name == name }?.expression
}
