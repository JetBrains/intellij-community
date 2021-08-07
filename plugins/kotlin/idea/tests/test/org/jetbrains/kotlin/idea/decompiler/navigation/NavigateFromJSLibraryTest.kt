// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.navigation

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ThrowableRunnable
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.KotlinCompilerStandalone
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class NavigateFromJSLibrarySourcesTest : AbstractNavigateFromLibrarySourcesTest() {
    private val mockLibraryFacility = MockLibraryFacility(
        source = IDEA_TEST_DATA_DIR.resolve("decompiler/navigation/fromJSLibSource"),
        platform = KotlinCompilerStandalone.Platform.JavaScript(MockLibraryFacility.MOCK_LIBRARY_NAME, "lib")
    )

    fun testIcon() {
        TestCase.assertEquals(
            "Icon.kt",
            navigationElementForReferenceInLibrarySource("lib.kt", "Icon").containingFile.name
        )
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return object : KotlinLightProjectDescriptor() {
            override fun getSdk() = KotlinSdkType.INSTANCE.createSdkWithUniqueName(emptyList())
        }
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
