// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.liveTemplates

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@TestRoot("code-insight/live-templates-k1")
@TestMetadata("testData/context")
@RunWith(JUnit38ClassRunner::class)
class LiveTemplatesContextTest : AbstractLiveTemplatesContextTest() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1
}
