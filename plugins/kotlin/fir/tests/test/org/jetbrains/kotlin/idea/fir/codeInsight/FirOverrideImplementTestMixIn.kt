// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.codeInsight

import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.idea.codeInsight.OverrideImplementTestMixIn
import org.jetbrains.kotlin.idea.core.overrideImplement.AbstractGenerateMembersHandler
import org.jetbrains.kotlin.idea.core.overrideImplement.KtClassMember
import org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersHandler
import org.jetbrains.kotlin.idea.core.overrideImplement.KtOverrideMembersHandler
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClassOrObject

internal interface FirOverrideImplementTestMixIn : OverrideImplementTestMixIn<KtClassMember> {
    override fun createImplementMembersHandler(): AbstractGenerateMembersHandler<KtClassMember> = KtImplementMembersHandler()

    override fun createOverrideMembersHandler(): AbstractGenerateMembersHandler<KtClassMember> = KtOverrideMembersHandler()

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun isMemberOfAny(parentClass: KtClassOrObject, chooserObject: KtClassMember): Boolean = allowAnalysisOnEdt {
        analyze(parentClass) {
            val symbol = chooserObject.memberInfo.symbolPointer.restoreSymbol() ?: return false
            symbol.callableIdIfNonLocal?.classId == StandardClassIds.Any
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun getMemberName(parentClass: KtClassOrObject, chooserObject: KtClassMember): String = allowAnalysisOnEdt {
        analyze(parentClass) {
            (chooserObject.memberInfo.symbolPointer.restoreSymbol() as? KtNamedSymbol)?.name?.asString() ?: ""
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun getContainingClassName(parentClass: KtClassOrObject, chooserObject: KtClassMember): String = allowAnalysisOnEdt {
        analyze(parentClass) {
            chooserObject.memberInfo.symbolPointer.restoreSymbol()?.callableIdIfNonLocal?.classId?.shortClassName?.asString() ?: ""
        }
    }
}