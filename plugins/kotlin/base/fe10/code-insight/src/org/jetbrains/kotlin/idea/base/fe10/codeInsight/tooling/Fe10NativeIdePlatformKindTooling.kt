// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight.tooling

import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractNativeIdePlatformKindTooling
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.highlighter.KotlinTestRunLineMarkerContributor
import org.jetbrains.kotlin.idea.testIntegration.genericKotlinTestUrls
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import javax.swing.Icon

private class Fe10NativeIdePlatformKindTooling : AbstractNativeIdePlatformKindTooling() {
    override val testIconProvider: AbstractGenericTestIconProvider
        get() = Fe10GenericTestIconProvider

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean {
        return function.isMainFunction() && super.acceptsAsEntryPoint(function)
    }

    @OptIn(IntellijInternalApi::class)
    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        if (!allowSlowOperations) {
            return null
        }

        val testContainerElement = testIconProvider.getTestContainerElement(declaration) ?: return null
        if (!testIconProvider.isKotlinTestDeclaration(testContainerElement)) {
            return null
        }

        val descriptor = declaration.resolveToDescriptorIfAny() ?: return null
        if (!Fe10GenericTestIconProvider.isKotlinTestDeclaration(descriptor)) return null

        return KotlinTestRunLineMarkerContributor.getTestStateIcon(declaration.genericKotlinTestUrls(), declaration)
    }
}

@ApiStatus.Internal
fun KtElement.isMainFunction(computedDescriptor: DeclarationDescriptor? = null): Boolean {
    if (this !is KtNamedFunction) return false
    val mainFunctionDetector = MainFunctionDetector(this.languageVersionSettings) { it.resolveToDescriptorIfAny() }

    if (computedDescriptor != null) return mainFunctionDetector.isMain(computedDescriptor)

    return mainFunctionDetector.isMain(this)
}