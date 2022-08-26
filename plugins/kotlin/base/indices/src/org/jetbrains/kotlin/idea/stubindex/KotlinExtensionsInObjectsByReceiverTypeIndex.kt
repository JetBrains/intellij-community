// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtCallableDeclaration

object KotlinExtensionsInObjectsByReceiverTypeIndex : KotlinExtensionsByReceiverTypeIndex() {
    private val KEY: StubIndexKey<String, KtCallableDeclaration> =
        StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinExtensionsInObjectsByReceiverTypeIndex")

    override fun getKey(): StubIndexKey<String, KtCallableDeclaration> = KEY
}