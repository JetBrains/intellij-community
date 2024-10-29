// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.util.*
import com.intellij.psi.PsiElement

private val unresolvedReferenceKindsKey = Key<List<KotlinUnresolvedReferenceKind>>("KOTLIN_UNRESOLVED_REFERENCE_KINDS")

@IntellijInternalApi
fun PsiElement.registerKotlinUnresolvedReferenceKind(kind: KotlinUnresolvedReferenceKind) {
    this as UserDataHolderEx
    updateUserData(unresolvedReferenceKindsKey) { list -> list.orEmpty() + kind }
}

@IntellijInternalApi
fun PsiElement.clearAllKotlinUnresolvedReferenceKinds() {
    removeUserData(unresolvedReferenceKindsKey)
}

/**
 * Returns all registered [KotlinUnresolvedReferenceKind].
 * Note: [kotlinUnresolvedReferenceKinds] will be cleared on follow-up highlighting passes.
 * However: This extra attribute is only valid with the corresponding highlighting marker, as this user-data
 * might only be updated if necessary.
 */
@IntellijInternalApi
val PsiElement.kotlinUnresolvedReferenceKinds: List<KotlinUnresolvedReferenceKind>
    get() = getUserData(unresolvedReferenceKindsKey).orEmpty()


/**
 * Marker attached to a [PsiElement] during the highlighting pass to decorate an unresolved reference with
 * potential meta-information (such as the expected function signature for a missing delegate function (e.g., getValue, setValue)
 *
 * @see registerKotlinUnresolvedReferenceKind
 * @see clearAllKotlinUnresolvedReferenceKinds
 * @see kotlinUnresolvedReferenceKinds
 */
@IntellijInternalApi
sealed class KotlinUnresolvedReferenceKind {
    /**
     * The unresolved reference is regular (such as, the call expression is missing an import to the called function).
     * e.g.
     * ```kotlin
     * fun main() {
     *     val x = thisFunctionIsNotImported()
     *           //  ^
     *           // Regular
     * }
     * ```
     */
    data object Regular : KotlinUnresolvedReferenceKind()

    /**
     * The code expects a special delegate function (such as 'getValue' or 'setValue'). The function mentioned in the
     * source code is resolved
     *
     * e.g.
     * ```kotlin
     * import remember
     *
     * fun main() {
     *     val x by remember { "" }
     *        // ^
     *        // remember is resolved, but we do not have any 'getValue' method for the 'by' delegation.
     *        // therefore the unresolved reference kind is "UnresolvedDelegateFunction"
     * }
     * ```
     */
    data class UnresolvedDelegateFunction(val expectedFunctionSignature: String) : KotlinUnresolvedReferenceKind()
}
