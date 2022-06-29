// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("JvmIdePlatformKindToolingUtils")

package org.jetbrains.kotlin.idea.base.fe10.codeInsight.tooling

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptorWithResolutionScopes
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinTestAvailabilityChecker
import org.jetbrains.kotlin.idea.base.codeInsight.isFrameworkAvailable
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractJvmIdePlatformKindTooling
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.highlighter.KotlinTestRunLineMarkerContributor.Companion.getTestStateIcon
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFramework
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import javax.swing.Icon

class Fe10JvmIdePlatformKindTooling : AbstractJvmIdePlatformKindTooling() {
    private fun calculateUrls(declaration: KtNamedDeclaration, includeSlowProviders: Boolean? = null): List<String>? {
        val testFramework = KotlinTestFramework.getApplicableFor(declaration, includeSlowProviders?.takeUnless { it }) ?: return null

        val relevantProvider = includeSlowProviders == null || includeSlowProviders == testFramework.isSlow
        if (!relevantProvider) return null

        val qualifiedName = testFramework.qualifiedName(declaration) ?: return null
        return when (declaration) {
            is KtClassOrObject -> listOf("java:suite://$qualifiedName")
            is KtNamedFunction -> listOf(
                "java:test://$qualifiedName/${declaration.name}",
                "java:test://$qualifiedName.${declaration.name}"
            )
            else -> null
        }
    }

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        val urls = calculateUrls(declaration, allowSlowOperations)

        if (urls != null) {
            return getTestStateIcon(urls, declaration)
        } else if (allowSlowOperations) {
            return getGenericTestIcon(declaration, { declaration.resolveToDescriptorIfAny() }) { emptyList() }
        }

        return null
    }
}

fun getGenericTestIcon(
    declaration: KtNamedDeclaration,
    descriptorProvider: () -> DeclarationDescriptor?,
    initialLocations: () -> List<String>?
): Icon? {
    val locations = initialLocations()?.toMutableList() ?: return null

    if (!isFrameworkAvailable<KotlinTestAvailabilityChecker>(declaration)) {
        return null
    }

    val clazz = when (declaration) {
        is KtClassOrObject -> declaration
        is KtNamedFunction -> declaration.containingClassOrObject ?: return null
        else -> return null
    }

    val descriptor = descriptorProvider() ?: return null
    if (!descriptor.isKotlinTestDeclaration()) return null

    locations += clazz.parentsWithSelf.filterIsInstance<KtNamedDeclaration>()
        .mapNotNull { it.name }
        .toList().asReversed()

    val testName = (declaration as? KtNamedFunction)?.name
    if (testName != null) {
        locations += "$testName"
    }

    val prefix = if (testName != null) "test://" else "suite://"
    val url = prefix + locations.joinWithEscape('.')

    return getTestStateIcon(listOf("java:$url", url), declaration)
}

private tailrec fun DeclarationDescriptor.isIgnored(): Boolean {
    if (annotations.any { it.fqName == KotlinTestAvailabilityChecker.IGNORE_FQ_NAME }) {
        return true
    }

    val containingClass = containingDeclaration as? ClassDescriptor ?: return false
    return containingClass.isIgnored()
}

fun DeclarationDescriptor.isKotlinTestDeclaration(): Boolean {
    if (isIgnored()) {
        return false
    }

    if (annotations.any { it.fqName == KotlinTestAvailabilityChecker.TEST_FQ_NAME }) {
        return true
    }

    val classDescriptor = this as? ClassDescriptorWithResolutionScopes ?: return false
    return classDescriptor.declaredCallableMembers.any { it.isKotlinTestDeclaration() }
}

private fun Collection<String>.joinWithEscape(delimiterChar: Char): String {
    if (isEmpty()) return ""

    val expectedSize = sumOf { it.length } + size - 1
    val out = StringBuilder(expectedSize)
    var first = true
    for (s in this) {
        if (!first) {
            out.append(delimiterChar)
        }
        first = false
        for (ch in s) {
            if (ch == delimiterChar || ch == '\\') {
                out.append('\\')
            }
            out.append(ch)
        }
    }
    return out.toString()
}