// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry

/**
 * Entries of destructuring declarations declared directly in a file (not in a class or a script) by their fully qualified names.
 * Like top-level properties, such entries are top-level callables of the package.
 */
class KotlinTopLevelDestructuringDeclarationEntryFqNameIndex internal constructor() :
    StringStubIndexExtension<KtDestructuringDeclarationEntry>() {

    companion object Helper : KotlinStringStubIndexHelper<KtDestructuringDeclarationEntry>(KtDestructuringDeclarationEntry::class.java) {
        override val indexKey: StubIndexKey<String, KtDestructuringDeclarationEntry> =
            StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelDestructuringDeclarationEntryFqNameIndex")
    }

    override fun getKey(): StubIndexKey<String, KtDestructuringDeclarationEntry> = indexKey

    override fun traceKeyHashToVirtualFileMapping(): Boolean = true
}
