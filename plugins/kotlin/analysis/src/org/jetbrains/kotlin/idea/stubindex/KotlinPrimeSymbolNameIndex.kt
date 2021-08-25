// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.stubindex

import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey

class KotlinPrimeSymbolNameIndex : StringStubIndexExtension<NavigatablePsiElement>() {
    override fun getKey(): StubIndexKey<String, NavigatablePsiElement> = KEY

    override fun getVersion(): Int {
        return super.getVersion() + 0
    }

    companion object {
        val KEY: StubIndexKey<String, NavigatablePsiElement> = StubIndexKey.createIndexKey("kotlin.primeIndexKey")
    }
}