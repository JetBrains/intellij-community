// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.testIntegration

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration.AbstractKotlinTestFinder
import org.jetbrains.kotlin.psi.KtClassOrObject

class K2TestFinder: AbstractKotlinTestFinder() {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun isResolvable(classOrObject: KtClassOrObject): Boolean =
        allowAnalysisOnEdt {
            analyze(classOrObject) {
                classOrObject.classSymbol != null
            }
        }

}