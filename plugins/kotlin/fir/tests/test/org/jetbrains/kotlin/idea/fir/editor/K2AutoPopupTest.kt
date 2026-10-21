// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.editor

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.editor.KotlinAutoPopupTest
import org.jetbrains.kotlin.test.util.invalidateCaches
import org.junit.jupiter.api.AfterEach

// TODO: rewrite this test to make it generated
@TestApplication
internal class K2AutoPopupTest : KotlinAutoPopupTest() {

    @AfterEach
    fun tearDown() {
        runInEdtAndWait { myFixture.project.invalidateCaches() }
    }
}
