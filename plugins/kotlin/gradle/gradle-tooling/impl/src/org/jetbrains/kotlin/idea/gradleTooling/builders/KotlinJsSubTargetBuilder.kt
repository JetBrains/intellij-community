// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.BrowserTestRunnerImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinBrowserTestExtensionsImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinJsBrowserSubTargetImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinJsSubTargetImpl
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinBrowserTestRunnerReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinJsBrowserTestReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinJsSubTargetReflection
import org.jetbrains.kotlin.idea.projectModel.BrowserTestRunner
import org.jetbrains.kotlin.idea.projectModel.BrowserTestRunnerType
import org.jetbrains.kotlin.idea.projectModel.KotlinBrowserDebugProtocolVersion
import org.jetbrains.kotlin.idea.projectModel.KotlinBrowserTestExtensions
import org.jetbrains.kotlin.idea.projectModel.KotlinJsSubTarget

object KotlinJsSubTargetBuilder : KotlinModelComponentBuilderBase<KotlinJsSubTargetReflection, KotlinJsSubTarget> {
    override fun buildComponent(origin: KotlinJsSubTargetReflection): KotlinJsSubTarget? {
        val name = origin.name ?: return null
        val testRunsNames = origin.testRuns?.mapNotNull {
            val name = it.name ?: return@mapNotNull null
            val taskName = it.executionTask?.name ?: return@mapNotNull null
            name to taskName
        }?.toMap() ?: return null

        val defaultJsBrowserSubtargetTestTaskName = testRunsNames["test"] ?: return null

        if (origin.type.contains("KotlinBrowserJsIr")) {
            return KotlinJsBrowserSubTargetImpl(name, testRunsNames.keys.toList(),  buildBrowserTestExtensions(origin.browserTest, defaultJsBrowserSubtargetTestTaskName))
        }

        return KotlinJsSubTargetImpl(name, testRunsNames.keys.toList())
    }

    private fun buildBrowserTestExtensions(
        test: KotlinJsBrowserTestReflection?,
        defaultJsBrowserSubtargetTestTaskName: String,
    ): KotlinBrowserTestExtensions {
        val browserTestRunners = test?.allBrowserRunners?.mapNotNullTo(LinkedHashSet<BrowserTestRunner>()) {
            buildBrowserTestRunner(it, defaultJsBrowserSubtargetTestTaskName)
        }
        return KotlinBrowserTestExtensionsImpl(browserTestRunners)
    }

    private fun buildBrowserTestRunner(runner: KotlinBrowserTestRunnerReflection, defaultJsBrowserSubtargetTestTaskName: String): BrowserTestRunner? {
        val type = when {
            runner.isChromiumTestRunnerDsl -> BrowserTestRunnerType.CHROMIUM
            else -> return null
        }

        val testTaskName = runner.testTask?.name ?: defaultJsBrowserSubtargetTestTaskName

        return BrowserTestRunnerImpl(
            name = runner.name,
            type = type,
            testTaskName = testTaskName,
            supportsKotlinBrowserDebugProtocolVersion =
                if (type == BrowserTestRunnerType.CHROMIUM) {
                    KotlinBrowserDebugProtocolVersion.V2_5
                } else {
                    KotlinBrowserDebugProtocolVersion.UNSUPPORTED
                },
        )
    }
}
