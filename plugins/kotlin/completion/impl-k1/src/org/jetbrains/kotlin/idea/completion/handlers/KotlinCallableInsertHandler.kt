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
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.DescriptorUtils

abstract class KotlinCallableInsertHandler(val callType: CallType<*>) : BaseDeclarationInsertHandler() {
    companion object {
        val SHORTEN_REFERENCES = ShortenReferences { ShortenReferences.Options.DEFAULT.copy(dropBracesInStringTemplates = false) }

        fun addImport(context: InsertionContext, item: LookupElement, callType: CallType<*>) {
            val project = context.project
            val psiDocumentManager = PsiDocumentManager.getInstance(project)
            psiDocumentManager.commitDocument(context.document)

            val file = context.file as? KtFile ?: return
            val lookupObject = item.`object` as? DescriptorBasedDeclarationLookupObject ?: return
            val descriptor = lookupObject.descriptor as? CallableDescriptor ?: return
            if (descriptor.isArtificialImportAliasedDescriptor) return

            if (cannotBeFullyQualified(callType, descriptor)) {
                if (DescriptorUtils.isTopLevelDeclaration(descriptor)) {
                    ImportInsertHelper.getInstance(project).importDescriptor(file, descriptor)
                }
            } else if (callType == CallType.DEFAULT) {
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

        /**
         * Determines that the declaration cannot be called with a full qualified name (FQN):
         *  - All callable references
         *  - Extension properties/functions
         *  - Root declarations
         */
        private fun cannotBeFullyQualified(callType: CallType<*>, descriptor: CallableDescriptor): Boolean {
            return callType is CallType.CallableReference ||
                    descriptor.extensionReceiverParameter != null ||
                    descriptor.importableFqName?.parentOrNull()?.isRoot == true
        }
    }

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        super.handleInsert(context, item)

        addImport(context, item, callType)
    }
}

