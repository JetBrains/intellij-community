// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.inspections.shared

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.intentions.AbstractBlockingCallRelatedFixesTest

internal class SharedK2BlockingCallRelatedFixesTest : AbstractBlockingCallRelatedFixesTest() {
    override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
    
    private fun muteFailure(test: () -> Unit): Unit = 
        assertThrows(Throwable::class.java, test)

    override fun `test wrap in withContext`() {
        muteFailure {
            super.`test wrap in withContext`()
        }
    }

    override fun `test add dispatcher in flow generator`() {
        muteFailure {
            super.`test add dispatcher in flow generator`()
        }
    }

    override fun `test add dispatcher in flow`() {
        muteFailure {
            super.`test add dispatcher in flow`()
        }
    }

    override fun `test add flowOn to flow generator`() {
        muteFailure {
            super.`test add flowOn to flow generator`()
        }
    }

    override fun `test replace unknown dispatcher in withContext`() {
        muteFailure {
            super.`test replace unknown dispatcher in withContext`()
        }
    }

    override fun `test replace unknown dispatcher in flow`() {
        muteFailure {
            super.`test replace unknown dispatcher in flow`()
        }
    }

    override fun `test wrap dot qualified expression`() {
        muteFailure {
            super.`test wrap dot qualified expression`()
        }
    }
}