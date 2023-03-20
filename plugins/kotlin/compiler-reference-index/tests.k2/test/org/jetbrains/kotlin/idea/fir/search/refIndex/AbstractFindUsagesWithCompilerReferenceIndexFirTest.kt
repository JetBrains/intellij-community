// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.search.refIndex

import org.jetbrains.kotlin.idea.search.refIndex.AbstractFindUsagesWithCompilerReferenceIndexTest

abstract class AbstractFindUsagesWithCompilerReferenceIndexFirTest : AbstractFindUsagesWithCompilerReferenceIndexTest() {
    override val isFir: Boolean get() = true
    override val ignoreLog: Boolean get() = true
}
