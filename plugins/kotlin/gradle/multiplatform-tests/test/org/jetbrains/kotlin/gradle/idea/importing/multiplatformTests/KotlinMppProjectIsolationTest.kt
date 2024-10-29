// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.NoErrorEventsDuringImportFeature
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test

@TestMetadata("multiplatform/core/features/projectIsolation")
public class KotlinMppProjectIsolationTest : AbstractKotlinMppGradleImportingTest() {
    @Test
    fun testJvmOnly() {
        doTest{
            onlyCheckers(NoErrorEventsDuringImportFeature)
        }
    }

}
