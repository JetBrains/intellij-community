// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.codeInsight

import org.jetbrains.kotlin.idea.codeInsight.OverrideImplementTestMixIn
import org.jetbrains.kotlin.idea.core.overrideImplement.AbstractGenerateMembersHandler
import org.jetbrains.kotlin.idea.core.overrideImplement.KtClassMember
import org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersHandler
import org.jetbrains.kotlin.idea.core.overrideImplement.KtOverrideMembersHandler
import org.jetbrains.kotlin.analysis.api.analyse
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.tokens.HackToForceAllowRunningAnalyzeOnEDT
import org.jetbrains.kotlin.analysis.api.tokens.hackyAllowRunningOnEdt
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClassOrObject

internal interface FirOverrideImplementTestMixIn : OverrideImplementTestMixIn<KtClassMember> {
    override fun createImplementMembersHandler(): AbstractGenerateMembersHandler<KtClassMember> = KtImplementMembersHandler()

    override fun createOverrideMembersHandler(): AbstractGenerateMembersHandler<KtClassMember> = KtOverrideMembersHandler()

    @OptIn(HackToForceAllowRunningAnalyzeOnEDT::class)
    override fun isMemberOfAny(parentClass: KtClassOrObject, chooserObject: KtClassMember): Boolean {
        return hackyAllowRunningOnEdt {
            analyse(parentClass) {
                chooserObject.symbol.callableIdIfNonLocal?.classId == StandardClassIds.Any
            }
        }
    }

    @OptIn(HackToForceAllowRunningAnalyzeOnEDT::class)
    override fun getMemberName(parentClass: KtClassOrObject, chooserObject: KtClassMember): String {
        return hackyAllowRunningOnEdt {
            analyse(parentClass) {
                (chooserObject.symbol as? KtNamedSymbol)?.name?.asString() ?: ""
            }
        }
    }

    @OptIn(HackToForceAllowRunningAnalyzeOnEDT::class)
    override fun getContainingClassName(parentClass: KtClassOrObject, chooserObject: KtClassMember): String {
        return hackyAllowRunningOnEdt {
            analyse(parentClass) {
                chooserObject.symbol.callableIdIfNonLocal?.classId?.shortClassName?.asString() ?: ""
            }
        }
    }
}