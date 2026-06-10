// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexService

/**
 * Installs the Maven CRI file watcher for reopened Maven projects
 */
class CriMavenStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // runs after `mavenProjectsManagerStartup`, so project should be ready
        project.serviceIfCreated<KotlinCompilerReferenceIndexService>()?.installBtaFileWatcherIfApplicable()
    }
}

/**
 * Installs the Maven CRI file watcher install once import has populated `MavenProjectsManager`
 */
internal class CriMavenImportListener(private val project: Project) : MavenImportListener {
    override fun importFinished(importedProjects: Collection<MavenProject>, newModules: List<Module>) {
        project.serviceIfCreated<KotlinCompilerReferenceIndexService>()?.installBtaFileWatcherIfApplicable()
    }
}
