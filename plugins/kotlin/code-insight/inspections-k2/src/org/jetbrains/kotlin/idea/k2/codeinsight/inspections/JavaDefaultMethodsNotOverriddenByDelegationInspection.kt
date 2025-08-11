// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.overrideImplement.BodyType
import org.jetbrains.kotlin.idea.core.overrideImplement.KtClassMemberInfo
import org.jetbrains.kotlin.idea.core.overrideImplement.KtGenerateMembersHandler
import org.jetbrains.kotlin.idea.refactoring.isTrueJavaMethod
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class JavaDefaultMethodsNotOverriddenByDelegationInspection : AbstractKotlinInspection() {

    @OptIn(KaExperimentalApi::class)
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitDelegatedSuperTypeEntry(specifier: KtDelegatedSuperTypeEntry) {
                super.visitDelegatedSuperTypeEntry(specifier)
                val delegateExpression = specifier.delegateExpression ?: return
                val superTypeList = specifier.parent as? KtSuperTypeList ?: return
                val declaration = superTypeList.parent as? KtClassOrObject ?: return

                val (methodsToOverride, delegateName) = analyze(specifier) {
                    val delegatedInterface = specifier.typeReference?.type?.symbol as? KaNamedClassSymbol ?: return

                    val inheritedJavaDefaultMethods = collectInheritedJavaDefaultMethods(
                        delegatedInterface,
                        declaration
                    ).takeIf { it.isNotEmpty() } ?: return

                    val delegateClass = delegateExpression.expressionType?.symbol as? KaClassSymbol ?: return
                    val javaDefaultMethodsToOverride = collectJavaDefaultMethodsToOverride(delegateClass, inheritedJavaDefaultMethods)

                    val delegateSymbol = delegateExpression.mainReference?.resolveToSymbol()
                    val delegateName = when (delegateSymbol) {
                        is KaVariableSymbol -> delegateSymbol.name.asString()
                        else -> null
                    }

                    val memberInfos = javaDefaultMethodsToOverride.mapNotNull { javaDefaultMethod ->
                        val containingSymbol = javaDefaultMethod.containingSymbol as? KaClassSymbol ?: return@mapNotNull null

                        @NlsSafe
                        val fqName = containingSymbol.classId?.asSingleFqName()?.toString() ?: containingSymbol.name?.asString()
                        KtClassMemberInfo.create(
                            symbol = javaDefaultMethod,
                            memberText = javaDefaultMethod.render(KtGenerateMembersHandler.renderer),
                            memberIcon = KotlinIconProvider.getIcon(javaDefaultMethod),
                            containingSymbolText = fqName,
                            containingSymbolIcon = KotlinIconProvider.getIcon(javaDefaultMethod),
                        )
                    }.toList().takeIf { it.isNotEmpty() } ?: return
                    memberInfos to delegateName
                }

                val bodyTypeDelegateFix = delegateName?.let {
                    IntentionWrapper(
                        KtDelegateJavaDefaultMethodsQuickFix(methodsToOverride, BodyType.Delegate(delegateName))
                    )
                }

                val bodyTypeSuperFix = IntentionWrapper(KtDelegateJavaDefaultMethodsQuickFix(methodsToOverride, BodyType.Super))

                val fixes = if (holder.isOnTheFly) listOfNotNull(bodyTypeDelegateFix, bodyTypeSuperFix).toTypedArray() else emptyArray()
                holder.registerProblem(
                    specifier,
                    KotlinBundle.message("inspection.java.default.methods.not.overridden.by.delegation.message"),
                    *fixes
                )
            }
        }
    }
}

private fun KaSession.collectInheritedJavaDefaultMethods(
    delegatedInterface: KaNamedClassSymbol,
    declaration: KtClassOrObject,
): Set<KaCallableSymbol> {
    val callables = delegatedInterface.memberScope.callables
    return declaration.classSymbol
        ?.memberScope
        ?.callables
        ?.filter { it.isJavaDefaultMethod() }
        ?.filter { callables.contains(it) }
        ?.toSet() ?: emptySet()
}

private fun KaSession.collectJavaDefaultMethodsToOverride(
    klass: KaClassSymbol,
    javaDefaultMethods: Set<KaCallableSymbol>
): Sequence<KaCallableSymbol> {
    return when (klass.modality) {
        KaSymbolModality.SEALED ->
            (klass as KaNamedClassSymbol)
                .sealedClassInheritors
                .flatMap { inheritor -> inheritor.memberScope.callables }
                .asSequence()
                .let { callables -> collectOverriddenJavaDefaultMethods(callables, javaDefaultMethods) }

        KaSymbolModality.FINAL ->
            klass.memberScope
                .callables
                .let { callables -> collectOverriddenJavaDefaultMethods(callables, javaDefaultMethods) }

        KaSymbolModality.ABSTRACT, KaSymbolModality.OPEN -> javaDefaultMethods.asSequence()
    }
}

private fun KaSession.collectOverriddenJavaDefaultMethods(
    callables: Sequence<KaCallableSymbol>,
    javaDefaultMethods: Set<KaCallableSymbol>,
): Sequence<KaCallableSymbol> = callables.flatMap { callable ->
    callable.allOverriddenSymbols.filter(javaDefaultMethods::contains)
}

private fun KaCallableSymbol.isJavaDefaultMethod(): Boolean {
    val method = (psi as? PsiMethod)?.takeIf { it.isTrueJavaMethod() } ?: return false
    return method.containingClass?.isInterface == true && method.hasModifierProperty(PsiModifier.DEFAULT)
}
