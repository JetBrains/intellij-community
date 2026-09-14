// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.psi.KtClass

object ValueClassMemberGenerationUtils {

    data class Parameter(val name: String, val typeText: String, val overrideComponentN: Boolean = false)

    fun KtClass.isFullValueClass(): Boolean =
        isValue() &&
                languageVersionSettings.supportsFeature(LanguageFeature.FullValueClasses) &&
                KotlinPsiHeuristics.findAnnotation(this, StandardKotlinNames.Jvm.JvmInline) == null

    fun KtClass.generationParameters(needsOverride: (Int) -> Boolean = { false }) : List<Parameter>? {
        val parameters = primaryConstructorParameters
        if (parameters.isEmpty()) return null
        return parameters.withIndex().map { (index, parameter) ->
            val name = parameter.nameIdentifier?.text ?: return null
            val typeText = parameter.typeReference?.text ?: return null
            Parameter(name, typeText, needsOverride(index))
        }
    }

    fun KtClass.copyFunctionText(parameters: List<Parameter>): String? {
        val className = nameIdentifier?.text ?: return null
        val typeParameterNames = typeParameters.map { it.nameIdentifier?.text ?: return null }
        val typeArguments = typeParameterNames.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "<", postfix = ">")
            .orEmpty()
        val classNameWithTypeArguments = className + typeArguments
        val parameterList = parameters.joinToString { "${it.name}: ${it.typeText} = this.${it.name}" }
        val argumentList = parameters.joinToString { it.name }
        return "fun copy($parameterList): $classNameWithTypeArguments = $classNameWithTypeArguments($argumentList)"
    }

    fun componentFunctionText(index: Int, parameter: Parameter): String {
        val text = "operator fun component$index(): ${parameter.typeText} = ${parameter.name}"
        if (parameter.overrideComponentN) return "override $text"
        return text
    }
}
