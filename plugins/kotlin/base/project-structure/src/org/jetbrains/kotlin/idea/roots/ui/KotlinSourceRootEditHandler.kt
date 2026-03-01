// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.roots.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.roots.ui.configuration.JavaModuleSourceRootEditHandler
import com.intellij.openapi.roots.ui.configuration.JavaResourceRootEditHandler
import com.intellij.openapi.roots.ui.configuration.JavaTestResourceRootEditHandler
import com.intellij.openapi.roots.ui.configuration.JavaTestSourceRootEditHandler
import com.intellij.openapi.roots.ui.configuration.ModuleSourceRootEditHandler
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.config.ResourceKotlinRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestResourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import javax.swing.Icon


sealed class KotlinModuleSourceRootEditHandler<Data : JpsElement>(
    rootType: JpsModuleSourceRootType<Data>,
    private val delegate: ModuleSourceRootEditHandler<Data>
) : ModuleSourceRootEditHandler<Data>(rootType) {
    class Source : KotlinModuleSourceRootEditHandler<JavaSourceRootProperties>(
        SourceKotlinRootType,
        JavaModuleSourceRootEditHandler()
    ) {
        override fun getGeneratedRootIcon(): Icon = AllIcons.Modules.GeneratedSourceRoot
    }

    class TestSource : KotlinModuleSourceRootEditHandler<JavaSourceRootProperties>(
        TestSourceKotlinRootType,
        JavaTestSourceRootEditHandler()
    ) {
        override fun getGeneratedRootIcon(): Icon = AllIcons.Modules.GeneratedTestRoot
    }

    class Resource : KotlinModuleSourceRootEditHandler<JavaResourceRootProperties>(
        ResourceKotlinRootType,
        JavaResourceRootEditHandler()
    ) {
        override fun getGeneratedRootIcon(): Icon = rootIcon
    }

    class TestResource : KotlinModuleSourceRootEditHandler<JavaResourceRootProperties>(
        TestResourceKotlinRootType,
        JavaTestResourceRootEditHandler()
    ) {
        override fun getGeneratedRootIcon(): Icon = rootIcon
    }

    override fun getRootIcon(properties: Data): Icon {
        val javaProperties = (properties as? JavaSourceRootProperties) ?: return getRootIcon()
        return if (javaProperties.isForGeneratedSources) getGeneratedRootIcon() else getRootIcon()
    }

    protected abstract fun getGeneratedRootIcon(): Icon

    override fun getUnmarkRootButtonText() = delegate.unmarkRootButtonText

    override fun getRootIcon() = delegate.rootIcon

    override fun getRootsGroupTitle() = delegate.rootsGroupTitle

    override fun getMarkRootShortcutSet() = delegate.markRootShortcutSet

    override fun getRootTypeName() = delegate.rootTypeName

    override fun getRootsGroupColor() = delegate.rootsGroupColor

    override fun getFolderUnderRootIcon() = delegate.folderUnderRootIcon
}