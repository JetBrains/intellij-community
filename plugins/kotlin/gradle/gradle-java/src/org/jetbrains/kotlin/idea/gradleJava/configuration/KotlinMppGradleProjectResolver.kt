// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.build.events.MessageEvent
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.util.PlatformUtils
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetInfo
import org.jetbrains.kotlin.idea.gradle.configuration.ResolveModulesPerSourceSetInMppBuildIssue
import org.jetbrains.kotlin.idea.gradle.configuration.buildClasspathData
import org.jetbrains.kotlin.idea.gradle.configuration.findChildModuleById
import org.jetbrains.kotlin.idea.gradle.ui.notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.*
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils.getKotlinModuleId
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBuilder
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelImpl
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.kotlin.tooling.core.Extras
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.lang.reflect.Proxy
import java.util.*
import kotlin.collections.set

@Suppress("unused") // Can be removed once AS rebased on 23.1
@Deprecated("Use KotlinMppGradleProjectResolver instead", replaceWith = ReplaceWith("KotlinMppGradleProjectResolver"))
typealias KotlinMPPGradleProjectResolver = KotlinMppGradleProjectResolver

@Order(ExternalSystemConstants.UNORDERED + 1)
open class KotlinMppGradleProjectResolver : AbstractProjectResolverExtension() {

    interface Context {
        val mppModel: KotlinMPPGradleModel
        val resolverCtx: ProjectResolverContext
        val gradleModule: IdeaModule
        val projectDataNode: DataNode<ProjectData>
        val moduleDataNode: DataNode<ModuleData>
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

    override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData>? {
        val moduleDataNode = super.createModule(gradleModule, projectDataNode) ?: return null
        val model = resolverCtx.getMppModel(gradleModule) ?: return moduleDataNode
        val context = KotlinMppGradleProjectResolver.Context(model, resolverCtx, gradleModule, projectDataNode, moduleDataNode)
        moduleDataNode.kotlinMppGradleProjectResolverContext = context
        populateMppModuleDataNode(context)
        return moduleDataNode
    }

    override fun populateModuleContentRoots(gradleModule: IdeaModule, moduleDataNode: DataNode<ModuleData>) {
        val context = moduleDataNode.kotlinMppGradleProjectResolverContext
            ?: return super.populateModuleContentRoots(gradleModule, moduleDataNode)

        reportMultiplatformNotifications(resolverCtx)
        context.populateContentRoots()
        populateExternalSystemRunTasks(gradleModule, moduleDataNode, resolverCtx)
    }

    override fun populateModuleCompileOutputSettings(gradleModule: IdeaModule, moduleDataNode: DataNode<ModuleData>) {
        moduleDataNode.kotlinMppGradleProjectResolverContext?.populateModuleCompileOutputSettings()
            ?: return super.populateModuleCompileOutputSettings(gradleModule, moduleDataNode)
    }

    override fun populateModuleDependencies(
        gradleModule: IdeaModule,
        moduleDataNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>
    ) {
        moduleDataNode.kotlinMppGradleProjectResolverContext?.populateModuleDependencies() ?:
            super.populateModuleDependencies(gradleModule, moduleDataNode, projectDataNode)

    }

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        if (ExternalSystemApiUtil.find(ideModule, BuildScriptClasspathData.KEY) == null) {
            val buildScriptClasspathData = buildClasspathData(gradleModule, resolverCtx)
            ideModule.createChild(BuildScriptClasspathData.KEY, buildScriptClasspathData)
        }
        super.populateModuleExtraModels(gradleModule, ideModule)
    }

    override fun resolveFinished(projectDataNode: DataNode<ProjectData>) {
        super.resolveFinished(projectDataNode)
        val extensionInstance = KotlinMppGradleProjectResolverExtension.buildInstance()
        val moduleDataNodes = mutableListOf<DataNode<ModuleData>>()

        /* Call the 'afterResolveFinished' extensions for all multiplatform nodes */
        ExternalSystemApiUtil.findAll(projectDataNode, ProjectKeys.MODULE).forEach { moduleDataNode ->
            extensionInstance.afterResolveFinished(moduleDataNode.kotlinMppGradleProjectResolverContext ?: return@forEach)
            moduleDataNodes.add(moduleDataNode)
        }

        /* Remove the context from all nodes since we're done working with it */
        moduleDataNodes.forEach { moduleDataNode -> moduleDataNode.kotlinMppGradleProjectResolverContext = null }
    }

    companion object {
        val proxyObjectCloningCache = WeakHashMap<Any, Any>()

        internal fun getSiblingKotlinModuleData(
            kotlinComponent: KotlinComponent,
            gradleModule: IdeaModule,
            ideModule: DataNode<ModuleData>,
            resolverCtx: ProjectResolverContext
        ): DataNode<out ModuleData>? {
            val usedModuleId = getKotlinModuleId(gradleModule, kotlinComponent, resolverCtx)
            return ideModule.findChildModuleById(usedModuleId)
        }

        fun createSourceSetInfo(
            mppModel: KotlinMPPGradleModel, sourceSet: KotlinSourceSet, gradleModule: IdeaModule,
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
    }
}

internal fun reportMultiplatformNotifications(resolverCtx: ProjectResolverContext) {
    if (!resolverCtx.isResolveModulePerSourceSet && !KotlinPlatformUtils.isAndroidStudio
    ) {
        notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(resolverCtx.projectPath)
        resolverCtx.report(MessageEvent.Kind.WARNING, ResolveModulesPerSourceSetInMppBuildIssue())
    }
}

fun ProjectResolverContext.getMppModel(gradleModule: IdeaModule): KotlinMPPGradleModel? =
    this.getExtraProject(gradleModule, KotlinMPPGradleModel::class.java)?.let { mppModel ->
        if (mppModel is Proxy) {
            KotlinMppGradleProjectResolver.proxyObjectCloningCache[mppModel] as? KotlinMPPGradleModelImpl
                ?: KotlinMPPGradleModelImpl(mppModel, KotlinMppGradleProjectResolver.proxyObjectCloningCache).also {
                    KotlinMppGradleProjectResolver.proxyObjectCloningCache[mppModel] = it
                }
        } else mppModel
    }

internal val KotlinComponent.sourceType
    get() = if (isTestComponent) ExternalSystemSourceType.TEST else ExternalSystemSourceType.SOURCE

internal val KotlinComponent.resourceType
    get() = if (isTestComponent) ExternalSystemSourceType.TEST_RESOURCE else ExternalSystemSourceType.RESOURCE
