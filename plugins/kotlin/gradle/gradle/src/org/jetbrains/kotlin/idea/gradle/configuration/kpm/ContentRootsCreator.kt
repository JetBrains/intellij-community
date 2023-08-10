// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.configuration.kpm

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

interface ContentRootsCreator {
    fun populateContentRoots(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>, resolverCtx: ProjectResolverContext)

    companion object {
        @JvmField
        val EP_NAME: ExtensionPointName<ContentRootsCreator> = ExtensionPointName.create("org.jetbrains.kotlin.kpm.createRoots")
    }

}
