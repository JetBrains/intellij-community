// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.stubs

import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.stubs.StubElement
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.stubs.elements.KtFileStubBuilder
import org.jetbrains.kotlin.psi.stubs.impl.STUB_TO_STRING_PREFIX
import java.io.File

abstract class AbstractStubBuilderTest : KotlinLightCodeInsightFixtureTestCase() {
    protected fun doTest(unused: String) {
        val file = myFixture.configureByFile(fileName()) as KtFile
        val ktStubBuilder = KtFileStubBuilder()
        val lighterTree = ktStubBuilder.buildStubTree(file)
        val stubTree = serializeStubToString(lighterTree)

        val testFile = dataFile()
        val expectedFile = File(testFile.parent, testFile.nameWithoutExtension + ".expected")
        KotlinTestUtils.assertEqualsToFile(expectedFile, stubTree)
    }

    companion object {
        fun serializeStubToString(stubElement: StubElement<*>): String {
            val treeStr = DebugUtil.stubTreeToString(stubElement)

            // Nodes are stored in form "NodeType:Node" and have too many repeating information for Kotlin stubs
            // Remove all repeating information (See KotlinStubBaseImpl.toString())
            return treeStr.lines().joinToString(separator = "\n") {
                if (it.contains(STUB_TO_STRING_PREFIX)) {
                    it.takeWhile(Char::isWhitespace) + it.substringAfter("KotlinStub$")
                } else {
                    it
                }
            }.replace(", [", "[")
        }
    }
}
