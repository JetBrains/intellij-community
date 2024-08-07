// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.lookups.factories

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import org.jetbrains.kotlin.idea.base.util.letIf
import org.jetbrains.kotlin.idea.completion.contributors.helpers.insertStringAndInvokeCompletion
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.render

internal object PackagePartLookupElementFactory {

    fun createLookup(packagePartFqName: FqName): LookupElement {
        val shortName = packagePartFqName.shortName()
        return LookupElementBuilder.create(PackagePartLookupObject(shortName), "${shortName.render()}.")
            .withInsertHandler(PackagePartInsertionHandler)
            .withIcon(AllIcons.Nodes.Package)
            .letIf(!packagePartFqName.parent().isRoot) {
                it.appendTailText(" (${packagePartFqName.asString()})", true)
            }
    }
}


internal data class PackagePartLookupObject(
    override val shortName: Name,
) : KotlinLookupObject


private object PackagePartInsertionHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val lookupElement = item.`object` as PackagePartLookupObject
        val name = lookupElement.shortName.render()
        context.document.replaceString(context.startOffset, context.tailOffset, name)
        context.commitDocument()
        context.insertStringAndInvokeCompletion(stringToInsert = ".")
    }
}

