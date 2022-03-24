// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.core

import com.intellij.icons.AllIcons
import com.intellij.ide.util.ChooseElementsDialog
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.util.ExternalSystemContentRootContributor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.scope.GeneratedFilesScope
import com.intellij.psi.search.scope.TestsScope
import com.intellij.ui.FileColorManager
import java.awt.Color
import javax.swing.Icon
import kotlin.io.path.Path
import kotlin.io.path.pathString

class ExternalContentRootChooser private constructor(
    project: Project,
    items: Collection<ExternalSystemContentRootContributor.ExternalContentRoot>,
) :
    ChooseElementsDialog<ExternalSystemContentRootContributor.ExternalContentRoot>(
        project,
        items.toList(),
        JavaRefactoringBundle.message("select.source.root.chooser.title"),
        null,
        true,
    ) {

    init {
        myChooser.setSingleSelectionMode()
    }

    private val projectPath = project.basePath?.let(::Path)
    private val testsColor by lazy(LazyThreadSafetyMode.PUBLICATION) {
        FileColorManager.getInstance(project).getScopeColor(TestsScope.INSTANCE.scopeId)
    }

    private val generatedColor by lazy(LazyThreadSafetyMode.PUBLICATION) {
        FileColorManager.getInstance(project).getScopeColor(GeneratedFilesScope.INSTANCE.scopeId)
    }

    override fun getItemText(item: ExternalSystemContentRootContributor.ExternalContentRoot): String =
        projectPath?.relativize(item.path)?.pathString ?: item.path.pathString

    override fun getItemIcon(item: ExternalSystemContentRootContributor.ExternalContentRoot): Icon = item.icon

    override fun getItemBackgroundColor(item: ExternalSystemContentRootContributor.ExternalContentRoot): Color? = when {
        item.rootType.isGenerated -> generatedColor
        item.rootType.isTest -> testsColor
        else -> null
    }

    companion object {
        fun choose(
            project: Project,
            roots: Collection<ExternalSystemContentRootContributor.ExternalContentRoot>
        ): ExternalSystemContentRootContributor.ExternalContentRoot? = when (roots.size) {
            0 -> null
            1 -> roots.singleOrNull()
            else -> ExternalContentRootChooser(project, roots).showAndGetResult().firstOrNull()
        }
    }
}

val ExternalSystemContentRootContributor.ExternalContentRoot.icon: Icon
    get() = when (rootType) {
        ExternalSystemSourceType.SOURCE -> AllIcons.Modules.SourceRoot
        ExternalSystemSourceType.TEST -> AllIcons.Modules.TestRoot

        ExternalSystemSourceType.SOURCE_GENERATED -> AllIcons.Modules.GeneratedSourceRoot
        ExternalSystemSourceType.TEST_GENERATED -> AllIcons.Modules.GeneratedTestRoot

        ExternalSystemSourceType.RESOURCE -> AllIcons.Modules.ResourcesRoot
        ExternalSystemSourceType.TEST_RESOURCE -> AllIcons.Modules.TestResourcesRoot

        ExternalSystemSourceType.EXCLUDED -> AllIcons.Modules.ExcludeRoot

        ExternalSystemSourceType.RESOURCE_GENERATED, ExternalSystemSourceType.TEST_RESOURCE_GENERATED -> AllIcons.Modules.GeneratedFolder
    }
