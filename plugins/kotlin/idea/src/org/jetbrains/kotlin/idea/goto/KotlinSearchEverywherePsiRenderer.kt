// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.goto

import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy

@Service
internal class KotlinSearchEverywherePsiRenderer(project: Project) :
    SearchEverywherePsiRenderer(KotlinPluginDisposable.getInstance(project)) {
    companion object {
        private val RENDERER = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.withOptions {
            parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
            modifiers = emptySet()
            startFromName = false
        }

        fun getInstance(project: Project): KotlinSearchEverywherePsiRenderer = project.service()
    }

    override fun getElementText(element: PsiElement?): String {
        if (element !is KtNamedFunction) return super.getElementText(element)
        val descriptor = element.resolveToDescriptorIfAny() ?: return ""
        return buildString {
            descriptor.extensionReceiverParameter?.let { append(RENDERER.renderType(it.type)).append('.') }
            append(element.name)
            descriptor.valueParameters.joinTo(this, prefix = "(", postfix = ")") { RENDERER.renderType(it.type) }
        }
    }
}
