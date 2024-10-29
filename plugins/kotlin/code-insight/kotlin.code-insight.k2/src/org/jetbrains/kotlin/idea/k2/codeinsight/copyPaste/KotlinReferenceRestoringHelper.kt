// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.copyPaste

import com.intellij.openapi.util.TextRange
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaSymbolBasedReference
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
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.castAll
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import kotlin.collections.flatMap

internal object KotlinReferenceRestoringHelper {
    @OptIn(KaImplementationDetail::class)
    fun collectSourceReferenceInfos(sourceFile: KtFile, startOffsets: IntArray, endOffsets: IntArray): List<KotlinSourceReferenceInfo> {
        var currentStartOffsetInPastedText = 0

        val sourceReferenceInfos = startOffsets.zip(endOffsets).flatMap { (startOffset, endOffset) ->
            // delta between the source text offset and the offset in the text to be pasted
            val deltaBetweenStartOffsets = currentStartOffsetInPastedText - startOffset

            val elements = sourceFile.collectElementsOfTypeInRange<KtElement>(startOffset, endOffset)
                .filterNot { it is KtSimpleNameExpression && !it.canBeUsedInImport() }
                .filter { it.mainReference is KaSymbolBasedReference }

            val infos = elements.map { element ->
                KotlinSourceReferenceInfo(
                    rangeInSource = element.textRange,
                    rangeInTextToBePasted = element.textRange.shiftRight(deltaBetweenStartOffsets)
                )
            }

            // add 1 to take into account separators between blocks in case of multi-caret selection
            currentStartOffsetInPastedText += endOffset - startOffset + 1

            infos
        }

        return sourceReferenceInfos
    }

    /**
     * Note that the contents of [sourceFile] are expected to be the same as they were at the moment of [sourceReferenceInfos]' collection.
     */
    fun findSourceReferencesInTargetFile(
        sourceFile: KtFile,
        sourceReferenceInfos: List<KotlinSourceReferenceInfo>,
        targetFile: KtFile,
        targetOffset: Int,
    ): List<KotlinSourceReferenceInTargetFile> = sourceReferenceInfos.mapNotNull { (rangeInSource, rangeInTextToBePasted) ->
        val sourceElement = rangeInSource.toElementInFile(sourceFile)
            ?: errorWithAttachment("Failed to obtain sourceElement") { withEntry("sourceText", rangeInSource.substring(sourceFile.text)) }

        val targetElementRange = rangeInTextToBePasted.shiftRight(targetOffset)
        val targetElement = targetElementRange.toElementInFile(targetFile)

        targetElement?.let { KotlinSourceReferenceInTargetFile(sourceElement, it.createSmartPointer()) }
    }

    /**
     * Note that the contents of [sourceFile] are expected to be the same as they were at the moment of [sourceReferenceInfos]' collection.
     */
    context(KaSession)
    fun getResolvedSourceReferencesThatMightRequireRestoring(
        sourceFile: KtFile,
        sourceReferenceInfos: List<KotlinSourceReferenceInfo>,
        sourceRanges: List<TextRange>,
    ): List<KotlinResolvedSourceReference> = sourceReferenceInfos.mapNotNull { (rangeInSource, _) ->
        val sourceElement = rangeInSource.toElementInFile(sourceFile)
        val sourceReference = sourceElement?.mainReference
            ?: errorWithAttachment("Failed to obtain sourceReference") { withEntry("sourceText", rangeInSource.substring(sourceFile.text)) }

        val receiverExpression = (rangeInSource.toElementInFile(sourceFile) as? KtSimpleNameExpression)?.getReceiverExpression()

        // resolve to all symbols instead of trying to resolve to a single one to cover cases with:
        // - multi-references
        // - ambiguity errors (if the source has an ambiguity error and candidates have the same `fqName`, import the `fqName` in question)
        val sourceSymbolsGroupedByFqName = sourceReference.getResolvedSymbolsGroupedByImportableFqName()
        if (sourceSymbolsGroupedByFqName.size > 1 && sourceReference !is KtMultiReference<*>) return@mapNotNull null

        val isReferenceQualifiable = sourceSymbolsGroupedByFqName.values.singleOrNull()?.any { !it.isExtension } == true

        @OptIn(UnsafeCastFunction::class)
        val sourceFqNames = sourceSymbolsGroupedByFqName
            .filterKeys { fqName -> fqName != null }
            .filterValues { symbols -> symbols.any { symbolMightRequireRestoring(it, receiverExpression, sourceFile, sourceRanges) } }
            .keys.castAll<FqName>().toList()

        if (sourceFqNames.isEmpty()) return@mapNotNull null

        KotlinResolvedSourceReference(sourceElement, sourceFqNames, isReferenceQualifiable)
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
        val resolvedSourceReferencesMap = resolvedSourceReferences.associateBy { it.sourceElement }

        return sourceReferencesInTargetFile.flatMap { (sourceElement, targetElementPointer) ->
            val targetReference = targetElementPointer.element?.mainReference ?: return@flatMap emptyList()
            val (_, sourceFqNames, isReferenceQualifiable) = resolvedSourceReferencesMap[sourceElement] ?: return@flatMap emptyList()

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
        sourceFile: KtFile,
        sourceRanges: List<TextRange>,
    ): Boolean {
        val psi = symbol.psi?.takeIf { it.isValid }
        if (psi != null && psi.containingFile == sourceFile && sourceRanges.any { psi.textRange in it }) return false

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
        sourceFqName in targetFqNames -> {
            null
        }

        // target reference is resolved to a symbol with different/with no fq-name, so we might need to add a qualifier instead of an import
        // TODO: maybe warn user if the reference is not qualifiable and adding an import affects other usages
        isReferenceQualifiable && sourceFqName.shortName() in targetShortNames -> {
            ReferenceToBindToFqName(sourceFqName, targetReference as KtSimpleNameReference)
        }

        isAccessible(targetReference, sourceFqName) -> ReferenceToImport(sourceFqName)

        else -> null
    }

    context(KaSession)
    private fun isAccessible(
        targetReference: KtReference,
        sourceFqName: FqName
    ): Boolean {
        val project = targetReference.element.project
        val importedReference =
            org.jetbrains.kotlin.psi.KtPsiFactory(project).createImportDirective(ImportPath(sourceFqName, false)).importedReference
        val reference = importedReference?.getQualifiedElementSelector()?.mainReference ?: return false
        val symbols = reference.resolveToSymbols()
        return symbols.isNotEmpty()
    }
}