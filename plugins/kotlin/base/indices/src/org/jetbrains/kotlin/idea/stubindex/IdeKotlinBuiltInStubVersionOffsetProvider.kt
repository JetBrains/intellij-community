// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.stubindex

import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInStubVersionOffsetProvider
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKindProvider

/**
 * Applies no changes to the K1 IDE stub version and adds a big constant offset to the K2 IDE stub version for .kotlin_builtins files.
 * It should be practically impossible to get a big enough stub version with K1 for it to clash with the K2 version range.
 * See the comment in [KotlinBuiltInStubVersionOffsetProvider] for the reasons why the offset is needed.
 */
internal class IdeKotlinBuiltInStubVersionOffsetProvider : KotlinBuiltInStubVersionOffsetProvider {
    override fun getVersionOffset(): Int {
        return when (KotlinPluginKindProvider.currentPluginKind) {
            KotlinPluginKind.K1 -> 0
            KotlinPluginKind.K2 -> K2_BUILTINS_STUB_VERSION_OFFSET
        }
    }
}

private const val K2_BUILTINS_STUB_VERSION_OFFSET = 100000
