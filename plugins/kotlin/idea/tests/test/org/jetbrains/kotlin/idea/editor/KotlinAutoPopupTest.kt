// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.editor

import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import kotlin.test.assertContains

@RunWith(JUnit38ClassRunner::class)
class KotlinAutoPopupTest : CompletionAutoPopupTestCase() {

    fun testAfterLT() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            fun test(x:Int) {
                if (x <caret>)
            }
        """.trimIndent())

        type("<")
        assertNull(lookup)
    }

    fun testAfterAtInModifierList() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            <caret>
            fun test() {}
        """.trimIndent())

        type("@")
        lookup?.items?.map { it.lookupString }.orEmpty().let { lookupStrings ->
            assertContains(lookupStrings, "receiver")
            assertContains(lookupStrings, "OptIn")
        }
        assertContains(lookup?.items?.map { it.lookupString }.orEmpty(), "OptIn")
    }

    fun testAfterAtInFileAnnotationList() {
        myFixture.configureByText(KotlinFileType.INSTANCE, """
            <caret>
        """.trimIndent())

        type("@")
        lookup?.items?.map { it.lookupString }.orEmpty().let { lookupStrings ->
            assertContains(lookupStrings, "file")
            assertContains(lookupStrings, "OptIn")
        }
        type("file:")
        assertContains(lookup?.items?.map { it.lookupString }.orEmpty(), "OptIn")
    }
}