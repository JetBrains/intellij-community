// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtNamedFunction

@ApiStatus.Internal
class KotlinProbablyInjectedFunctionShortNameIndex internal constructor() : StringStubIndexExtension<KtNamedFunction>() {
    companion object Helper : KotlinStringStubIndexHelper<KtNamedFunction>(KtNamedFunction::class.java) {
        override val indexKey: StubIndexKey<String, KtNamedFunction> =
            StubIndexKey.createIndexKey(KotlinProbablyInjectedFunctionShortNameIndex::class.java.simpleName)
    }

    override fun getKey(): StubIndexKey<String, KtNamedFunction> = indexKey

    override fun traceKeyHashToVirtualFileMapping(): Boolean = true
}