// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dependency.analyzer

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency.Scope.Type.CUSTOM
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency.Scope.Type.STANDARD
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.dependencies.*
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleModuleData
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.gradleIdentityPathOrNull
import java.util.concurrent.ConcurrentHashMap
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency

@ApiStatus.Internal
class GradleDependencyAnalyzerContributor(private val project: Project) : DependencyAnalyzerContributor {
  private val projects = ConcurrentHashMap<DependencyAnalyzerProject, GradleModuleData>()
  private val dependencyMap = ConcurrentHashMap<Long, Dependency>()

  override fun whenDataChanged(listener: () -> Unit, parentDisposable: Disposable) {
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(object : ExternalSystemTaskNotificationListener {
      override fun onEnd(proojecPath: String, id: ExternalSystemTaskId) {
        if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        if (id.projectSystemId != GradleConstants.SYSTEM_ID) return
        projects.clear()
        dependencyMap.clear()
        listener()
      }
    }, parentDisposable)
  }

  override fun getProjects(): List<DependencyAnalyzerProject> {
    if (projects.isEmpty()) {
      val projectDataManager = ProjectDataManager.getInstance()
      for (projectInfo in projectDataManager.getExternalProjectsData(project, GradleConstants.SYSTEM_ID)) {
        val projectNode = projectInfo.externalProjectStructure ?: continue
        val projectData = projectNode.data
        for (moduleNode in ExternalSystemApiUtil.findAll(projectNode, ProjectKeys.MODULE)) {
          val moduleData = moduleNode.data
          val gradleModuleData = GradleModuleData(moduleNode)
          if (!gradleModuleData.isBuildSrcModule) {
            val module = GradleUtil.findGradleModule(project, moduleData) ?: continue
            val externalProjectName = projectData.externalName + (moduleData.gradleIdentityPathOrNull ?: (":" + moduleData.moduleName))
            val externalProject = DAProject(module, externalProjectName.removeSuffix(":"))
            projects[externalProject] = gradleModuleData
          }
        }
      }
    }
    return projects.keys.toList()
  }

  override fun getDependencyScopes(externalProject: DependencyAnalyzerProject): List<Dependency.Scope> {
    val gradleModuleData = projects[externalProject] ?: return emptyList()
    return getOrCollectDependencyScopeNodes(gradleModuleData).map { scope(it.scope) }
  }

  override fun getDependencies(externalProject: DependencyAnalyzerProject): List<Dependency> {
    val gradleModuleData = projects[externalProject] ?: return emptyList()
    val scopeNodes = getOrCollectDependencyScopeNodes(gradleModuleData)
    return getDependencies(gradleModuleData, scopeNodes)
  }

  private fun getOrCollectDependencyScopeNodes(gradleModuleData: GradleModuleData): List<DependencyScopeNode> {
    return LOG.runAndLogException {
      GradleDependencyNodeIndex.getOrCollectDependencies(project, gradleModuleData).get()
    } ?: emptyList()
  }

  private fun getDependencies(moduleData: GradleModuleData,
                              scopeNodes: List<DependencyScopeNode>): List<Dependency> {
    if (scopeNodes.isEmpty()) return emptyList()
    val dependencies = ArrayList<Dependency>()
    val root = DAModule(moduleData.moduleName)
    root.putUserData(MODULE_DATA, moduleData.moduleData)

    val rootDependency = DADependency(root, DEFAULT_SCOPE, null, emptyList())
    dependencies.add(rootDependency)
    for (scopeNode in scopeNodes) {
      val scope = scope(scopeNode.scope)
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
      val title = ExternalSystemBundle.message("external.system.dependency.analyzer.warning.unresolved.title")
      val message = ExternalSystemBundle.message("external.system.dependency.analyzer.warning.unresolved")
      status.add(DAWarning(title, message))
    }
    val selectionReason = selectionReason
    if (data is Dependency.Data.Artifact && selectionReason != null && selectionReason.startsWith("between versions")) {
      val conflictedVersion = selectionReason.substringAfter("between versions ${data.version} and ", "")
      if (conflictedVersion.isNotEmpty()) {
        val title = ExternalSystemBundle.message("external.system.dependency.analyzer.warning.version.conflict.title", conflictedVersion)
        val message = ExternalSystemBundle.message("external.system.dependency.analyzer.warning.version.conflict", conflictedVersion)
        status.add(DAWarning(title, message))
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

    private val LOG = logger<GradleDependencyAnalyzerContributor>()

    internal val MODULE_DATA = Key.create<ModuleData>("GradleDependencyAnalyzerContributor.ModuleData")

    internal val DEFAULT_SCOPE = DAScope(ExternalSystemBundle.message("external.system.dependency.analyzer.scope.default"))

    private val STANDARD_SCOPES = setOf(
      "annotationProcessor",
      "compileClasspath",
      "runtimeClasspath",
      "testAnnotationProcessor",
      "testCompileClasspath",
      "testRuntimeClasspath"
    )

    private fun scope(name: @Nls String): DAScope {
      return DAScope(name, type = scopeType(name))
    }

    private fun scopeType(name: String): Dependency.Scope.Type {
      return if (name in STANDARD_SCOPES) STANDARD else CUSTOM
    }
  }
}