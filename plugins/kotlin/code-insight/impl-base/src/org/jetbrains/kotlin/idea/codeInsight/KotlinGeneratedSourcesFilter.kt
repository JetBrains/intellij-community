// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.kotlin.idea.util.getSourceRoot
import org.jetbrains.kotlin.idea.util.isKotlinFileType

internal class KotlinGeneratedSourcesFilter : GeneratedSourcesFilter() {
    override fun isGeneratedSource(
        file: VirtualFile,
        project: Project
    ): Boolean {
        if (!file.isKotlinFileType()) return false

        val contentFileSetRoot = file.getSourceRoot(project) ?: return false

        val moduleSourceRoot = ProjectRootsUtil.getModuleSourceRoot(contentFileSetRoot, project)
        val properties =
            moduleSourceRoot?.getJpsElement()?.getProperties() as? JavaSourceRootProperties ?: return false
        return properties.isForGeneratedSources
    }
}
