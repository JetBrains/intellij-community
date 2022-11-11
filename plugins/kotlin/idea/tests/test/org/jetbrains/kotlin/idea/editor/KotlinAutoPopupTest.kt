// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.editor

import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinAutoPopupTest: CompletionAutoPopupTestCase() {

    fun testAfterLT() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            fun test(x:Int) {
                if (x <caret>)
            }
        """.trimIndent())
        type("<")
        assertNull(lookup)
    }

}