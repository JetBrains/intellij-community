// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.caches.resolve.analyzeInContext
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils

class K1ExtractableSubstringInfo(
    startEntry: KtStringTemplateEntry,
    endEntry: KtStringTemplateEntry,
    prefix: String,
    suffix: String,
    type: KotlinType? = null
): ExtractableSubstringInfo(startEntry, endEntry, prefix, suffix) {
    private fun guessLiteralType(literal: String): KotlinType {
        val facade = template.getResolutionFacade()
        val module = facade.moduleDescriptor
        val stringType = module.builtIns.stringType

        if (startEntry != endEntry || startEntry !is KtLiteralStringTemplateEntry) return stringType

        val expr = KtPsiFactory(facade.project).createExpressionIfPossible(literal) ?: return stringType

        val context = facade.analyze(template, BodyResolveMode.PARTIAL)
        val scope = template.getResolutionScope(context, facade)

        val tempContext = expr.analyzeInContext(scope, template)
        val trace = DelegatingBindingTrace(tempContext, "Evaluate '$literal'")
        val languageVersionSettings = facade.languageVersionSettings
        val value = ConstantExpressionEvaluator(module, languageVersionSettings, facade.project).evaluateExpression(expr, trace)
        if (value == null || value.isError) return stringType

        return value.toConstantValue(TypeUtils.NO_EXPECTED_TYPE).getType(module)
    }

    val type = type ?: guessLiteralType(content)

    override val isString: Boolean = KotlinBuiltIns.isString(this.type)

    override fun copy(newStartEntry: KtStringTemplateEntry, newEndEntry: KtStringTemplateEntry): K1ExtractableSubstringInfo {
        return K1ExtractableSubstringInfo(newStartEntry, newEndEntry, prefix, suffix, type)
    }
}