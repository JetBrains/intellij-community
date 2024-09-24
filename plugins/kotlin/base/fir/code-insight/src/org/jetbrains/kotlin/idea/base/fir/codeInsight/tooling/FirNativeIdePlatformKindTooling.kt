// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight.tooling

import org.jetbrains.kotlin.idea.base.codeInsight.PsiOnlyKotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractNativeIdePlatformKindTooling
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.highlighter.KotlinTestRunLineMarkerContributor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import javax.swing.Icon

internal class FirNativeIdePlatformKindTooling : AbstractNativeIdePlatformKindTooling() {

    override val testIconProvider: AbstractGenericTestIconProvider
        get() = SymbolBasedGenericTestIconProvider

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean {
        return function is KtNamedFunction
                && PsiOnlyKotlinMainFunctionDetector.isMain(function)
                && super.acceptsAsEntryPoint(function)
    }

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        if (!allowSlowOperations) {
            return null
        }

        val testContainerElement = testIconProvider.getTestContainerElement(declaration) ?: return null
        if (!testIconProvider.isKotlinTestDeclaration(testContainerElement)) {
            return null
        }

        val module = declaration.module ?: return null

        /**
         * Find all target names which are expected to run this test.
         * For example, consider running a test in 'nativeTest', then we might expect
         * macosX64, macosArm64, linuxX64, ... target to potentially execute this test class.
         */
        val targetNames = listOf(module, *module.implementingModules.toTypedArray())
            .filter { it.implementingModules.isEmpty() }
            .map { module -> module.name.substringAfterLast(".").removeSuffix("Test") }


        val urls = when (declaration) {
            is KtClassOrObject -> {
                listOf("java:suite://${declaration.fqName?.asString()}")
            }

            is KtNamedFunction -> {
                val containingClass = declaration.containingClass()
                val baseName = "java:test://${containingClass?.fqName?.asString()}.${declaration.name}"
                targetNames.map { targetName -> "$baseName[$targetName]" } + baseName
            }

            else -> return null
        }

        return KotlinTestRunLineMarkerContributor.getTestStateIcon(urls, declaration)
    }
}