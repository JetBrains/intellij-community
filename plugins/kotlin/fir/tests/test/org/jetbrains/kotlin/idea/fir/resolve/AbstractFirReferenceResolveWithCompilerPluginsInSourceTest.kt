// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.resolve

import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveWithCompilerPluginsInSourceTest
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.util.invalidateCaches

abstract class AbstractFirReferenceResolveWithCompilerPluginsInSourceTest : AbstractReferenceResolveWithCompilerPluginsInSourceTest() {

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}
