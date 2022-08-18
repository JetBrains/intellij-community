// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler

import com.intellij.psi.ClassFileViewProvider
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class InternalCompiledClassesTest : AbstractInternalCompiledClassesTest() {
    private val mockLibraryFacility = MockLibraryFacility(
        source = IDEA_TEST_DATA_DIR.resolve("decompiler/internalClasses"),
        attachSources = false
    )

    fun testSyntheticClassesAreInvisible() = doTestNoPsiFilesAreBuiltForSyntheticClasses()

    fun testLocalClassesAreInvisible() = doTestNoPsiFilesAreBuiltForLocalClass()

    fun testInnerClassIsInvisible() = doTestNoPsiFilesAreBuiltFor("inner or nested class") {
        ClassFileViewProvider.isInnerClass(this)
    }

    override fun setUp() {
        super.setUp()
        mockLibraryFacility.setUp(module)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { mockLibraryFacility.tearDown(module) },
            ThrowableRunnable { super.tearDown() }
        )
    }
}
