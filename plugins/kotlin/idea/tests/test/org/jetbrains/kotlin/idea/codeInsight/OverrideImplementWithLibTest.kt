// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.testFramework.TestDataPath
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.junit.internal.runners.JUnit38ClassRunner
import com.intellij.codeInsight.generation.ClassMember
import org.jetbrains.kotlin.idea.core.overrideImplement.OverrideMemberChooserObject
import org.junit.runner.RunWith

@TestRoot("idea/tests")
@TestDataPath("\$CONTENT_ROOT")
@RunWith(JUnit38ClassRunner::class)
@TestMetadata("testData/codeInsight/overrideImplement/withLib")
class OldOverrideImplementWithLibTest : OverrideImplementWithLibTest<OverrideMemberChooserObject>(), OldOverrideImplementTestMixIn

abstract class OverrideImplementWithLibTest<T : ClassMember> : AbstractOverrideImplementTest<T>() {
    private lateinit var mockLibraryFacility: MockLibraryFacility

    override fun setUp() {
        super.setUp()

        val mockSourcesBase = IDEA_TEST_DATA_DIR.resolve("codeInsight/overrideImplement/withLib")
        val mockSource = mockSourcesBase.resolve(getTestName(true) + "Src")

        mockLibraryFacility = MockLibraryFacility(mockSource, attachSources = false)
        mockLibraryFacility.setUp(module)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { mockLibraryFacility.tearDown(module) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    fun testFakeOverride() {
        doOverrideFileTest()
    }

    fun testGenericSubstituted() {
        doOverrideFileTest()
    }
}
