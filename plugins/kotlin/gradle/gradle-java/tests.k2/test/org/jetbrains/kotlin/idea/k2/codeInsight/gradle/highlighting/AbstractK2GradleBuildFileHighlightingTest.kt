// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.highlighting

import org.jetbrains.kotlin.idea.codeInsight.gradle.AbstractGradleBuildFileHighlightingTest
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class AbstractK2GradleBuildFileHighlightingTest : AbstractGradleBuildFileHighlightingTest(),
                                                           ExpectedPluginModeProvider {

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    override val outputFileExt: String = ".highlighting.k2"
}