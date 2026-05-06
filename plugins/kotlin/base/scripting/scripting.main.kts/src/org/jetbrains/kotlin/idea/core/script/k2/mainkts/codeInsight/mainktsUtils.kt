// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.mainkts.codeInsight

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry

internal val IMPORT_FQN: FqName = FqName("org.jetbrains.kotlin.mainKts.Import")
internal val DEPENDS_ON_FQN: FqName = FqName("kotlin.script.experimental.dependencies.DependsOn")

@OptIn(KaAllowAnalysisOnEdt::class)
internal fun KtAnnotationEntry.matchesAnnotationFqn(fqn: FqName): Boolean {
    val element = this
    return allowAnalysisOnEdt {
        analyze(element) {
            element.calleeExpression?.resolveToCall()?.singleConstructorCallOrNull()?.symbol?.containingClassId?.asSingleFqName()
        }
    } == fqn
}