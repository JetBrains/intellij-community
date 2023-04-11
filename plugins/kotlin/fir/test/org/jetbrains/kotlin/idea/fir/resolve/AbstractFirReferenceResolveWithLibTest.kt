// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.resolve

import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithCompiledLibTest
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithLibTest

abstract class AbstractFirReferenceResolveWithLibTest : AbstractReferenceResolveWithLibTest() {
    override fun isFirPlugin(): Boolean = true
}

abstract class AbstractFirReferenceResolveWithCompiledLibTest : AbstractReferenceResolveWithCompiledLibTest() {
    override fun isFirPlugin(): Boolean = true
}