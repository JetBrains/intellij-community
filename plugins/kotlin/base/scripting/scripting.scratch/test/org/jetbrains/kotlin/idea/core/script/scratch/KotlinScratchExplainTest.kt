// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.jupiter.api.Test

@TestRoot("base/scripting/scripting.scratch")
@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/kotlin/base/scripting/scripting.scratch/testData")
@TestMetadata("testData")
class KotlinScratchExplainTest : AbstractKotlinScratchRunActionTest() {
    override val isExplainEnabled: Boolean
        get() = true

    @TestMetadata("destructuringDecls.kts")
    @Test
    fun testDestructuringDecls(): Unit = doScratchTest()

    @TestMetadata("for.kts")
    @Test
    fun testFor(): Unit = doScratchTest()

    @TestMetadata("generalCollections.kts")
    @Test
    fun testGeneralCollections(): Unit = doScratchTest()

    @TestMetadata("generics.kts")
    @Test
    fun testGenerics(): Unit = doScratchTest()

    @TestMetadata("hexFormat.kts")
    @Test
    fun testHexFormat(): Unit = doScratchTest()

    @TestMetadata("jdk17HexFormat.kts")
    @Test
    fun testJdk17HexFormat(): Unit = doScratchTest()

    @TestMetadata("klass.kts")
    @Test
    fun testKlass(): Unit = doScratchTest()

    @TestMetadata("unresolved.kts")
    @Test
    fun testUnresolved(): Unit = doScratchTest()

    @TestMetadata("var.kts")
    @Test
    fun testVar(): Unit = doScratchTest()

    @TestMetadata("veryLongOutput.kts")
    @Test
    fun testVeryLongOutput(): Unit = doScratchTest()

    @TestMetadata("when.kts")
    @Test
    fun testWhen(): Unit = doScratchTest()
}
