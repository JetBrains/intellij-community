// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.goto

import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.types.Variance

@Service(Service.Level.PROJECT)
internal class KotlinSearchEverywherePsiRenderer(project: Project) :
    SearchEverywherePsiRenderer(KotlinPluginDisposable.getInstance(project)) {
    companion object {
        fun getInstance(project: Project): KotlinSearchEverywherePsiRenderer = project.service()
    }

    override fun getElementText(element: PsiElement?): String {
        if (element !is KtNamedFunction) return super.getElementText(element)
        return analyze(element) {
            val declarationSymbol = element.getSymbol() as? KtFunctionLikeSymbol ?: return@analyze super.getElementText(element)
            buildString {
                declarationSymbol.receiverParameter?.let {
                    append(it.type.render(KtTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT))
                    append('.')
                }
                append(element.name)
                declarationSymbol.valueParameters.joinTo(this, prefix = "(", postfix = ")") {
                    it.returnType.render(
                        KtTypeRendererForSource.WITH_SHORT_NAMES,
                        position = Variance.INVARIANT
                    )
                }
            }
        }
    }
}
