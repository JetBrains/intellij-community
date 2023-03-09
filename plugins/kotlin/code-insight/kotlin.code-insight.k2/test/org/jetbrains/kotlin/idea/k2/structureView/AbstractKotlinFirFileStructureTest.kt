// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.structureView

import com.intellij.ide.util.InheritedMembersNodeProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.structureView.KotlinFirInheritedMembersNodeProvider
import org.jetbrains.kotlin.idea.structureView.AbstractKotlinFileStructureTest

abstract class AbstractKotlinFirFileStructureTest : AbstractKotlinFileStructureTest() {
    override fun isFirPlugin(): Boolean {
        return true
    }

    override fun nodeProviderClass(): Class<out InheritedMembersNodeProvider<*>> {
        return KotlinFirInheritedMembersNodeProvider::class.java
    }
}