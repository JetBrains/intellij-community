// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.search.refIndex

import com.intellij.testFramework.SkipSlowTestLocally
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceMultiModuleTest

@SkipSlowTestLocally
class KotlinCompilerReferenceMultiModuleFirTest : KotlinCompilerReferenceMultiModuleTest() {
    override val isFir: Boolean get() = true
}
