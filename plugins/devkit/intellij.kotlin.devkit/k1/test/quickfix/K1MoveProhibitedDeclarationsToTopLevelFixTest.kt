// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.k1.quickfix

import org.jetbrains.idea.devkit.kotlin.inspections.quickfix.KtMoveProhibitedDeclarationsToTopLevelFixTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1MoveProhibitedDeclarationsToTopLevelFixTest : KtMoveProhibitedDeclarationsToTopLevelFixTest() {

  override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K1

  fun testMoveConflicts() {
    val expectedConflicts = listOf(
      "Following declarations would clash: to move function &#39;fun foo()&#39; and destination function &#39;fun foo()&#39; declared in scope ",
    )
    doTestFixWithConflicts(fixName, expectedConflicts)
  }
}
