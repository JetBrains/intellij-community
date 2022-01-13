// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.training.ifs

import com.intellij.testFramework.PlatformTestUtil
import java.io.File

object KotlinSuggestersTestUtils {
    val testDataPath: String
        get() = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/plugins/kotlin/features-trainer/tests/testData"
}