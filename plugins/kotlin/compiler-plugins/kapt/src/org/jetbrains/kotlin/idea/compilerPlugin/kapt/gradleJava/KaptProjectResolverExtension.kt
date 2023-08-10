// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kapt.gradleJava

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.util.Key
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptModelBuilderService
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.util.GradleConstants

@Suppress("unused")
class KaptProjectResolverExtension : AbstractProjectResolverExtension() {
    private companion object {
        private val LOG = Logger.getInstance(KaptProjectResolverExtension::class.java)
    }

    override fun getExtraProjectModelClasses(): Set<Class<KaptGradleModel>> {
        val isAndroidPluginRequestingKotlinGradleModelKey = Key.findKeyByName("IS_ANDROID_PLUGIN_REQUESTING_KAPT_GRADLE_MODEL_KEY")
        if (isAndroidPluginRequestingKotlinGradleModelKey != null && resolverCtx.getUserData(isAndroidPluginRequestingKotlinGradleModelKey) != null) {
            return emptySet()
        }

        return setOf(KaptGradleModel::class.java)
    }

    override fun getToolingExtensionsClasses() = setOf(KaptModelBuilderService::class.java, Unit::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        val kaptModel = resolverCtx.getExtraProject(gradleModule, KaptGradleModel::class.java)

        if (kaptModel != null && kaptModel.isEnabled) {
            for (sourceSet in kaptModel.sourceSets) {
                val parentDataNode = ideModule.findParentForSourceSetDataNode(sourceSet.sourceSetName) ?: continue

                fun addSourceSet(path: String, type: ExternalSystemSourceType) {
                    val contentRootData = ContentRootData(GradleConstants.SYSTEM_ID, path)
                    contentRootData.storePath(type, path)
                    parentDataNode.createChild(ProjectKeys.CONTENT_ROOT, contentRootData)
                }

                val sourceType =
                    if (sourceSet.isTest) ExternalSystemSourceType.TEST_GENERATED else ExternalSystemSourceType.SOURCE_GENERATED
                sourceSet.generatedSourcesDirFile?.let { addSourceSet(it.absolutePath, sourceType) }
                sourceSet.generatedKotlinSourcesDirFile?.let { addSourceSet(it.absolutePath, sourceType) }

                sourceSet.generatedClassesDirFile?.let { generatedClassesDir ->
                    val libraryData = LibraryData(GradleConstants.SYSTEM_ID, "kaptGeneratedClasses")
                    val existingNode =
                        parentDataNode.children.map { (it.data as? LibraryDependencyData)?.target }
                            .firstOrNull { it?.externalName == libraryData.externalName }
                    if (existingNode != null) {
                        existingNode.addPath(LibraryPathType.BINARY, generatedClassesDir.absolutePath)
                    } else {
                        libraryData.addPath(LibraryPathType.BINARY, generatedClassesDir.absolutePath)
                        val libraryDependencyData = LibraryDependencyData(parentDataNode.data, libraryData, LibraryLevel.MODULE)
                        parentDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData)
                    }
                }
            }
        }

        super.populateModuleExtraModels(gradleModule, ideModule)
    }

    private fun DataNode<ModuleData>.findParentForSourceSetDataNode(sourceSetName: String): DataNode<ModuleData>? {
        val moduleName = data.id
        for (child in children) {
            val gradleSourceSetData = child.data as? GradleSourceSetData ?: continue
            if (gradleSourceSetData.id == "$moduleName:$sourceSetName") {
                @Suppress("UNCHECKED_CAST")
                return child as? DataNode<ModuleData>
            }
        }

        return this
    }
}
