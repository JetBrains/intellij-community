// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.testGenerator

import junit.framework.TestCase

class AllTestsGeneratedTest : TestCase() {
    fun testAllTestsIsUpToDate(): Unit = generateTests(isUpToDateCheck = true)
}