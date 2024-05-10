// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.resolve

import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithCompiledLibTest
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithCrossLibTest
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithLibTest
import org.jetbrains.kotlin.idea.test.runAll

abstract class AbstractFirReferenceResolveWithLibTest : AbstractReferenceResolveWithLibTest() {

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}

abstract class AbstractFirReferenceResolveWithCompiledLibTest : AbstractReferenceResolveWithCompiledLibTest() {

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}

abstract class AbstractFirReferenceResolveWithCrossLibTest : AbstractReferenceResolveWithCrossLibTest() {

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}