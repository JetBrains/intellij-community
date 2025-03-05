// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.intentions.shared

import org.jetbrains.kotlin.idea.k2.intentions.tests.AbstractK2IntentionTest

abstract class AbstractSharedK2IntentionTest : AbstractK2IntentionTest() {
    override fun intentionFileName(): String = ".intention"
}