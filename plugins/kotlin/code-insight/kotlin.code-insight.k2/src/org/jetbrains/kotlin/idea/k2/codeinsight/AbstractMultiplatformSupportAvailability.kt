// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import com.intellij.psi.util.parents
import org.jetbrains.kotlin.idea.base.facet.JvmOnlyProjectChecker
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinSupportAvailability
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier

internal abstract class AbstractMultiplatformSupportAvailability : KotlinSupportAvailability {

    protected abstract fun isEnabledByRegistry(): Boolean

    protected abstract fun isSupportedPlatform(platform: TargetPlatform): Boolean

    final override fun isSupported(ktElement: KtElement): Boolean {
        if (isUnitTestMode()) {
            // we want to test KMP in our unit tests
            return true
        }
        if (isEnabledByRegistry() || JvmOnlyProjectChecker.getInstance(ktElement.project).value()) return true
        return when (ktElement) {
            is KtFile -> {
                val platform = ktElement.platform
                isSupportedPlatform(platform)
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

