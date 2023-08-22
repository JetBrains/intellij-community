// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.psi

import com.intellij.lang.html.HTMLLanguage
import com.intellij.util.ThrowableRunnable
import org.intellij.lang.annotations.Language
import org.intellij.lang.regexp.RegExpLanguage
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class KotlinLibInjectionTest : AbstractInjectionTest() {
    private val mockLibraryFacility = MockLibraryFacility(
        source = IDEA_TEST_DATA_DIR.resolve("injection/lib"),
        attachSources = false
    )

    fun testFunInjection() = assertInjectionPresent(
        """
            import injection.html
            fun test() {
                12.html("<caret><html></html>")
            }
            """,
        HTMLLanguage.INSTANCE.id
    )

    fun testFunInjectionWithImportedAnnotation() = assertInjectionPresent(
        """
            import injection.regexp
            fun test() {
                12.regexp("<caret>test")
            }
            """,
        RegExpLanguage.INSTANCE.id
    )

    private fun assertInjectionPresent(@Language("kotlin") text: String, languageId: String) {
        doInjectionPresentTest(text, languageId = languageId, unInjectShouldBePresent = false)
    }

    override fun setUp() {
        super.setUp()
        mockLibraryFacility.setUp(module)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { mockLibraryFacility.tearDown(module) },
            ThrowableRunnable { super.tearDown() }
        )
    }
}
