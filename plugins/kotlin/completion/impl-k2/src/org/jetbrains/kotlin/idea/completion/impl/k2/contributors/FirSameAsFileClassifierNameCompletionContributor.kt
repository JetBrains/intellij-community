// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinClassifierNamePositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.renderer.render

internal class FirSameAsFileClassifierNameCompletionContributor(
    parameters: KotlinFirCompletionParameters,
    sink: LookupElementSink,
    priority: Int = 0,
) : FirCompletionContributorBase<KotlinClassifierNamePositionContext>(parameters, sink, priority) {

    context(KaSession)
    override fun complete(
        positionContext: KotlinClassifierNamePositionContext,
        weighingContext: WeighingContext,
    ) {
        (positionContext.classLikeDeclaration as? KtClassOrObject)?.let { completeTopLevelClassName(it) }
    }

    private fun completeTopLevelClassName(classOrObject: KtClassOrObject) {
        if (!classOrObject.isTopLevel()) return
        val name = originalKtFile.virtualFile.nameWithoutExtension
        if (!isValidUpperCapitalizedClassName(name)) return
        if (originalKtFile.declarations.any { it is KtClassOrObject && it.name == name }) return
        sink.addElement(LookupElementBuilder.create(name))
    }

    private fun isValidUpperCapitalizedClassName(name: String) =
        Name.isValidIdentifier(name) && Name.identifier(name).render() == name && name[0].isUpperCase()
}
