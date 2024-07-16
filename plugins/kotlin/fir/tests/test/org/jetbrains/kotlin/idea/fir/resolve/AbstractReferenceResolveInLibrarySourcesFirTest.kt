// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.resolve

import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveInLibrarySourcesTest
import org.jetbrains.kotlin.idea.test.runAll

abstract class AbstractReferenceResolveInLibrarySourcesFirTest : AbstractReferenceResolveInLibrarySourcesTest() {

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }
}