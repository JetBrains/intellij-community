// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.handlers

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.base.analysis.withRootPrefixIfNeeded
import org.jetbrains.kotlin.idea.completion.isArtificialImportAliasedDescriptor
import org.jetbrains.kotlin.idea.completion.shortenReferences
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.completion.DescriptorBasedDeclarationLookupObject
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.util.CallType
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.DescriptorUtils

abstract class KotlinCallableInsertHandler(val callType: CallType<*>) : BaseDeclarationInsertHandler() {
    companion object {
        val SHORTEN_REFERENCES = ShortenReferences { ShortenReferences.Options.DEFAULT.copy(dropBracesInStringTemplates = false) }

        fun addImport(context: InsertionContext, item: LookupElement, callType: CallType<*>) {
            val psiDocumentManager = PsiDocumentManager.getInstance(context.project)
            psiDocumentManager.commitDocument(context.document)

            val file = context.file
            val o = item.`object`
            if (file is KtFile && o is DescriptorBasedDeclarationLookupObject) {
                val descriptor = o.descriptor as? CallableDescriptor ?: return
                if (descriptor.extensionReceiverParameter != null || callType is CallType.CallableReference) {
                    if (DescriptorUtils.isTopLevelDeclaration(descriptor) && !descriptor.isArtificialImportAliasedDescriptor) {
                        ImportInsertHelper.getInstance(context.project).importDescriptor(file, descriptor)
                    }
                } else if (callType == CallType.DEFAULT) {
                    if (descriptor.isArtificialImportAliasedDescriptor) return
                    val fqName = descriptor.importableFqName ?: return
                    context.document.replaceString(
                        context.startOffset,
                        context.tailOffset,
                        fqName.withRootPrefixIfNeeded().render() + " "
                    ) // insert space after for correct parsing

                    psiDocumentManager.commitDocument(context.document)

                    shortenReferences(context, context.startOffset, context.tailOffset - 1, SHORTEN_REFERENCES)

                    psiDocumentManager.doPostponedOperationsAndUnblockDocument(context.document)

                    // delete space
                    if (context.document.isTextAt(context.tailOffset - 1, " ")) { // sometimes space can be lost because of reformatting
                        context.document.deleteString(context.tailOffset - 1, context.tailOffset)
                    }
                }
            }
        }
    }

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        super.handleInsert(context, item)

        addImport(context, item, callType)
    }
}

