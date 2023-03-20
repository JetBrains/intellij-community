// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable

import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.CallableInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.FunctionInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.TypeInfo
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.guessTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.TypeProjectionImpl
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.toDefaultAttributes
import org.jetbrains.kotlin.util.OperatorNameConventions
import java.util.*

object CreateIteratorFunctionActionFactory : CreateCallableMemberFromUsageFactory<KtForExpression>() {
    override fun getElementOfInterest(diagnostic: Diagnostic): KtForExpression? {
        return diagnostic.psiElement.findParentOfType(strict = false)
    }

    override fun createCallableInfo(element: KtForExpression, diagnostic: Diagnostic): CallableInfo? {
        val file = diagnostic.psiFile as? KtFile ?: return null
        val iterableExpr = element.loopRange ?: return null
        val variableExpr: KtExpression = ((element.loopParameter ?: element.destructuringDeclaration) ?: return null) as KtExpression
        val iterableType = TypeInfo(iterableExpr, Variance.IN_VARIANCE)

        val (bindingContext, moduleDescriptor) = file.analyzeWithAllCompilerChecks()

        val returnJetType = moduleDescriptor.builtIns.iterator.defaultType
        val returnJetTypeParameterTypes = variableExpr.guessTypes(bindingContext, moduleDescriptor)
        if (returnJetTypeParameterTypes.size != 1) return null

        val returnJetTypeParameterType = TypeProjectionImpl(returnJetTypeParameterTypes[0])
        val returnJetTypeArguments = Collections.singletonList(returnJetTypeParameterType)
        val newReturnJetType = KotlinTypeFactory.simpleTypeWithNonTrivialMemberScope(
            returnJetType.annotations.toDefaultAttributes(),
            returnJetType.constructor,
            returnJetTypeArguments,
            returnJetType.isMarkedNullable,
            returnJetType.memberScope
        )
        val returnType = TypeInfo(newReturnJetType, Variance.OUT_VARIANCE)
        return FunctionInfo(
            OperatorNameConventions.ITERATOR.asString(),
            iterableType,
            returnType,
            modifierList = KtPsiFactory(element.project).createModifierList(KtTokens.OPERATOR_KEYWORD)
        )
    }
}
