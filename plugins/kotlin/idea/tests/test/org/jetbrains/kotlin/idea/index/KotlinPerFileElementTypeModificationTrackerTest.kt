// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.index

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.indexing.StubIndexPerFileElementTypeModificationTrackerTestHelper
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.stubs.elements.KtFileElementType

class KotlinPerFileElementTypeModificationTrackerTest : KotlinLightCodeInsightFixtureTestCase() {
    companion object {
        val KOTLIN get() = KtFileElementType
    }

    private val helper = StubIndexPerFileElementTypeModificationTrackerTestHelper()

    override fun setUp() {
        super.setUp()
        helper.setUp()
    }

    fun `test mod counter changes on file creation and stub change`() {
        helper.initModCounts(KOTLIN)
        val psi = myFixture.addFileToProject("a/Foo.kt", """
            class Foo { 
              var value: Integer = 42
            }
        """.trimIndent())
        helper.ensureStubIndexUpToDate(project)
        helper.checkModCountHasChanged(KOTLIN)
        WriteAction.run<Throwable> { VfsUtil.saveText(psi.containingFile.virtualFile, """
            class Foo {
              var value: Double = 42.42
            }
        """.trimIndent()); }
        helper.checkModCountHasChanged(KOTLIN)
    }

    fun `test mod counter doesnt change on non-stub changes`() {
        helper.initModCounts(KOTLIN)
        val psi = myFixture.addFileToProject("Foo.kt", """
            class Foo { 
              fun test(x: Integer): Boolean {
                return false
              }
            }
        """.trimIndent())
        helper.checkModCountHasChanged(KOTLIN)
        helper.ensureStubIndexUpToDate(project)

        WriteAction.run<Throwable> { VfsUtil.saveText(psi.containingFile.virtualFile, """
            class Foo {
              fun test(x: Integer): Boolean {
                return x >= 0
              }
            }
        """.trimIndent()); }
        helper.checkModCountIsSame(KOTLIN)
    }
}