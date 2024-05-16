// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.copyPaste

import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtSymbolBasedReference
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.base.psi.canBeUsedInImport
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeUsedAsExtension
import org.jetbrains.kotlin.idea.references.KtMultiReference
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.castAll
import kotlin.collections.flatMap

internal object KotlinReferenceRestoringHelper {
    fun collectSourceReferences(sourceFile: KtFile, startOffsets: IntArray, endOffsets: IntArray): List<KotlinSourceReferenceWithRange> {
        var currentStartOffsetInPastedText = 0

        val elementsWithRanges = startOffsets.zip(endOffsets).flatMap { (startOffset, endOffset) ->
            // delta between the source text offset and the offset in the text to be pasted
            val deltaBetweenStartOffsets = currentStartOffsetInPastedText - startOffset
            val elementsWithRanges = sourceFile.collectElementsOfTypeInRange<KtElement>(startOffset, endOffset)
                .map { it to it.textRange.shiftRight(deltaBetweenStartOffsets) }

            // add 1 to take into account separators between blocks in case of multi-caret selection
            currentStartOffsetInPastedText += endOffset - startOffset + 1

            elementsWithRanges
        }

        val sourceReferencesWithRanges = elementsWithRanges.mapNotNull { (element, range) ->
            val reference = when (element) {
                is KtSimpleNameExpression -> if (element.canBeUsedInImport()) element.mainReference else null
                else -> element.mainReference
            }
            reference?.takeIf { it is KtSymbolBasedReference }?.let { KotlinSourceReferenceWithRange(it, range) }
        }

        return sourceReferencesWithRanges
    }

    fun collectSourceDeclarations(sourceFile: KtFile, startOffsets: IntArray, endOffsets: IntArray): List<KtDeclaration> =
        startOffsets.zip(endOffsets).flatMap { (startOffset, endOffset) ->
            sourceFile.collectElementsOfTypeInRange(startOffset, endOffset)
        }

    fun findSourceReferencesInTargetFile(
        sourceReferencesWithRanges: List<KotlinSourceReferenceWithRange>,
        targetFile: KtFile,
        targetOffset: Int,
    ): List<KotlinSourceReferenceInTargetFile> = sourceReferencesWithRanges.mapNotNull { (sourceReference, rangeInTextToBePasted) ->
        val targetElementRange = rangeInTextToBePasted.shiftRight(targetOffset)
        val targetElement = targetFile.findElementAt(targetElementRange.startOffset)
            ?.parentsOfType(sourceReference.element::class.java, withSelf = true)
            ?.takeWhile { it.textRange in targetElementRange }
            ?.firstOrNull { it.textRange == targetElementRange }

        targetElement?.let { KotlinSourceReferenceInTargetFile(sourceReference, it.createSmartPointer()) }
    }

    context(KtAnalysisSession)
    fun getResolvedSourceReferencesThatMightRequireRestoring(
        sourceReferences: List<KotlinSourceReferenceWithRange>,
        sourceDeclarations: Set<KtDeclaration>,
    ): List<KotlinResolvedSourceReference> = sourceReferences.mapNotNull { (sourceReference, _) ->
        val receiverExpression = (sourceReference.element as? KtSimpleNameExpression)?.getReceiverExpression()

        // resolve to all symbols instead of trying to resolve to a single one to cover cases with:
        // - multi-references
        // - ambiguity errors (if the source has an ambiguity error and candidates have the same `fqName`, import the `fqName` in question)
        val sourceSymbolsGroupedByFqName = sourceReference.getResolvedSymbolsGroupedByImportableFqName()
        if (sourceSymbolsGroupedByFqName.size > 1 && sourceReference !is KtMultiReference<*>) return@mapNotNull null

        val isReferenceQualifiable = sourceSymbolsGroupedByFqName.values.singleOrNull()?.any { !it.isExtension } == true

        @OptIn(UnsafeCastFunction::class)
        val sourceFqNames = sourceSymbolsGroupedByFqName
            .filterKeys { fqName -> fqName != null }
            .filterValues { symbols -> symbols.any { symbolMightRequireRestoring(it, receiverExpression, sourceDeclarations) } }
            .keys.castAll<FqName>().toList()

        if (sourceFqNames.isEmpty()) return@mapNotNull null

        KotlinResolvedSourceReference(sourceReference, sourceFqNames, isReferenceQualifiable)
    }

    sealed class ReferenceToRestore {
        abstract val fqName: FqName
    }

    data class ReferenceToImport(
        override val fqName: FqName,
    ) : ReferenceToRestore() {
        override fun toString(): String = fqName.asString()
    }

    data class ReferenceToBindToFqName(
        override val fqName: FqName,
        val reference: KtSimpleNameReference,
    ) : ReferenceToRestore() {
        override fun toString(): String = fqName.asString()
    }

    context(KaSession)
    fun getTargetReferencesToRestore(
        sourceReferencesInTargetFile: List<KotlinSourceReferenceInTargetFile>,
        resolvedSourceReferences: List<KotlinResolvedSourceReference>,
    ): List<ReferenceToRestore> {
        val resolvedSourceReferencesMap = resolvedSourceReferences.associateBy { it.sourceReference }

        return sourceReferencesInTargetFile.flatMap { (sourceReference, targetElementPointer) ->
            val targetReference = targetElementPointer.element?.mainReference ?: return@flatMap emptyList()
            val (_, sourceFqNames, isReferenceQualifiable) = resolvedSourceReferencesMap[sourceReference] ?: return@flatMap emptyList()

            val targetSymbolsGroupedByFqName = targetReference.getResolvedSymbolsGroupedByImportableFqName()
            val targetFqNames = targetSymbolsGroupedByFqName.keys.filterNotNull().toSet()
            val targetShortNames = targetSymbolsGroupedByFqName.values.flatten().mapNotNull { (it as? KaNamedSymbol)?.name }.toSet()

            sourceFqNames.mapNotNull { sourceFqName ->
                buildTargetReferenceToRestore(sourceFqName, isReferenceQualifiable, targetReference, targetFqNames, targetShortNames)
            }
        }
    }

    fun restoreReference(targetFile: KtFile, referenceToRestore: ReferenceToRestore) {
        when (referenceToRestore) {
            is ReferenceToImport -> targetFile.addImport(referenceToRestore.fqName)
            is ReferenceToBindToFqName -> referenceToRestore.reference.bindToFqName(referenceToRestore.fqName)
        }
    }

    context(KaSession)
    private fun symbolMightRequireRestoring(
        symbol: KaSymbol,
        receiverExpression: KtExpression?,
        sourceDeclarations: Set<KtDeclaration>,
    ): Boolean {
        if (symbol.psi in sourceDeclarations) return false

        if (receiverExpression != null && (symbol as? KaCallableSymbol)?.canBeUsedAsExtension() != true) return false

        return true
    }

    context(KaSession)
    private fun buildTargetReferenceToRestore(
        sourceFqName: FqName,
        isReferenceQualifiable: Boolean,
        targetReference: KtReference,
        targetFqNames: Set<FqName>,
        targetShortNames: Set<Name>
    ): ReferenceToRestore? = when {
        sourceFqName in targetFqNames -> null

        // target reference is resolved to a symbol with different/with no fq-name, so we might need to add a qualifier instead of an import
        // TODO: maybe warn user if the reference is not qualifiable and adding an import affects other usages
        isReferenceQualifiable && sourceFqName.shortName() in targetShortNames ->
            ReferenceToBindToFqName(sourceFqName, targetReference as KtSimpleNameReference)

        else -> ReferenceToImport(sourceFqName)
    }
}