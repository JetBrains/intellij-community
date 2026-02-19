// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.resolve

import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithCompilerPluginsWithCompiledLibTest
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithCompilerPluginsWithCrossLibTest
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithCompilerPluginsWithLibTest
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractFirReferenceResolveWithCompilerPluginsWithLibTest : AbstractReferenceResolveWithCompilerPluginsWithLibTest() {

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}

abstract class AbstractFirReferenceResolveWithCompilerPluginsWithCompiledLibTest : AbstractReferenceResolveWithCompilerPluginsWithCompiledLibTest() {

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}

abstract class AbstractFirReferenceResolveWithCompilerPluginsWithCrossLibTest : AbstractReferenceResolveWithCompilerPluginsWithCrossLibTest() {

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}
