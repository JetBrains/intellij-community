// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.copyPaste

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.importableFqName
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbols
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveCompanionObjectShortReferenceToContainingClassSymbol
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry
import kotlin.collections.flatMap

internal val KaSymbol.isExtension: Boolean get() = this is KaCallableSymbol && isExtension

internal inline fun <reified T : KtElement> KtFile.collectElementsOfTypeInRange(startOffset: Int, endOffset: Int): List<T> =
    elementsInRange(TextRange(startOffset, endOffset)).flatMap { it.collectDescendantsOfType<T>() }

internal fun TextRange.toElementInFile(file: KtFile): KtElement? = file.findElementAt(startOffset)
    ?.parentsOfType<KtElement>(withSelf = true)
    ?.takeWhile { it.textRange in this }
    ?.firstOrNull { it.textRange == this }

internal fun <T> Collection<T>.toSortedStringSet(): Set<String> = map { it.toString() }.toSortedSet()

/**
 * In the resulting map symbols that cannot be imported (e.g., local symbols) are associated with `null` key.
 */
context(_: KaSession)
@OptIn(KaIdeApi::class)
internal fun KtReference.getResolvedSymbolsGroupedByImportableFqName(): Map<FqName?, List<KaSymbol>> = resolveToImportableSymbols()
    .groupBy { symbol -> symbol.importableFqName }

context(_: KaSession)
private fun KtReference.resolveToImportableSymbols(): Collection<KaSymbol> =
    resolveCompanionObjectShortReferenceToContainingClassSymbol()?.let { listOf(it) } ?: resolveToSymbols()

internal fun checkRangeIsProper(startOffset: Int, endOffset: Int, file: KtFile) {
    if (!TextRange.isProperRange(startOffset, endOffset)) {
        errorWithAttachment("Incorrect range") {
            withPsiEntry("file", file)
            withEntry("startOffset", startOffset.toString())
            withEntry("endOffset", endOffset.toString())
        }
    }
}
