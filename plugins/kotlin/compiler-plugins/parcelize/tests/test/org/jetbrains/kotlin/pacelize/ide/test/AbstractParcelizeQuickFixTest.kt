// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.pacelize.ide.test

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.roots.OrderRootType
import org.jetbrains.kotlin.idea.artifacts.AdditionalKotlinArtifacts
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addRoot
import java.io.File

abstract class AbstractParcelizeQuickFixTest : AbstractQuickFixTest() {
    override fun setUp() {
        super.setUp()
        addParcelizeLibraries(module)
    }

    override fun tearDown() {
        removeParcelizeLibraries(module)
        super.tearDown()
    }
}