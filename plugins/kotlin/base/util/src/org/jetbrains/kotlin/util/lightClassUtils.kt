// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.util

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.javaInterop.asPsiClass
import org.jetbrains.kotlin.analysis.api.javaInterop.asPsiMethods
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.classSymbol
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.psi.KtClassOrObject

@OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
fun KtClassOrObject.toLightClassWithBuiltinMapping(): PsiClass? {
    // FIXME: KTIJ-40145
    allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            analyze(this) {
                classSymbol?.asPsiClass()?.let { return it }
            }
        }
    }

    val fqName = fqName ?: return null
    val javaClassFqName = JavaToKotlinClassMap.mapKotlinToJava(fqName.toUnsafe())?.asSingleFqName() ?: return null
    val searchScope = useScope as? GlobalSearchScope ?: return null
    return JavaPsiFacade.getInstance(project).findClass(javaClassFqName.asString(), searchScope)
}

context(_: KaSession)
fun KaCallableSymbol.getPsiMethods(): List<PsiMethod> =
    when (this) {
        is KaFunctionSymbol -> asPsiMethods()
        is KaPropertySymbol -> listOfNotNull(getter?.asPsiMethods(), setter?.asPsiMethods()).flatten()
        else -> emptyList()
    }
