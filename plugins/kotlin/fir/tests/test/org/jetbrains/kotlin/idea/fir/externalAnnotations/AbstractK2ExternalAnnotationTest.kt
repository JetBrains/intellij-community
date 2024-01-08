// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.externalAnnotations

import org.jetbrains.kotlin.idea.externalAnnotations.AbstractExternalAnnotationTest
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import java.io.File

abstract class AbstractK2ExternalAnnotationTest : AbstractExternalAnnotationTest() {
    override fun isFirPlugin(): Boolean = true

    override fun doTest(kotlinFilePath: String) {
        val original = File(kotlinFilePath)
        super.doTest(IgnoreTests.getFirTestFile(original).path)
        IgnoreTests.cleanUpIdenticalFirTestFile(original)
    }
}
