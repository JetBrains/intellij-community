// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.intentions.AbstractBlockingCallRelatedFixesTest

internal class SharedK1BlockingCallRelatedFixesTest : AbstractBlockingCallRelatedFixesTest() {
    override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K1
}