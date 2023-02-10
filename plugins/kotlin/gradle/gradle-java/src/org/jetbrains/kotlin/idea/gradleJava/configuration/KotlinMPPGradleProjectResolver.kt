// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.notification.*
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.util.Key
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.idea.gradle.configuration.*
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.*
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils.getKotlinModuleId
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBuilder
import org.jetbrains.kotlin.idea.projectModel.*
import org.jetbrains.kotlin.tooling.core.Extras
import org.jetbrains.plugins.gradle.model.*
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.lang.reflect.Proxy
import java.util.*

@Order(ExternalSystemConstants.UNORDERED + 1)
open class KotlinMPPGradleProjectResolver : AbstractProjectResolverExtension() {

    override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData>? {
        val moduleDataNode = super.createModule(gradleModule, projectDataNode) ?: return null
        initializeMppModuleDataNode(gradleModule, moduleDataNode, projectDataNode, resolverCtx)
        return moduleDataNode
    }

    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return setOf(
            KotlinMPPGradleModelBuilder::class.java, KotlinTarget::class.java,
            IdeaKotlinDependency::class.java, Extras::class.java, Unit::class.java
        )
    }

    override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
        return setOf(KotlinMPPGradleModel::class.java, KotlinTarget::class.java, IdeaKotlinDependency::class.java, Extras::class.java)
    }

    override fun getExtraCommandLineArgs(): List<String> =
        /**
         * The Kotlin Gradle plugin might want to use this intransitive metadata configuration to tell the IDE, that specific
         * dependencies shall not be passed on to dependsOn source sets. (e.g. some commonized libraries).
         * By default, the Gradle plugin does not use this configuration and instead places the dependencies into a previously
         * supported configuration.
         * This will tell the Gradle plugin that this version of the IDE plugin does support importing this special configuraiton.
         */
        listOf("-Pkotlin.mpp.enableIntransitiveMetadataConfiguration=true")

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        if (ExternalSystemApiUtil.find(ideModule, BuildScriptClasspathData.KEY) == null) {
            val buildScriptClasspathData = buildClasspathData(gradleModule, resolverCtx)
            ideModule.createChild(BuildScriptClasspathData.KEY, buildScriptClasspathData)
        }
        super.populateModuleExtraModels(gradleModule, ideModule)
    }

    override fun populateModuleContentRoots(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        val mppModel = resolverCtx.getMppModel(gradleModule)
            ?: return super.populateModuleContentRoots(gradleModule, ideModule)

        reportMultiplatformNotifications(mppModel, resolverCtx)
        populateContentRoots(gradleModule, ideModule, resolverCtx)
        populateExternalSystemRunTasks(gradleModule, ideModule, resolverCtx)
    }

    override fun populateModuleCompileOutputSettings(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        val mppModel = resolverCtx.getMppModel(gradleModule)
            ?: return super.populateModuleCompileOutputSettings(gradleModule, ideModule)

        populateModuleCompileOutputSettings(gradleModule, ideModule, mppModel, resolverCtx)
    }

    override fun populateModuleDependencies(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>, ideProject: DataNode<ProjectData>) {
        val mppModel = resolverCtx.getExtraProject(gradleModule, KotlinMPPGradleModel::class.java)
        if (mppModel == null) {
            resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java)?.sourceSets?.values?.forEach { sourceSet ->
                sourceSet.dependencies.modifyDependenciesOnMppModules(ideProject)
            }

            super.populateModuleDependencies(gradleModule, ideModule, ideProject) //TODO add dependencies on mpp module
        }

        populateModuleDependencies(gradleModule, ideProject, ideModule, resolverCtx)
    }

    companion object {
        val MPP_CONFIGURATION_ARTIFACTS =
            Key.create<MutableMap<String/* artifact path */, MutableList<String> /* module ids*/>>("gradleMPPArtifactsMap")
        val proxyObjectCloningCache = WeakHashMap<Any, Any>()

        fun populateModuleDependencies(
            gradleModule: IdeaModule,
            ideProject: DataNode<ProjectData>,
            ideModule: DataNode<ModuleData>,
            resolverCtx: ProjectResolverContext
        ) {
            val mppModel = resolverCtx.getMppModel(gradleModule) ?: return
            populateModuleDependencies(gradleModule, ideProject, ideModule, resolverCtx, mppModel)
        }

        internal fun getSiblingKotlinModuleData(
            kotlinComponent: KotlinComponent,
            gradleModule: IdeaModule,
            ideModule: DataNode<ModuleData>,
            resolverCtx: ProjectResolverContext
        ): DataNode<out ModuleData>? {
            val usedModuleId = getKotlinModuleId(gradleModule, kotlinComponent, resolverCtx)
            return ideModule.findChildModuleById(usedModuleId)
        }

        val KotlinComponent.sourceType
            get() = if (isTestComponent) ExternalSystemSourceType.TEST else ExternalSystemSourceType.SOURCE

        val KotlinComponent.resourceType
            get() = if (isTestComponent) ExternalSystemSourceType.TEST_RESOURCE else ExternalSystemSourceType.RESOURCE

        fun createSourceSetInfo(mppModel: KotlinMPPGradleModel, sourceSet: KotlinSourceSet, gradleModule: IdeaModule,
            resolverCtx: ProjectResolverContext
        ): KotlinSourceSetInfo? = doCreateSourceSetInfo(mppModel, sourceSet, gradleModule, resolverCtx)

        // This method is used in Android side of import and it's signature could not be changed
        fun createSourceSetInfo(
            model: KotlinMPPGradleModel,
            compilation: KotlinCompilation,
            gradleModule: IdeaModule,
            resolverCtx: ProjectResolverContext
        ): KotlinSourceSetInfo? {
            return doCreateSourceSetInfo(model, compilation, gradleModule, resolverCtx)
        }

        // restored method for binary compatibility with Android plugin, will be removed in future commits
        fun createSourceSetInfo(
            compilation: KotlinCompilation,
            gradleModule: IdeaModule,
            resolverCtx: ProjectResolverContext
        ): KotlinSourceSetInfo? {
            val model = resolverCtx.getMppModel(gradleModule) ?: return null
            return createSourceSetInfo(model, compilation, gradleModule, resolverCtx)
        }
    }
}

fun ProjectResolverContext.getMppModel(gradleModule: IdeaModule): KotlinMPPGradleModel? =
    this.getExtraProject(gradleModule, KotlinMPPGradleModel::class.java)?.let { mppModel ->
        if (mppModel is Proxy) {
            KotlinMPPGradleProjectResolver.proxyObjectCloningCache[mppModel] as? KotlinMPPGradleModelImpl
                ?: KotlinMPPGradleModelImpl(mppModel, KotlinMPPGradleProjectResolver.proxyObjectCloningCache).also {
                    KotlinMPPGradleProjectResolver.proxyObjectCloningCache[mppModel] = it
                }
        } else mppModel
    }

