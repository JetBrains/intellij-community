// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.navigation

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.idea.test.closeAndDeleteProject
import org.jetbrains.kotlin.idea.test.runAll
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class NavigateToStdlibSourceRegressionTest : NavigateToLibraryRegressionTest() {

    fun testRefToAssertEquals() {
        val navigationElement = configureAndResolve("import kotlin.io.createTempDir; val x = <caret>createTempDir()")
        assertEquals("Utils.kt", navigationElement.containingFile.name)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { super.tearDown() },
            ThrowableRunnable { closeAndDeleteProject() }
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor =
        ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()
}
