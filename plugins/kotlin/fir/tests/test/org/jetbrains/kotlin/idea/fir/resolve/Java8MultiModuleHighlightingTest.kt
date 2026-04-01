// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.resolve

import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class Java8MultiModuleHighlightingTest : AbstractMultiModuleTest() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("multiModuleHighlighting")

    fun testDifferentJdk() {
        val module1 = module("jdk8") { IdeaTestUtil.getMockJdk18() }
        val module2 = module("jdk6") { IdeaTestUtil.getMockJdk16() }

        module1.addDependency(module2)

        checkFiles({ project.allKotlinFiles() }) {
            checkHighlighting(myEditor, true, false)
        }
    }
}