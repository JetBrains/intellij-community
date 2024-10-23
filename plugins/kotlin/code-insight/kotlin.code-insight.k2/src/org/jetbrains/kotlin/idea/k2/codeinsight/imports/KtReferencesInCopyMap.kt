// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import com.intellij.psi.util.descendants
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.KtFile

internal class KtReferencesInCopyMap(
    private val referenceMap: Map<KtReference, KtReference>
) {

    /**
     * Allows one to quickly navigate from [KtReference]s in the original [KtFile] to corresponding references in its copy.
     *
     * Throws [NoSuchElementException] if no references found for [originalReference].
     */
    fun findReferenceInCopy(originalReference: KtReference): KtReference {
        return referenceMap.getValue(originalReference)
    }

    companion object {
        /**
         * Populates [KtReferencesInCopyMap] with references from [originalFile] and corresponding references from [copyFile].
         *
         * Expects the [originalFile] and [copyFile] to be mostly the same.
         */
        fun createFor(originalFile: KtFile, copyFile: KtFile): KtReferencesInCopyMap {
            // TODO unify reference processing with code from UsedReferencesCollector
            val referenceMap = originalFile.descendants().zip(copyFile.descendants())
                .filterNot { (original, _) -> original.ignoreReferencesDuringImportOptimization }
                .flatMap { (original, copy) ->
                    val originals = original.references.filterIsInstance<KtReference>()
                    val copies = copy.references.filterIsInstance<KtReference>()

                    originals zip copies
                }
                .toMap()

            return KtReferencesInCopyMap(referenceMap)
        }
    }
}