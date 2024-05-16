// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon.weighers

import com.intellij.psi.PsiElement
import com.intellij.psi.util.ProximityLocation
import com.intellij.psi.util.proximity.ProximityWeigher
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.psi.KtElement

@ApiStatus.Internal
class PreferKotlinxLibrariesWeigher : ProximityWeigher() {
    // The order of weights for the ProximityWeigher are the opposite way of the ones we use in Kotlin
    private enum class Weight {
        OTHER,
        KOTLINX,
    }

    /**
     * This is a set of contained packages that we want to prefer over their Java counterparts.
     * Java marks packages from java.time and similar as special preferred packages, so we do the same here for Kotlin.
     * We do it before Java to ensure they are preferred over Java.
     * We only check for exact package matches of elements to include. Sub-packages are ignored and would have to be listed separately.
     */
    private val preferredPackages = setOf(
        "kotlinx.datetime"
    )

    override fun weigh(element: PsiElement, location: ProximityLocation): Comparable<*>? {
        // Only enable this weigher if we are editing a Kotlin file
        if (location.position?.language != KotlinLanguage.INSTANCE) {
            return null
        }
        val ktElement = element as? KtElement ?: return Weight.OTHER
        val fqn = ktElement.kotlinFqName ?: return Weight.OTHER
        val packageFqn = fqn.asString().substringBeforeLast('.')
        if (packageFqn !in preferredPackages) return Weight.OTHER
        return Weight.KOTLINX
    }
}