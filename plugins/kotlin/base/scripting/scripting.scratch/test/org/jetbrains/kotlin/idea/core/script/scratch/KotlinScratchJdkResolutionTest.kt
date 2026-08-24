// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.lang.JavaVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

class KotlinScratchJdkResolutionTest : KotlinLightCodeInsightFixtureTestCase() {

    private val testScope = CoroutineScope(SupervisorJob())

    private var registeredJdk: Sdk? = null
    private var originalProjectSdk: Sdk? = null
    private var projectSdkChanged = false

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    override fun tearDown() {
        try {
            restoreProjectSdk()
            unregisterMockJdk()
            testScope.cancel()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun testSelectedJdkReachesScriptEntity() {
        val jdk = resolvableJdk()
        val scratch = createScratch("println(\"hello\")")
        assertNull("jdkSupplier prefers the module SDK, so this test needs no module bound", scratch.module)

        scratch.selectJdkAndWait(jdk)

        val entity = KotlinScriptEntityProvider.provide(project, scratch.virtualFile)
        assertEquals("the selected JDK must reach the script entity", jdk.name, entity?.sdkId?.name)
    }

    fun testJdkGatedApiResolvesOnlyUnderModernJdk() {
        val modernJdk = resolvableJdk()
        assertTrue("fixture JDK must be 17+ for java.util.HexFormat to exist", isAtLeast17(modernJdk))
        val legacyJdk = registerMockJdk()

        val scratch = createScratch(HEX_FORMAT_SCRIPT)

        scratch.selectJdkAndWait(modernJdk)
        scratch.checkMetaInfoHighlighting("hexFormatUnderModernJdk.kts.highlighting")

        scratch.selectJdkAndWait(legacyJdk)
        scratch.checkMetaInfoHighlighting("hexFormatUnderLegacyJdk.kts.highlighting", allowErrors = true)
    }

    fun testScratchJdkOverridesProjectJdk() {
        val projectJdk = resolvableJdk()
        assertTrue("project JDK must be 17+ for this test to mean anything", isAtLeast17(projectJdk))
        setProjectSdk(projectJdk)
        val legacyJdk = registerMockJdk()

        val scratch = createScratch(HEX_FORMAT_SCRIPT)

        scratch.selectJdkAndWait(legacyJdk)

        scratch.checkMetaInfoHighlighting("hexFormatUnderLegacyJdkWithModernProjectJdk.kts.highlighting", allowErrors = true)
    }

    private fun KotlinScratchFile.selectJdkAndWait(jdk: Sdk) {
        selectJdk(jdk)
        PlatformTestUtil.waitWithEventsDispatching(
            "the selected JDK '${jdk.name}' never reached KotlinScriptEntity.sdkId",
            { KotlinScriptEntityProvider.provide(project, virtualFile)?.sdkId?.name == jdk.name },
            SCRIPT_ENTITY_TIMEOUT_SECONDS,
        )
    }

    private fun createScratch(text: String): KotlinScratchFile {
        val scratch = ScratchRootType.getInstance().createScratchFile(
            project, getTestName(false) + ".kts", KotlinLanguage.INSTANCE, text,
            ScratchFileService.Option.create_if_missing
        ) ?: error("Couldn't create scratch file")

        IndexingTestUtil.waitUntilIndexesAreReady(project)
        return KotlinScratchFile(project, scratch, testScope)
    }

    private fun resolvableJdk(): Sdk {
        val jdk = checkNotNull(ModuleRootManager.getInstance(module).sdk) { "fixture must expose a module SDK" }
        checkNotNull(ProjectJdkTable.getInstance().findJdk(jdk.name)) {
            "fixture SDK must be registered in ProjectJdkTable for sdkId to resolve"
        }
        return jdk
    }

    private fun registerMockJdk(): Sdk {
        val jdk = IdeaTestUtil.getMockJdk18()
        runWriteAction { ProjectJdkTable.getInstance().addJdk(jdk) }
        registeredJdk = jdk
        return jdk
    }

    private fun unregisterMockJdk() {
        val jdk = registeredJdk ?: return
        registeredJdk = null
        runWriteAction { ProjectJdkTable.getInstance().removeJdk(jdk) }
    }

    private fun setProjectSdk(jdk: Sdk) {
        val rootManager = ProjectRootManager.getInstance(project)
        originalProjectSdk = rootManager.projectSdk
        projectSdkChanged = true
        runWriteAction { rootManager.projectSdk = jdk }
    }

    private fun restoreProjectSdk() {
        if (!projectSdkChanged) return
        val restored = originalProjectSdk
        originalProjectSdk = null
        projectSdkChanged = false
        runWriteAction { ProjectRootManager.getInstance(project).projectSdk = restored }
    }

    private fun isAtLeast17(jdk: Sdk): Boolean {
        val version = jdk.versionString?.let { JavaVersion.tryParse(it) } ?: return false
        return version.feature >= 17
    }
}

private const val HEX_FORMAT_SCRIPT = "java.util.HexFormat.of()"
