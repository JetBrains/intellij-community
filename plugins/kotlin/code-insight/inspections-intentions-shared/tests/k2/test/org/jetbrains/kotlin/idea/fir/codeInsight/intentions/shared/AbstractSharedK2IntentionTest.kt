// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.codeInsight.intentions.shared

import org.jetbrains.kotlin.idea.fir.intentions.AbstractHLIntentionTest

abstract class AbstractSharedK2IntentionTest : AbstractHLIntentionTest() {
    override fun intentionFileName() = ".intention"
}