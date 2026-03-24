// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test

class KotlinGeneratedSourcesImportTest : KotlinGradleImportingTestCase() {

    @Test
    @TargetVersions("7.6+")
    fun testGeneratedInMainSourceSet() {
        configureByFiles()
        importProject()

        val generatedRoot = getContentRootFor("main", ExternalSystemSourceType.SOURCE_GENERATED)
        assertSize(1, generatedRoot)
        assertTrue(generatedRoot.first().path.endsWith("src/main/kotlinGen"))
    }

    @Test
    @TargetVersions("7.6+")
    fun testGeneratedInTestSourceSet() {
        configureByFiles()
        importProject()

        val generatedRoot = getContentRootFor("test", ExternalSystemSourceType.TEST_GENERATED)
        assertSize(1, generatedRoot)
        assertTrue(generatedRoot.first().path.endsWith("src/test/kotlinGen"))
    }

    @Test
    @TargetVersions("7.6+")
    fun testGeneratedWithIdeaPlugin() {
        configureByFiles()
        importProject()

        val generatedRoot = getContentRootFor("main", ExternalSystemSourceType.SOURCE_GENERATED)
        assertSize(1, generatedRoot)
        assertTrue(generatedRoot.first().path.endsWith("src/main/kotlinGen"))
    }

    override fun testDataDirName(): String = "kotlinGeneratedSourcesImportTest"

    private fun getContentRootFor(
        sourceSetName: String,
        type: ExternalSystemSourceType,
    ): Collection<ContentRootData.SourceRoot> {
        val gradleProjectData = ProjectDataManager.getInstance().getExternalProjectsData(myProject, GradleConstants.SYSTEM_ID).first()
        val externalProjectData = gradleProjectData!!.externalProjectStructure!!
        val modulesNodes = ExternalSystemApiUtil.findAll(externalProjectData, ProjectKeys.MODULE)

        // main module
        val mainModule = modulesNodes.first()
        val gradleSourceSets = ExternalSystemApiUtil.findAll(mainModule, GradleSourceSetData.KEY)
        val gradleSourceSet = gradleSourceSets.single { it.data.id == "${mainModule.data.id}:${sourceSetName}" }
        val contentRootData = gradleSourceSet.children.single { it.data is ContentRootData }.data as ContentRootData

        return contentRootData.getPaths(type)
    }
}
