// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.runner.RunWith

@TestRoot("base/scripting/scratch")
@TestDataPath($$"$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners::class)
@TestMetadata("testData")
class KotlinScratchExplainTest : AbstractKotlinScratchRunActionTest() {
    override val isExplainEnabled: Boolean
        get() = true

    @TestMetadata("destructuringDecls.kts")
    fun testDestructuringDecls() = doScratchTest()

    @TestMetadata("for.kts")
    fun testFor() = doScratchTest()

    @TestMetadata("generalCollections.kts")
    fun testGeneralCollections() = doScratchTest()

    @TestMetadata("generics.kts")
    fun testGenerics() = doScratchTest()

    @TestMetadata("hexFormat.kts")
    fun testHexFormat() = doScratchTest()

    @TestMetadata("jdk17HexFormat.kts")
    fun testJdk17HexFormat() = doScratchTest()

    @TestMetadata("klass.kts")
    fun testKlass() = doScratchTest()

    @TestMetadata("unresolved.kts")
    fun testUnresolved() = doScratchTest()

    @TestMetadata("var.kts")
    fun testVar() = doScratchTest()

    @TestMetadata("veryLongOutput.kts")
    fun testVeryLongOutput() = doScratchTest()

    @TestMetadata("when.kts")
    fun testWhen() = doScratchTest()
}
