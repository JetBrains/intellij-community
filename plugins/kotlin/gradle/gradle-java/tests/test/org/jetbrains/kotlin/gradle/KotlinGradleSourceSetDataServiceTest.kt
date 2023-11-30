// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import junit.framework.AssertionFailedError
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.facetSettings
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinGradleProjectDataService
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow

class KotlinGradleSourceSetDataServiceTest : KotlinGradleImportingTestCase() {
    @Test
    @TargetVersions("7.6+")
    fun testSimpleFacetImport() {
        configureByFiles()
        importProject()
        assertDoesNotThrow {
            facetSettings("project.main")
            facetSettings("project.test")
        }
        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            facetSettings("project")
        }
    }

    @Test
    @TargetVersions("7.6+")
    fun testSingleFacetPerModule() {
        configureByFiles()
        // This means a single module called project is imported rather than one per source set
        currentExternalProjectSettings.isResolveModulePerSourceSet = false
        importProject()
        assertDoesNotThrow {
            facetSettings("project")
        }
        org.junit.jupiter.api.assertThrows<AssertionFailedError> {
            facetSettings("project.main")
        }
        org.junit.jupiter.api.assertThrows<AssertionFailedError> {
            facetSettings("project.test")
        }
    }

    private val testSystemId = ProjectSystemId("TestSystem")

    // See KTIJ-27111 for details
    @Test
    @TargetVersions("7.6+")
    fun testExternalSystem() {
        configureByFiles()
        currentExternalProjectSettings.isResolveModulePerSourceSet = false
        importProject()
        assertDoesNotThrow {
            facetSettings("project")
        }

        val service = KotlinGradleProjectDataService()
        val currentSettings = currentExternalProjectSettings
        // Fake external system projectData
        val projectData = ProjectData(testSystemId, "TestSystem", myProject.projectFilePath!!, currentSettings.externalProjectPath)

        // Find the module nodes
        val gradleProjectData = ProjectDataManager.getInstance().getExternalProjectsData(myProject, GradleConstants.SYSTEM_ID).first()
        val externalProjectData = gradleProjectData!!.externalProjectStructure!!
        val modulesNodes = ExternalSystemApiUtil.findAll(externalProjectData, ProjectKeys.MODULE)

        // main module
        val mainModule = modulesNodes.first()
        mainModule.clear(false)
        // Clear all the Gradle source sets, so that this module appears like from an external system,
        // but it has a Kotlin facet
        ExternalSystemApiUtil.getChildren(mainModule, KotlinSourceSetData.KEY).forEach {
            it.clear(true)
        }
        val modifiableProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(myProject)

        // We pretend that an external system has imported this project, including the existing Kotlin facet.
        // This call should NOT remove facets from other systems
        service.postProcess(modulesNodes, projectData, myProject, modifiableProvider)
        runWriteActionAndWait {
            modifiableProvider.commit()
        }

        assertDoesNotThrow {
            facetSettings("project")
        }
    }

    override fun testDataDirName(): String = "gradleSourceSetDataServiceTest"
}