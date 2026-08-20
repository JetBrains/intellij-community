// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.module.Module
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

class KotlinScratchHighlightingModuleTest : KotlinLightCodeInsightFixtureTestCase() {

    private val testScope = CoroutineScope(SupervisorJob())

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    override fun tearDown() {
        try {
            testScope.cancel()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun testSymbolFromSelectedModuleResolves() {
        myFixture.addFileToProject("Alpha.kt", "class Alpha")
        val scratch = createScratch("Alpha()")

        scratch.bindTo(module)

        scratch.checkMetaInfoHighlighting("alphaWithModuleSelected.kts.highlighting")
    }

    fun testSymbolIsUnresolvedWhenNoModuleSelected() {
        myFixture.addFileToProject("Alpha.kt", "class Alpha")
        val scratch = createScratch("Alpha()")

        scratch.bindTo(null)

        scratch.checkMetaInfoHighlighting("alphaWithoutModuleSelected.kts.highlighting", allowErrors = true)
    }

    private fun KotlinScratchFile.bindTo(boundModule: Module?) {
        setModule(boundModule)
        PlatformTestUtil.waitWhileBusy { testScope.coroutineContext.job.children.any { it.isActive } }
        UIUtil.dispatchAllInvocationEvents()
        awaitScriptEntity()
    }

    private fun createScratch(text: String): KotlinScratchFile {
        val name = getTestName(false) + ".kts"
        val scratch = ScratchRootType.getInstance().createScratchFile(
            project, name, KotlinLanguage.INSTANCE, text, ScratchFileService.Option.create_if_missing
        ) ?: error("Couldn't create scratch file")

        IndexingTestUtil.waitUntilIndexesAreReady(project)
        return KotlinScratchFile(project, scratch, testScope)
    }
}
