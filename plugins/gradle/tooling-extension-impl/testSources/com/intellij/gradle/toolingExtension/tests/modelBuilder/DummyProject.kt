// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.tests.modelBuilder

import groovy.lang.Closure
import org.gradle.api.*
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.api.artifacts.dsl.DependencyLockingHandler
import org.gradle.api.component.SoftwareComponentContainer
import org.gradle.api.file.*
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.normalization.InputNormalizationHandler
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import java.io.File
import java.util.concurrent.Callable

class DummyProject(
  private val projectName: String,
  private val parent: DummyProject?
) : Project {

  private val projectPath: String = when {
    parent == null -> ":"
    parent.parent == null -> ":$projectName"
    else -> parent.getPath() + ":$projectName"
  }

  private val projectDisplayName: String = when {
    parent == null -> "root project '$projectName'"
    else -> "project '$projectPath'"
  }

  override fun getBuildFile(): File = File("build.gradle")

  override fun getParent(): Project? = parent

  override fun getName(): String = projectName

  override fun getPath(): String = projectPath

  override fun getDisplayName(): String = projectDisplayName

  override fun compareTo(other: Project?) = throw UnsupportedOperationException()

  override fun getExtensions() = throw UnsupportedOperationException()

  override fun getPlugins() = throw UnsupportedOperationException()

  override fun apply(closure: Closure<*>) = throw UnsupportedOperationException()

  override fun apply(action: Action<in ObjectConfigurationAction>) = throw UnsupportedOperationException()

  override fun apply(options: MutableMap<String, *>) = throw UnsupportedOperationException()

  override fun getPluginManager() = throw UnsupportedOperationException()

  override fun getRootProject() = throw UnsupportedOperationException()

  override fun getRootDir() = throw UnsupportedOperationException()

  override fun getBuildDir() = throw UnsupportedOperationException()

  override fun setBuildDir(path: File) = throw UnsupportedOperationException()

  override fun setBuildDir(path: Any) = throw UnsupportedOperationException()

  override fun getDescription() = throw UnsupportedOperationException()

  override fun setDescription(description: String?) = throw UnsupportedOperationException()

  override fun getGroup() = throw UnsupportedOperationException()

  override fun setGroup(group: Any) = throw UnsupportedOperationException()

  override fun getVersion() = throw UnsupportedOperationException()

  override fun setVersion(version: Any) = throw UnsupportedOperationException()

  override fun getStatus() = throw UnsupportedOperationException()

  override fun setStatus(status: Any) = throw UnsupportedOperationException()

  override fun getChildProjects() = throw UnsupportedOperationException()

  override fun setProperty(name: String, value: Any?) = throw UnsupportedOperationException()

  override fun getProject() = throw UnsupportedOperationException()

  override fun getAllprojects() = throw UnsupportedOperationException()

  override fun getSubprojects() = throw UnsupportedOperationException()

  override fun task(name: String) = throw UnsupportedOperationException()

  override fun task(args: MutableMap<String, *>, name: String) = throw UnsupportedOperationException()

  override fun task(args: MutableMap<String, *>, name: String, configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun task(name: String, configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun task(name: String, configureAction: Action<in Task>) = throw UnsupportedOperationException()

  override fun getDefaultTasks() = throw UnsupportedOperationException()

  override fun setDefaultTasks(defaultTasks: MutableList<String>) = throw UnsupportedOperationException()

  override fun defaultTasks(vararg defaultTasks: String?) = throw UnsupportedOperationException()

  override fun evaluationDependsOn(path: String) = throw UnsupportedOperationException()

  override fun evaluationDependsOnChildren() = throw UnsupportedOperationException()

  override fun findProject(path: String) = throw UnsupportedOperationException()

  override fun project(path: String) = throw UnsupportedOperationException()

  override fun project(path: String, configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun project(path: String, configureAction: Action<in Project>) = throw UnsupportedOperationException()

  override fun getAllTasks(recursive: Boolean) = throw UnsupportedOperationException()

  override fun getTasksByName(name: String, recursive: Boolean) = throw UnsupportedOperationException()

  override fun getProjectDir() = throw UnsupportedOperationException()

  override fun file(path: Any) = throw UnsupportedOperationException()

  override fun file(path: Any, validation: PathValidation) = throw UnsupportedOperationException()

  override fun uri(path: Any) = throw UnsupportedOperationException()

  override fun relativePath(path: Any) = throw UnsupportedOperationException()

  override fun files(vararg paths: Any?) = throw UnsupportedOperationException()

  override fun files(paths: Any, configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun files(paths: Any, configureAction: Action<in ConfigurableFileCollection>) = throw UnsupportedOperationException()

  override fun fileTree(baseDir: Any) = throw UnsupportedOperationException()

  override fun fileTree(baseDir: Any, configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun fileTree(baseDir: Any, configureAction: Action<in ConfigurableFileTree>) = throw UnsupportedOperationException()

  override fun fileTree(args: MutableMap<String, *>) = throw UnsupportedOperationException()

  override fun zipTree(zipPath: Any) = throw UnsupportedOperationException()

  override fun tarTree(tarPath: Any) = throw UnsupportedOperationException()

  override fun <T : Any?> provider(value: Callable<out T?>) = throw UnsupportedOperationException()

  override fun getProviders() = throw UnsupportedOperationException()

  override fun getObjects() = throw UnsupportedOperationException()

  override fun getLayout() = throw UnsupportedOperationException()

  override fun mkdir(path: Any) = throw UnsupportedOperationException()

  override fun delete(vararg paths: Any?) = throw UnsupportedOperationException()

  override fun delete(action: Action<in DeleteSpec>) = throw UnsupportedOperationException()

  override fun javaexec(closure: Closure<*>) = throw UnsupportedOperationException()

  override fun javaexec(action: Action<in JavaExecSpec>) = throw UnsupportedOperationException()

  override fun exec(closure: Closure<*>) = throw UnsupportedOperationException()

  override fun exec(action: Action<in ExecSpec>) = throw UnsupportedOperationException()

  override fun absoluteProjectPath(path: String) = throw UnsupportedOperationException()

  override fun relativeProjectPath(path: String) = throw UnsupportedOperationException()

  override fun getAnt() = throw UnsupportedOperationException()

  override fun createAntBuilder() = throw UnsupportedOperationException()

  override fun ant(configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun ant(configureAction: Action<in AntBuilder>) = throw UnsupportedOperationException()

  override fun getConfigurations() = throw UnsupportedOperationException()

  override fun configurations(configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun getArtifacts() = throw UnsupportedOperationException()

  override fun artifacts(configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun artifacts(configureAction: Action<in ArtifactHandler>) = throw UnsupportedOperationException()

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getConvention() = throw UnsupportedOperationException()

  override fun depthCompare(otherProject: Project) = throw UnsupportedOperationException()

  override fun getDepth() = throw UnsupportedOperationException()

  override fun getTasks() = throw UnsupportedOperationException()

  override fun subprojects(action: Action<in Project>) = throw UnsupportedOperationException()

  override fun subprojects(configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun allprojects(action: Action<in Project>) = throw UnsupportedOperationException()

  override fun allprojects(configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun beforeEvaluate(action: Action<in Project>) = throw UnsupportedOperationException()

  override fun beforeEvaluate(closure: Closure<*>) = throw UnsupportedOperationException()

  override fun afterEvaluate(action: Action<in Project>) = throw UnsupportedOperationException()

  override fun afterEvaluate(closure: Closure<*>) = throw UnsupportedOperationException()

  override fun hasProperty(propertyName: String) = throw UnsupportedOperationException()

  override fun getProperties() = throw UnsupportedOperationException()

  override fun property(propertyName: String) = throw UnsupportedOperationException()

  override fun findProperty(propertyName: String) = throw UnsupportedOperationException()

  override fun getLogger() = throw UnsupportedOperationException()

  override fun getGradle() = throw UnsupportedOperationException()

  override fun getLogging() = throw UnsupportedOperationException()

  override fun configure(`object`: Any, configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun configure(objects: MutableIterable<*>, configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun <T : Any?> configure(objects: MutableIterable<T>, configureAction: Action<in T>) = throw UnsupportedOperationException()

  override fun getRepositories() = throw UnsupportedOperationException()

  override fun repositories(configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun getDependencies() = throw UnsupportedOperationException()

  override fun dependencies(configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun getDependencyFactory() = throw UnsupportedOperationException()

  override fun getBuildscript() = throw UnsupportedOperationException()

  override fun buildscript(configureClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun copy(closure: Closure<*>) = throw UnsupportedOperationException()

  override fun copy(action: Action<in CopySpec>) = throw UnsupportedOperationException()

  override fun copySpec(closure: Closure<*>) = throw UnsupportedOperationException()

  override fun copySpec(action: Action<in CopySpec>) = throw UnsupportedOperationException()

  override fun copySpec() = throw UnsupportedOperationException()

  override fun sync(action: Action<in SyncSpec>) = throw UnsupportedOperationException()

  override fun getState() = throw UnsupportedOperationException()

  override fun <T : Any?> container(type: Class<T>) = throw UnsupportedOperationException()

  override fun <T : Any?> container(type: Class<T>, factory: NamedDomainObjectFactory<T>) = throw UnsupportedOperationException()

  override fun <T : Any?> container(type: Class<T>, factoryClosure: Closure<*>) = throw UnsupportedOperationException()

  override fun getResources() = throw UnsupportedOperationException()

  override fun getComponents() = throw UnsupportedOperationException()

  override fun components(configuration: Action<in SoftwareComponentContainer>) = throw UnsupportedOperationException()

  override fun getNormalization() = throw UnsupportedOperationException()

  override fun normalization(configuration: Action<in InputNormalizationHandler>) = throw UnsupportedOperationException()

  override fun dependencyLocking(configuration: Action<in DependencyLockingHandler>) = throw UnsupportedOperationException()

  override fun getDependencyLocking() = throw UnsupportedOperationException()

  override fun getBuildTreePath(): String? = throw UnsupportedOperationException()
}