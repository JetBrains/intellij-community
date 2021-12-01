// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.google.gson.GsonBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.Dependency
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.Dependency.Data.Artifact
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.InspectionResult.Warning.VersionConflict
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.dependencies.*
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.concurrency.runAsync
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.tooling.tasks.DependenciesReport
import org.jetbrains.plugins.gradle.tooling.tasks.DependencyNodeDeserializer
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleModuleData
import java.util.concurrent.ConcurrentHashMap

class GradleDependencyContributor(private val project: Project) : DependencyContributor {
  private val projects = mutableMapOf<String, GradleModuleData>()
  private val configurationNodesMap = ConcurrentHashMap<String, List<DependencyScopeNode>>()
  private val inspectionResults = mutableMapOf<Pair<String, Dependency>, List<DependencyContributor.InspectionResult>>()
  private val dependenciesData = ConcurrentHashMap<Long, Pair<Dependency.Data, List<DependencyContributor.InspectionResult>>>()
  private lateinit var updateViewTrigger: () -> Unit

  override fun whenDataChanged(listener: () -> Unit, parentDisposable: Disposable) {
    updateViewTrigger = listener
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onEnd(id: ExternalSystemTaskId) {
        if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        if (id.projectSystemId != GradleConstants.SYSTEM_ID) return
        projects.clear()
        configurationNodesMap.clear()
        inspectionResults.clear()
        dependenciesData.clear()
        updateViewTrigger()
      }
    }, parentDisposable)
  }

  override fun getExternalProjects(): List<DependencyContributor.ExternalProject> {
    if (projects.isEmpty()) {
      ProjectDataManager.getInstance().getExternalProjectsData(project, GradleConstants.SYSTEM_ID)
        .mapNotNull { it.externalProjectStructure }
        .flatMap { ExternalSystemApiUtil.findAll(it, ProjectKeys.MODULE) }
        .map(::GradleModuleData)
        .filterNot(GradleModuleData::isBuildSrcModule)
        .associateByTo(projects, GradleModuleData::gradleProjectDir)
    }
    return projects.values.map { DependencyContributor.ExternalProject(it.gradleProjectDir, it.moduleName) }
  }

  override fun getDependencyScopes(externalProjectPath: String): List<DependencyContributor.Scope> {
    val gradleModuleData = projects[externalProjectPath] ?: return emptyList()
    return getOrRefreshData(gradleModuleData).map { it.toScope() }
  }

  override fun getDependencyGroups(externalProjectPath: String): List<DependencyContributor.DependencyGroup> {
    val gradleModuleData = projects[externalProjectPath] ?: return emptyList()
    val scopeNodes = getOrRefreshData(gradleModuleData)
    return toDependencyGroups(gradleModuleData, scopeNodes)
  }

  override fun getInspectionResult(externalProjectPath: String,
                                   dependency: Dependency): List<DependencyContributor.InspectionResult> {
    return inspectionResults.getOrDefault(Pair(externalProjectPath, dependency), emptyList())
  }

  private fun getOrRefreshData(gradleModuleData: GradleModuleData): List<DependencyScopeNode> {
    return configurationNodesMap.computeIfAbsent(gradleModuleData.gradleProjectDir) { path ->
      runAsync { // TODO should be replaced with async loading data by DependencyAnalyzerViewImpl
        val dependencyScopeNodes = gradleModuleData.getDependencies(project)
        if (dependencyScopeNodes.isNotEmpty()) {
          configurationNodesMap[path] = dependencyScopeNodes
          updateViewTrigger()
        }
      }
      emptyList()
    }
  }

  private fun toDependencyGroups(moduleData: GradleModuleData,
                                 scopeNodes: List<DependencyScopeNode>): List<DependencyContributor.DependencyGroup> {
    if (scopeNodes.isEmpty()) return emptyList()
    val groups = mutableMapOf<Dependency.Data, MutableSet<Dependency>>()
    val root = Dependency.Data.Module(moduleData.moduleName)

    val rootDependency = Dependency(root, defaultConfiguration, null)
    groups[root] = mutableSetOf(rootDependency)
    for (scopeNode in scopeNodes) {
      val scope = scopeNode.toScope()
      for (dependencyNode in scopeNode.dependencies) {
        addDependencyGroup(rootDependency, scope, dependencyNode, groups, moduleData.gradleProjectDir)
      }
    }
    return groups.map { (data, dependencies) -> DependencyContributor.DependencyGroup(data, dependencies.toList()) }
  }

  private fun addDependencyGroup(usage: Dependency,
                                 scope: DependencyContributor.Scope,
                                 dependencyNode: DependencyNode,
                                 groups: MutableMap<Dependency.Data, MutableSet<Dependency>>,
                                 gradleProjectDir: String) {
    if (dependencyNode is ReferenceNode) {
      dependenciesData[dependencyNode.id]?.let { (data, inspections) ->
        val dependency = Dependency(data, scope, usage)
        groups.getOrPut(data, ::mutableSetOf).add(dependency)
        inspectionResults[gradleProjectDir to dependency] = inspections
      }
      return
    }

    val dependencyData = dependencyNode.toDependencyData() ?: return
    val dependency = Dependency(dependencyData, scope, usage)
    groups.getOrPut(dependencyData, ::mutableSetOf).add(dependency)

    val inspectionsList = mutableListOf<DependencyContributor.InspectionResult>()
    // see, org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency.ResolutionState
    if (dependencyNode.resolutionState == "FAILED" || dependencyNode.resolutionState == "UNRESOLVED") {
      inspectionsList.add(DependencyContributor.InspectionResult.Warning.Unresolved)
    }
    val selectionReason = dependencyNode.selectionReason
    if (dependencyData is Artifact && selectionReason != null && selectionReason.startsWith("between versions")) {
      val conflictedVersion = selectionReason.substringAfter("between versions ${dependencyData.version} and ", "")
      if (conflictedVersion.isNotEmpty()) {
        inspectionsList.add(VersionConflict(dependencyData.copy(version = conflictedVersion)))
      }
    }
    inspectionResults[gradleProjectDir to dependency] = inspectionsList
    dependenciesData[dependencyNode.id] = dependencyData to inspectionsList
    for (node in dependencyNode.dependencies) {
      addDependencyGroup(dependency, scope, node, groups, gradleProjectDir)
    }
  }

  private fun DependencyNode.toDependencyData(): Dependency.Data? {
    return when (this) {
      is ProjectDependencyNode -> {
        Dependency.Data.Module(projectName)
      }
      is ArtifactDependencyNode -> {
        Artifact(group, module, version)
      }
      is UnknownDependencyNode -> {
        Artifact("", displayName, "")
      }
      else -> null
    }
  }

  companion object {
    @Suppress("HardCodedStringLiteral")
    internal val defaultConfiguration = DependencyContributor.Scope("default", "default", "Default")
  }
}

private fun GradleModuleData.getDependencies(project: Project): List<DependencyScopeNode> {
  var dependencyScopeNodes: List<DependencyScopeNode> = emptyList()
  val outputFile = FileUtil.createTempFile("dependencies", ".json", true)
  val taskConfiguration =
    """
    outputFile = project.file("${FileUtil.toCanonicalPath(outputFile.absolutePath)}")
    configurations = []
    """.trimIndent()
  GradleTaskManager.runCustomTask(
    project, GradleBundle.message("gradle.dependency.analyzer.loading"),
    DependenciesReport::class.java,
    directoryToRunTask,
    fullGradlePath,
    taskConfiguration,
    ProgressExecutionMode.NO_PROGRESS_SYNC,
    object : TaskCallback {
      override fun onSuccess() {
        val json = FileUtil.loadFile(outputFile)
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(DependencyNode::class.java, DependencyNodeDeserializer())
        dependencyScopeNodes = gsonBuilder.create().fromJson(json, Array<DependencyScopeNode>::class.java).asList()
      }

      override fun onFailure() {
      }
    }
  )
  FileUtil.asyncDelete(outputFile)
  return dependencyScopeNodes
}

private fun DependencyScopeNode.toScope() = DependencyContributor.Scope(scope, scope, scope)