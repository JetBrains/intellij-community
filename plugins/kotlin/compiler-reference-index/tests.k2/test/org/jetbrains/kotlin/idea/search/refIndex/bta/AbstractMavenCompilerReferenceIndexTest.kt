// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.useProjectAsync
import com.intellij.util.SystemProperties
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexService
import kotlin.time.Duration.Companion.minutes

abstract class AbstractMavenCompilerReferenceIndexTest : MavenMultiVersionImportingTestCase() {

    override fun setUp() {
        super.setUp()
        Registry.get(BTA_CRI_REGISTRY_KEY).setValue(true, testRootDisposable)
    }

    protected fun mavenProjectWithCriValue(value: String = "true"): String =
        """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1.0.0</version>
        <properties>
            <kotlin.compiler.generateCompilerRefIndex>$value</kotlin.compiler.generateCompilerRefIndex>
        </properties>
        """.trimIndent()

    protected fun mavenProjectWithoutCri(): String =
        """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1.0.0</version>
        """.trimIndent()

    protected fun preloadCriService(
        project: Project = this.project,
    ): KotlinCompilerReferenceIndexService = project.service<KotlinCompilerReferenceIndexService>()

    // Imports a Maven project, saves its state, and then reopens it to simulate a project restart
    protected suspend fun withReopenedMavenProject(
        relativePath: String,
        projectPomContent: String,
        action: suspend (Project) -> Unit,
    ) {
        Registry.get("ide.activity.tracking.enable.debug").setValue(true, testRootDisposable)
        WorkspaceModelCacheImpl.forceEnableCaching(testRootDisposable)
        val projectPom = createModulePom(relativePath, projectPomContent)

        openProjectAsync(projectPom).useProjectAsync(save = true) { importedProject ->
            TestObservation.awaitConfiguration(importedProject, 5.minutes)
        }

        withMavenStartupEnabledForHeadlessReopenTest {
            openProjectAsync(projectPom).useProjectAsync { reopenedProject ->
                action(reopenedProject)
            }
        }
    }

    // Enables Maven startup restoration so the reopened test project repopulates [MavenProjectsManager.projects]
    protected suspend fun withMavenStartupEnabledForHeadlessReopenTest(action: suspend () -> Unit) {
        withSystemProperty("maven.default.headless.import", "true") {
            withSystemProperty("maven.unit.tests.remove", "true", action)
        }
    }

    private suspend fun withSystemProperty(propertyName: String, value: String, action: suspend () -> Unit) {
        val oldValue = SystemProperties.setProperty(propertyName, value)
        try {
            action()
        } finally {
            SystemProperties.setProperty(propertyName, oldValue)
        }
    }

    companion object {
        private const val BTA_CRI_REGISTRY_KEY = "kotlin.cri.bta.support.enabled"
    }
}
