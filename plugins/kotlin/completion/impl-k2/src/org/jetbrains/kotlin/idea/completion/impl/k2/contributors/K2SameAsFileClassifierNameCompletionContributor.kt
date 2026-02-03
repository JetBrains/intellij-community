// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.util.positionContext.KotlinClassifierNamePositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.renderer.render

internal class K2SameAsFileClassifierNameCompletionContributor : K2SimpleCompletionContributor<KotlinClassifierNamePositionContext>(
    KotlinClassifierNamePositionContext::class
) {

    context(_: KaSession, context: K2CompletionSectionContext<KotlinClassifierNamePositionContext>)
    override fun complete() {
        (context.positionContext.classLikeDeclaration as? KtClassOrObject)?.let { completeTopLevelClassName(it) }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinClassifierNamePositionContext>)
    private fun completeTopLevelClassName(classOrObject: KtClassOrObject) {
        if (!classOrObject.isTopLevel()) return
        val name = context.completionContext.originalFile.virtualFile.nameWithoutExtension
        if (!isValidUpperCapitalizedClassName(name)) return
        if (context.completionContext.originalFile.declarations.any { it is KtClassOrObject && it.name == name }) return
        addElement(LookupElementBuilder.create(name))
    }

    private fun isValidUpperCapitalizedClassName(name: String) =
        Name.isValidIdentifier(name) && Name.identifier(name).render() == name && name[0].isUpperCase()
}
