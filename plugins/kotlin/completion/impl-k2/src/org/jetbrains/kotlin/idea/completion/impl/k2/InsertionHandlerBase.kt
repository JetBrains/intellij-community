// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiDocumentManager
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.lookups.KotlinLookupObject
import org.jetbrains.kotlin.psi.KtFile
import kotlin.reflect.KClass

@Polymorphic
@Serializable
internal abstract class InsertionHandlerBase<LO : KotlinLookupObject>(
    private val lookupObjectClass: KClass<LO>
) : SerializableInsertHandler {

    final override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val ktFile = context.file as? KtFile ?: return
        val lookupObject = item.`object`
        check(lookupObjectClass.isInstance(lookupObject))

        @Suppress("UNCHECKED_CAST")
        handleInsert(context, item, ktFile, lookupObject as LO)
    }

    abstract fun handleInsert(context: InsertionContext, item: LookupElement, ktFile: KtFile, lookupObject: LO)
}


internal fun InsertionContext.doPostponedOperationsAndUnblockDocument() {
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
}
