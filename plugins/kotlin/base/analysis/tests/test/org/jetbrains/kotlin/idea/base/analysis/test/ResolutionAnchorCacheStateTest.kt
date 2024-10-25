// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.test

import com.intellij.testFramework.ProjectRule
import org.jetbrains.kotlin.idea.caches.resolve.util.ResolutionAnchorCacheState
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions

class ResolutionAnchorCacheStateTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun testResolutionAnchorCacheStateIsSerializable() {
        val anchorState = ResolutionAnchorCacheState.getInstance(projectRule.project)
        val dummyState = ResolutionAnchorCacheState.State(
            mapOf(
                "foo" to "bar",
                "baz" to "qux"
            )
        )
        anchorState.loadState(dummyState)
        Assertions.assertEquals(dummyState, anchorState.state)
    }
}