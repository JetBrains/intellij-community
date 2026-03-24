// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.structureView

import com.intellij.ide.util.InheritedMembersNodeProvider

abstract class AbstractK1KotlinFileStructureTest: AbstractKotlinFileStructureTest() {
    override fun nodeProvider(): InheritedMembersNodeProvider<*> = KotlinInheritedMembersNodeProvider()
}
