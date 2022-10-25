// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import com.intellij.codeInspection.CleanupLocalInspectionTool
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.AbstractKotlinApplicatorBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.inputProvider
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.TypeInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateType
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class RedundantUnitReturnTypeInspection :
    AbstractKotlinApplicatorBasedInspection<KtNamedFunction, TypeInfo>(
        KtNamedFunction::class
    ), CleanupLocalInspectionTool {

    override fun getApplicabilityRange() = ApplicabilityRanges.CALLABLE_RETURN_TYPE

    override fun getApplicator() = applicator<KtNamedFunction, TypeInfo> {
        isApplicableByPsi { callable ->
            val function = callable as? KtNamedFunction ?: return@isApplicableByPsi false
            function.hasBlockBody() && function.typeReference != null
        }
        familyName(KotlinBundle.lazyMessage("remove.explicit.type.specification"))
        actionName(KotlinBundle.lazyMessage("redundant.unit.return.type"))
        applyTo { declaration, typeInfo, project, editor ->
            updateType(declaration, typeInfo, project, editor)
        }
    }

    override fun getInputProvider() = inputProvider<KtNamedFunction, TypeInfo> { function ->
        when {
            function.getFunctionLikeSymbol().returnType.isUnit -> TypeInfo(TypeInfo.UNIT)
            else -> null
        }
    }
}