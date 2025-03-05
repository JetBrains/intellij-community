// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.google.gson.GsonBuilder
import com.intellij.gradle.toolingExtension.impl.model.dependencyGraphModel.GradleDependencyNodeDeserializer
import com.intellij.gradle.toolingExtension.impl.model.dependencyGraphModel.GradleDependencyReportTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.dependencies.*
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleModuleData
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.util.concurrent.ConcurrentHashMap
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency

class GradleDependencyAnalyzerContributor(private val project: Project) : DependencyAnalyzerContributor {
  private val projects = ConcurrentHashMap<DependencyAnalyzerProject, GradleModuleData>()
  private val configurationNodesMap = ConcurrentHashMap<String, List<DependencyScopeNode>>()
  private val dependencyMap = ConcurrentHashMap<Long, Dependency>()

  override fun whenDataChanged(listener: () -> Unit, parentDisposable: Disposable) {
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(object : ExternalSystemTaskNotificationListener {
      override fun onEnd(proojecPath: String, id: ExternalSystemTaskId) {
        if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        if (id.projectSystemId != GradleConstants.SYSTEM_ID) return
        projects.clear()
        configurationNodesMap.clear()
        dependencyMap.clear()
        listener()
      }
    }, parentDisposable)
  }

  override fun getProjects(): List<DependencyAnalyzerProject> {
    if (projects.isEmpty()) {
      val projectDataManager = ProjectDataManager.getInstance()
      for (projectInfo in projectDataManager.getExternalProjectsData(project, GradleConstants.SYSTEM_ID)) {
        val projectStructure = projectInfo.externalProjectStructure ?: continue
        for (moduleNode in ExternalSystemApiUtil.findAll(projectStructure, ProjectKeys.MODULE)) {
          val moduleData = moduleNode.data
          val gradleModuleData = GradleModuleData(moduleNode)
          if (!gradleModuleData.isBuildSrcModule) {
            val module = GradleUtil.findGradleModule(project, moduleData) ?: continue
            val externalProject = DAProject(module, moduleData.moduleName)
            projects[externalProject] = gradleModuleData
          }
        }
      }
    }
    return projects.keys.toList()
  }

  override fun getDependencyScopes(externalProject: DependencyAnalyzerProject): List<Dependency.Scope> {
    val gradleModuleData = projects[externalProject] ?: return emptyList()
    return getOrRefreshData(gradleModuleData).map { it.toScope() }
  }

  override fun getDependencies(externalProject: DependencyAnalyzerProject): List<Dependency> {
    val gradleModuleData = projects[externalProject] ?: return emptyList()
    val scopeNodes = getOrRefreshData(gradleModuleData)
    return getDependencies(gradleModuleData, scopeNodes)
  }

  private fun getOrRefreshData(gradleModuleData: GradleModuleData): List<DependencyScopeNode> {
    return configurationNodesMap.computeIfAbsent(gradleModuleData.gradleProjectDir) {
      gradleModuleData.loadDependencies(project)
    }
  }

  private fun getDependencies(moduleData: GradleModuleData,
                              scopeNodes: List<DependencyScopeNode>): List<Dependency> {
    if (scopeNodes.isEmpty()) return emptyList()
    val dependencies = ArrayList<Dependency>()
    val root = DAModule(moduleData.moduleName)
    root.putUserData(MODULE_DATA, moduleData.moduleData)

    val rootDependency = DADependency(root, defaultConfiguration, null, emptyList())
    dependencies.add(rootDependency)
    for (scopeNode in scopeNodes) {
      val scope = scopeNode.toScope()
      for (dependencyNode in scopeNode.dependencies) {
        addDependencies(rootDependency, scope, dependencyNode, dependencies, moduleData.gradleProjectDir)
      }
    }
    return dependencies
  }

