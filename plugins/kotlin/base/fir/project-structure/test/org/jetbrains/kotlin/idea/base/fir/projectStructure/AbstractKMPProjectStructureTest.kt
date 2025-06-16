// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.test.*
import java.nio.file.Paths
import kotlin.io.path.div
import kotlin.io.path.readLines

abstract class AbstractKMPProjectStructureTest : KotlinLightCodeInsightFixtureTestCaseBase() {
    protected open fun doTest(testDataPath: String) {
        val testDataPath = Paths.get(testDataPath)
        val allModules = project.getAllKaModules()

        val txt = KaModuleStructureTxtRenderer.render(allModules)
        KotlinTestUtils.assertEqualsToFile(testDataPath / "kaModules.txt", txt)

        val mermaid = KaModuleStructureMermaidRenderer.render(allModules)
        KotlinTestUtils.assertEqualsToFile(testDataPath / "kaModules.mmd", mermaid)
    }

    override fun tearDown() {
        runAll(
            { projectDescriptor.cleanupSourceRoots() },
            { KotlinSdkType.removeKotlinSdkInTests() },
            { super.tearDown() },
        )
    }

    override fun getProjectDescriptor(): KotlinMultiPlatformProjectDescriptor {
        val testDataFile = Paths.get(
            TestMetadataUtil.getTestDataPath(javaClass),
            KotlinTestUtils.getTestDataFileName(this::class.java, this.name),
            "platforms.txt"
        )
        val platformDescriptors = testDataFile.readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { KotlinMultiPlatformProjectDescriptor.PlatformDescriptor.valueOf(it) }
        return KotlinMultiPlatformProjectDescriptor(platformDescriptors)
    }
}