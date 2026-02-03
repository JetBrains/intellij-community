// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.TestIndexingModeSupporter
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

abstract class AbstractDumbCompletionTest : KotlinFixtureCompletionBaseTestCase() {
    init {
      indexingMode = TestIndexingModeSupporter.IndexingMode.DUMB_EMPTY_INDEX
    }

    override fun getPlatform(): TargetPlatform = JvmPlatforms.unspecifiedJvmPlatform

    override fun defaultCompletionType(): CompletionType = CompletionType.BASIC
}