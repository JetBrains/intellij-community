// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k1.codeinsight.inspections

import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.AbstractFunctionNameInspection
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes

class FunctionNameInspection : AbstractFunctionNameInspection() {
    override fun KtNamedFunction.isFactoryFunctionByAnalyze(): Boolean {
        val functionName = this.name ?: return false
        val returnType = resolveToDescriptorIfAny()?.returnType ?: return false
        return returnType.shortName() == functionName || returnType.supertypes().any { it.shortName() == functionName }
    }

    private fun KotlinType.shortName(): String? = fqName?.takeUnless(FqName::isRoot)?.shortName()?.asString()
}