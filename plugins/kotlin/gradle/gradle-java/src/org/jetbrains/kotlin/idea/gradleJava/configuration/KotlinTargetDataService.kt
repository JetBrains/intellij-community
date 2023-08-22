// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.packaging.artifacts.ModifiableArtifactModel
import com.intellij.packaging.impl.artifacts.JarArtifactType
import com.intellij.packaging.impl.elements.ProductionModuleOutputPackagingElement
import org.jetbrains.kotlin.idea.gradle.configuration.*
import org.jetbrains.kotlin.idea.util.createPointer

class KotlinTargetDataService : AbstractProjectDataService<KotlinTargetData, Void>() {
    override fun getTargetDataKey() = KotlinTargetData.KEY

    override fun importData(
        toImport: MutableCollection<out DataNode<KotlinTargetData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider
    ) {
        for (nodeToImport in toImport) {
            val targetData = nodeToImport.data
            val archiveFile = targetData.archiveFile ?: continue

            //TODO(auskov) should be replaced with direct invocation of the method after migration to new API in IDEA 192
            val artifactModel = try {
                val oldMethod = modelsProvider.javaClass.getMethod("getModifiableArtifactModel")
                oldMethod.invoke(modelsProvider) as ModifiableArtifactModel
            } catch (_: Exception) {
                val newMethod = modelsProvider.javaClass.getMethod("getModifiableModel", Class::class.java)
                val packagingModifiableModel = newMethod.invoke(
                    modelsProvider,
                    Class.forName("com.intellij.openapi.externalSystem.project.PackagingModifiableModel")
                )
                packagingModifiableModel.javaClass
                    .getMethod("getModifiableArtifactModel")
                    .invoke(packagingModifiableModel) as ModifiableArtifactModel
            }

            val artifactName = FileUtil.getNameWithoutExtension(archiveFile)
            artifactModel.findArtifact(artifactName)?.let { artifactModel.removeArtifact(it) }
            artifactModel.addArtifact(artifactName, JarArtifactType.getInstance()).also {
                it.outputPath = archiveFile.parent
                for (moduleId in targetData.moduleIds) {
                    val compilationModuleDataNode = nodeToImport.parent?.findChildModuleById(moduleId) ?: continue
                    val compilationData = compilationModuleDataNode.data
                    val kotlinSourceSet = compilationModuleDataNode.kotlinSourceSetData?.sourceSetInfo ?: continue
                    if (kotlinSourceSet.isTestModule) continue
                    val moduleToPackage = modelsProvider.findIdeModule(compilationData) ?: continue
                    it.rootElement.addOrFindChild(ProductionModuleOutputPackagingElement(project, moduleToPackage.createPointer()))
                }
            }
        }
    }
}