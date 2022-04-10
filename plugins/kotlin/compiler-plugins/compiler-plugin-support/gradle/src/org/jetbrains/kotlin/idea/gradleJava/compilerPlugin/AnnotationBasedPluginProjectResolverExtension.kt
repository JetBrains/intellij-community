// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.compilerPlugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.util.Key
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.model.annotation.AnnotationBasedPluginModel
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

@Suppress("unused")
abstract class AnnotationBasedPluginProjectResolverExtension<T : AnnotationBasedPluginModel> : AbstractProjectResolverExtension() {
    private companion object {
        private val LOG = Logger.getInstance(AnnotationBasedPluginProjectResolverExtension::class.java)
    }

    abstract val modelClass: Class<T>
    abstract val userDataKey: Key<T>

    override fun getExtraProjectModelClasses() = setOf(modelClass, AnnotationBasedPluginModel::class.java)

    override fun getToolingExtensionsClasses() = setOf(
            modelClass,
            AnnotationBasedPluginProjectResolverExtension::class.java,
            Unit::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        val model = resolverCtx.getExtraProject(gradleModule, modelClass)

        if (model != null) {
            val (className, args) = model.dump()

            @Suppress("UNCHECKED_CAST")
            val refurbishedModel = Class.forName(className).constructors.single().newInstance(*args) as T

            ideModule.putCopyableUserData(userDataKey, refurbishedModel)
        }

        super.populateModuleExtraModels(gradleModule, ideModule)
    }
}