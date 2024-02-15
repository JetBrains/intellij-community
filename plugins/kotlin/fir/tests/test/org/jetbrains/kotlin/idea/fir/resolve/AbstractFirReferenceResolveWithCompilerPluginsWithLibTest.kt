// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.resolve

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithCompilerPluginsWithCompiledLibTest
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithCompilerPluginsWithCrossLibTest
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithCompilerPluginsWithLibTest
import org.jetbrains.kotlin.idea.test.runAll

abstract class AbstractFirReferenceResolveWithCompilerPluginsWithLibTest : AbstractReferenceResolveWithCompilerPluginsWithLibTest() {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }
}

abstract class AbstractFirReferenceResolveWithCompilerPluginsWithCompiledLibTest : AbstractReferenceResolveWithCompilerPluginsWithCompiledLibTest() {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }
}

abstract class AbstractFirReferenceResolveWithCompilerPluginsWithCrossLibTest : AbstractReferenceResolveWithCompilerPluginsWithCrossLibTest() {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }
}
