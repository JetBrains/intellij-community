// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.quickfix

import org.jetbrains.idea.devkit.kotlin.inspections.quickfix.KtCreateObjectAndMoveProhibitedDeclarationsFixTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2CreateObjectAndMoveProhibitedDeclarationsFixTest : KtCreateObjectAndMoveProhibitedDeclarationsFixTest() {

  override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K2

}
