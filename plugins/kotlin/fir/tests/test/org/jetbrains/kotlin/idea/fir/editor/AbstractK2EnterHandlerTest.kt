// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.editor

import org.jetbrains.kotlin.formatter.AbstractEnterHandlerTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

abstract class AbstractK2EnterHandlerTest: AbstractEnterHandlerTest() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
}
