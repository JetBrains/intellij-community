// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.idea.base.facet.JvmOnlyProjectChecker
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinSupportAvailability
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPureClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier

class MultiplatformSupportAvailability: KotlinSupportAvailability {
    override fun name(): String = KotlinBaseFirCodeInsight.message("kmp.support.availability.name")

    override fun isSupported(ktElement: KtElement): Boolean {
        if (Registry.`is`("kotlin.k2.kmp.enabled", true) || JvmOnlyProjectChecker.getInstance(ktElement.project).value()) return true
        return when (ktElement) {
            is KtFile -> {
                val platform = ktElement.platform
                platform.isJvm() || platform.isCommon()
            }

            else -> {
                var result: Boolean = true
                for (declaration in ktElement.parents(true).filterIsInstance<KtDeclaration>()) {
                    if ((declaration as? KtPureClassOrObject)?.isLocal == true || (declaration as? KtFunction)?.isLocal == true) {
                        result = true
                        break
                    }

                    if (declaration.hasActualModifier() || declaration.hasExpectModifier()) {
                        result = false
                        break
                    }
                }
                result
            }

        }
    }
}