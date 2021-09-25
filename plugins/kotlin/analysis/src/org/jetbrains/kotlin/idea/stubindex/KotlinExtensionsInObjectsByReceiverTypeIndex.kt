// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtCallableDeclaration

internal class KotlinExtensionsInObjectsByReceiverTypeIndex private constructor() : KotlinExtensionsByReceiverTypeIndex() {
    override fun getKey(): StubIndexKey<String, KtCallableDeclaration> = KEY

    companion object {
        private val KEY = KotlinIndexUtil.createIndexKey(KotlinExtensionsInObjectsByReceiverTypeIndex::class.java)
        val INSTANCE = KotlinExtensionsInObjectsByReceiverTypeIndex()
    }
}