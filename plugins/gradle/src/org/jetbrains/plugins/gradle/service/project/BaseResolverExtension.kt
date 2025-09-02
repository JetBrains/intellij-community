// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.amazon.ion.IonType
import com.google.gson.GsonBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.net.HttpConfigurable
import org.gradle.internal.impldep.com.google.common.collect.Multimap
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.annotations.ApiStatus
import kotlin.Int.Companion.MAX_VALUE

@ApiStatus.Internal
@Order(MAX_VALUE)
internal class BaseResolverExtension : GradleProjectResolverExtension {
  override fun setProjectResolverContext(projectResolverContext: ProjectResolverContext) {}
  override fun getNext(): GradleProjectResolverExtension? = null
  override fun setNext(projectResolverExtension: GradleProjectResolverExtension) {
    throw AssertionError("should be the last extension in the chain")
  }
  override fun populateProjectExtraModels(gradleProject: IdeaProject, ideProject: DataNode<ProjectData>) {}
  override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData>? = null
  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {}
  override fun populateModuleContentRoots(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {}
  override fun populateModuleCompileOutputSettings(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {}
  override fun populateModuleDependencies(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>, ideProject: DataNode<ProjectData>) {}
  override fun populateModuleTasks(gradleModule: IdeaModule,
                                   ideModule: DataNode<ModuleData>,
                                   ideProject: DataNode<ProjectData>): Collection<TaskData> = emptyList()

  override fun getToolingExtensionsClasses(): Set<Class<*>> {
    return linkedSetOf(
      Multimap::class.java, // repacked gradle guava
      GsonBuilder::class.java,
      IonType::class.java,  // ion-java jar
    )
  }

  override fun getExtraJvmArgs(): List<Pair<String, String>> {
    val extraJvmArgs = mutableListOf<Pair<String, String>>()
    val httpConfigurable = HttpConfigurable.getInstance()
    if (!httpConfigurable.PROXY_EXCEPTIONS.isNullOrEmpty()) {
      val hosts = StringUtil.split(httpConfigurable.PROXY_EXCEPTIONS, ",")
      if (hosts.isNotEmpty()) {
        val nonProxyHosts = hosts.joinToString(separator = "|") { it.trim() }
        extraJvmArgs.add(Pair("http.nonProxyHosts", nonProxyHosts))
        extraJvmArgs.add(Pair("https.nonProxyHosts", nonProxyHosts))
      }
    }
    if (httpConfigurable.USE_HTTP_PROXY && StringUtil.isNotEmpty(httpConfigurable.proxyLogin)) {
      extraJvmArgs.add(
        Pair.pair("http.proxyUser", httpConfigurable.proxyLogin))
      extraJvmArgs.add(
        Pair.pair("https.proxyUser", httpConfigurable.proxyLogin))
      val plainProxyPassword = httpConfigurable.plainProxyPassword
      extraJvmArgs.add(Pair.pair("http.proxyPassword", plainProxyPassword))
      extraJvmArgs.add(Pair.pair("https.proxyPassword", plainProxyPassword))
    }
    extraJvmArgs.addAll(httpConfigurable.getJvmProperties(false, null))
    return extraJvmArgs
  }

  override fun getUserFriendlyError(buildEnvironment: BuildEnvironment?,
                                    error: Throwable,
                                    projectPath: String,
                                    buildFilePath: String?): ExternalSystemException =
    BaseProjectImportErrorHandler().getUserFriendlyError(buildEnvironment, error, projectPath, buildFilePath)
}