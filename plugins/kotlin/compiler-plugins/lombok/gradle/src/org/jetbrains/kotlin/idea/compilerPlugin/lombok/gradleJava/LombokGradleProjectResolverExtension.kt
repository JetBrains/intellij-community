// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.lombok.gradleJava

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.model.lombok.LombokModel
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

internal class LombokGradleProjectResolverExtension : AbstractProjectResolverExtension() {

    private val modelClass: Class<LombokModel> = LombokModel::class.java
    private val userDataKey: Key<LombokModel> = KEY

    override fun getExtraProjectModelClasses() = setOf(modelClass)

    override fun getToolingExtensionsClasses() = setOf(
        modelClass,
        Unit::class.java
    )

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        val model = resolverCtx.getExtraProject(gradleModule, modelClass)

        if (model != null) {
            ideModule.createChild(userDataKey, model)
        }

        super.populateModuleExtraModels(gradleModule, ideModule)
    }

    companion object {
        val KEY = Key.create(LombokModel::class.java, 1)
    }

}
