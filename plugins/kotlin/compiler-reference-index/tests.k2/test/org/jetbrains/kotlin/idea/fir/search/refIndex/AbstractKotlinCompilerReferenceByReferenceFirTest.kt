// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex


abstract class AbstractKotlinCompilerReferenceByReferenceFirTest : AbstractKotlinCompilerReferenceByReferenceTest() {
    override val isFir: Boolean get() = true
}
