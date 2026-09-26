// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeInsight.gradle.highlighting

import org.jetbrains.kotlin.idea.codeInsight.gradle.AbstractGradleBuildFileHighlightingTest

abstract class AbstractK2GradleBuildFileHighlightingTest : AbstractGradleBuildFileHighlightingTest() {

    

    override val outputFileExt: String = ".highlighting.k2"
}