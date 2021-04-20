/*
 * Copyright 2010-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
