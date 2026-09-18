// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.util.descendants
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.KtFile
import java.util.Objects

/**
 * **Important**: This class can only be used on the files and references which are not being actively changed.
 * Doing otherwise (e.g. actively modifying the PSI under the references in [originalReferenceMap]) will
 * result in an undefined behavior.
 */
internal class KtReferencesInCopyMap(originalReferenceMap: Map<KtReference, KtReference>) {

    private val referenceMap: Map<KtReferenceStableKey, KtReference> = originalReferenceMap.mapKeys { KtReferenceStableKey(it.key) }

    /**
     * Provides a stable map key for a [KtReference].
     *
     * Kotlin PSI caches references through soft references. The cache can create a new
     * [KtReference] instance for the same logical reference after garbage collection.
     *
     * [equals] first checks [reference] identity as a fast path, then falls back to a
     * comparison of the reference class, element, range, and resolved names.
     *
     * Note: this class relies on the fact that [reference] and its properties
     * remain the same throughout its lifetime.
     */
    private class KtReferenceStableKey(val reference: KtReference) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is KtReferenceStableKey) return false

            // Fast path - references share the same identity
            if (reference == other.reference) return true

            return reference.javaClass == other.reference.javaClass &&
                    reference.element == other.reference.element &&
                    reference.rangeInElement == other.reference.rangeInElement &&
                    reference.resolvesByNames.toSet() == other.reference.resolvesByNames.toSet()
        }

        override fun hashCode(): Int = Objects.hash(
            reference.javaClass,
            reference.element,
            reference.rangeInElement,
            reference.resolvesByNames.toSet(),
        )
    }

    /**
     * Allows one to quickly navigate from [KtReference]s in the original [KtFile] to corresponding references in its copy.
     *
     * Returns `null` if no references were found for [originalReference].
     */
    fun findReferenceInCopy(originalReference: KtReference): KtReference? {
        val reference = referenceMap[KtReferenceStableKey(originalReference)]

        if (reference == null) {
            LOG.error(buildString {
                appendLine("Reference '${originalReference}' (${System.identityHashCode(originalReference)}) not found")

                val referenceMapRendered = referenceMap.entries.joinToString(";") { (key, value) ->
                    "'${key.reference}' (${System.identityHashCode(key.reference)}) -> '$value' (${System.identityHashCode(value)})"
                }
                appendLine("referenceMap: $referenceMapRendered")
            })
        }

        return reference
    }

    companion object {
        private val LOG: Logger = logger<KtReferencesInCopyMap>()

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
