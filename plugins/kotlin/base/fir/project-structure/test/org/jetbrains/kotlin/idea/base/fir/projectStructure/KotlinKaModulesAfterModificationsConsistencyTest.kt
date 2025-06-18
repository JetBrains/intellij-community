// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.testFramework.DumbModeTestUtils
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile

class KotlinKaModulesAfterModificationsConsistencyTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun testPreserveConsistentEntriesAfterDumbMode() {
        myFixture.configureByText("A.kt", "import java.io.IOExc<caret>eption \nclass A")

        val libFile = myFixture.findClass("java.io.IOException").containingFile
        val projectStructureProvider = KotlinProjectStructureProvider.getInstance(project)

        val beforeDumbModeModule = projectStructureProvider.getModule(libFile, null)
        assertNotNull(beforeDumbModeModule)

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            // do nothing, just ensure dumb mode was done
        }

        val afterDumbModeModule = projectStructureProvider.getModule(libFile, null)
        assertNotNull(afterDumbModeModule)

        assertTrue("Modules should be recreated", beforeDumbModeModule !== afterDumbModeModule)
        assertTrue("Same sdks should be still equal", beforeDumbModeModule == afterDumbModeModule)
    }
}