  private fun addDependencies(usage: Dependency,
                              scope: Dependency.Scope,
                              dependencyNode: DependencyNode,
                              dependencies: MutableList<Dependency>,
                              gradleProjectDir: String) {
    val dependency = createDependency(dependencyNode, scope, usage) ?: return
    dependencies.add(dependency)
    for (node in dependencyNode.dependencies) {
      addDependencies(dependency, scope, node, dependencies, gradleProjectDir)
    }
  }

  private fun createDependency(dependencyNode: DependencyNode, scope: Dependency.Scope, usage: Dependency): Dependency? {
    when (dependencyNode) {
      is ReferenceNode -> {
        val dependency = dependencyMap[dependencyNode.id] ?: return null
        return DADependency(dependency.data, scope, usage, dependency.status)
      }
      else -> {
        val dependencyData = dependencyNode.getDependencyData() ?: return null
        val status = dependencyNode.getStatus(dependencyData)
        return DADependency(dependencyData, scope, usage, status)
          .also { dependencyMap[dependencyNode.id] = it }
      }
    }
  }

  private fun DependencyNode.getStatus(data: Dependency.Data): List<Dependency.Status> {
    val status = mutableListOf<Dependency.Status>()
    if (resolutionState == ResolutionState.UNRESOLVED) {
      val message = ExternalSystemBundle.message("external.system.dependency.analyzer.warning.unresolved")
      status.add(DAWarning(message))
    }
    val selectionReason = selectionReason
    if (data is Dependency.Data.Artifact && selectionReason != null && selectionReason.startsWith("between versions")) {
      val conflictedVersion = selectionReason.substringAfter("between versions ${data.version} and ", "")
      if (conflictedVersion.isNotEmpty()) {
        val message = ExternalSystemBundle.message("external.system.dependency.analyzer.warning.version.conflict", conflictedVersion)
        status.add(DAWarning(message))
      }
    }
    return status
  }

  private fun DependencyNode.getDependencyData(): Dependency.Data? {
    return when (this) {
      is ProjectDependencyNode -> {
        val data = DAModule(projectName)
        val moduleData = getModuleData()
        data.putUserData(MODULE_DATA, moduleData)
        data
      }
      is ArtifactDependencyNode -> {
        DAArtifact(group, module, version)
      }
      else -> null
    }
  }

  private fun ProjectDependencyNode.getModuleData(): ModuleData? {
    return projects.values.asSequence()
      .map { it.moduleData }
      .find { it.id == projectPath }
  }

  companion object {
    internal val defaultConfiguration = scope("default")

    internal val MODULE_DATA = Key.create<ModuleData>("GradleDependencyAnalyzerContributor.ModuleData")

    private fun GradleModuleData.loadDependencies(project: Project): List<DependencyScopeNode> {
      var dependencyScopeNodes: List<DependencyScopeNode> = emptyList()
      val outputFile = FileUtil.createTempFile("dependencies", ".json", true)
      val taskConfiguration =
        """
        outputFile = project.file("${FileUtil.toCanonicalPath(outputFile.absolutePath)}")
        configurations = []
        """.trimIndent()
      GradleTaskManager.runCustomTask(
        project, GradleBundle.message("gradle.dependency.analyzer.loading"),
        GradleDependencyReportTask::class.java,
        directoryToRunTask,
        fullGradlePath,
        taskConfiguration,
        ProgressExecutionMode.NO_PROGRESS_SYNC,
        object : TaskCallback {
          override fun onSuccess() {
            val json = FileUtil.loadFile(outputFile)
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeAdapter(DependencyNode::class.java, GradleDependencyNodeDeserializer())
            val scopeNodes = gsonBuilder.create().fromJson(json, Array<DependencyScopeNode>::class.java)
            dependencyScopeNodes = scopeNodes?.asList() ?: emptyList()
          }

          override fun onFailure() {
          }
        }
      )
      FileUtil.asyncDelete(outputFile)
      return dependencyScopeNodes
    }

    private fun DependencyScopeNode.toScope() = scope(scope)

    private fun scope(name: @NlsSafe String) = DAScope(name, StringUtil.toTitleCase(name))
  }
}