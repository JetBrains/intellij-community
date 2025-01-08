// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util

import com.intellij.testFramework.common.mock.notImplemented
import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskContainer
import org.junit.jupiter.api.io.TempDir
import org.slf4j.Marker
import java.io.File
import java.nio.file.Path

abstract class GradleTasksUtilTestCase {

  @TempDir
  lateinit var tempDirectory: Path

  fun createStartParameter(workingDirectory: Path, taskNames: List<String>): StartParameter {
    val startParameter = StartParameter()
    startParameter.projectDir = workingDirectory.toFile()
    startParameter.setTaskNames(taskNames)
    return startParameter
  }

  fun createGradle(startParameter: StartParameter): MockGradle {
    return MockGradle(startParameter)
  }

  fun createProject(gradle: MockGradle, projectPath: Path, path: String, name: String): MockProject {
    return MockProject(gradle, path, name, projectPath)
  }

  fun createTask(path: String, name: String): MockTask {
    return MockTask(path, name)
  }

  class MockGradle(
    private val startParameter: StartParameter,
  ) : Gradle by notImplemented(Gradle::class.java) {

    private var parentGradle: Gradle? = null
    private lateinit var rootProject: Project

    fun setParent(gradle: Gradle?) {
      parentGradle = gradle
    }

    fun setRootProject(project: Project) {
      rootProject = project
    }

    override fun getParent(): Gradle? = parentGradle
    override fun getRootProject(): Project = rootProject
    override fun getStartParameter(): StartParameter = startParameter
    override fun toString(): String = rootProject.toString()
  }

  class MockProject(
    private val gradle: Gradle,
    private val path: String,
    private val name: String,
    private val projectPath: Path,
  ) : Project by notImplemented(Project::class.java) {

    private val logger = NoOpLogger("logger")
    private val tasks = MockTaskContainer()

    private var parentProject: MockProject? = null

    private val subprojects = LinkedHashSet<Project>()

    fun setParent(project: MockProject?) {
      parentProject = project
      project?.subprojects?.add(this)
    }

    override fun getLogger(): Logger = logger
    override fun getGradle(): Gradle = gradle
    override fun getName(): String = name
    override fun getPath(): String = path
    override fun getProjectDir(): File = projectPath.toFile().canonicalFile
    override fun getParent(): MockProject? = parentProject
    override fun getSubprojects(): Set<Project> = subprojects
    override fun getAllprojects(): Set<Project> = setOf(this) + subprojects + subprojects.flatMap { it.subprojects }
    override fun getDefaultTasks(): List<String> = listOf("help")
    override fun getTasks(): TaskContainer = tasks
    override fun toString(): String = if (path == ":") "root project '$name'" else "project $path"
  }

  class MockTaskContainer private constructor(
    private val delegate: MutableSet<Task>,
  ) : TaskContainer by notImplemented(TaskContainer::class.java), MutableSet<Task> {

    constructor() : this(LinkedHashSet())
    constructor(delegate: Collection<Task>) : this(LinkedHashSet(delegate))

    override fun hashCode() = delegate.hashCode()
    override fun equals(other: Any?) = delegate == other
    override fun toString() = delegate.toString()

    override val size: Int by delegate::size
    override fun isEmpty() = delegate.isEmpty()
    override fun contains(element: Task) = delegate.contains(element)
    override fun containsAll(elements: Collection<Task>) = delegate.containsAll(elements)
    override fun iterator() = delegate.iterator()
    override fun add(element: Task) = delegate.add(element)
    override fun addAll(elements: Collection<Task>) = delegate.addAll(elements)
    override fun remove(element: Task) = delegate.remove(element)
    override fun removeAll(elements: Collection<Task>) = delegate.removeAll(elements)
    override fun retainAll(elements: Collection<Task>) = delegate.retainAll(elements)
    override fun clear() = delegate.clear()

    override fun matching(spec: Spec<in Task>) = MockTaskContainer(delegate.filter { spec.isSatisfiedBy(it) })
  }

  class MockTask(
    private val path: String,
    private val name: String,
  ) : Task by notImplemented(Task::class.java) {
    override fun getPath(): String = path
    override fun getName(): String = name
    override fun toString(): String = "task $path"
  }

  private class NoOpLogger(private val name: String) : Logger {
    override fun getName(): String = name
    override fun isTraceEnabled(): Boolean = false
    override fun trace(msg: String?) {}
    override fun trace(format: String?, arg: Any?) {}
    override fun trace(format: String?, arg1: Any?, arg2: Any?) {}
    override fun trace(message: String, vararg objects: Any?) {}
    override fun trace(msg: String?, t: Throwable?) {}
    override fun isTraceEnabled(marker: Marker?): Boolean = false
    override fun trace(marker: Marker, msg: String?) {}
    override fun trace(marker: Marker, format: String?, arg: Any?) {}
    override fun trace(marker: Marker, format: String?, arg1: Any?, arg2: Any?) {}
    override fun trace(marker: Marker, message: String, vararg objects: Any?) {}
    override fun trace(marker: Marker, msg: String?, t: Throwable?) {}
    override fun isDebugEnabled(): Boolean = false
    override fun debug(msg: String?) {}
    override fun debug(format: String?, arg: Any?) {}
    override fun debug(format: String?, arg1: Any?, arg2: Any?) {}
    override fun debug(message: String, vararg objects: Any?) {}
    override fun debug(msg: String?, t: Throwable?) {}
    override fun isDebugEnabled(marker: Marker?): Boolean = false
    override fun debug(marker: Marker, msg: String?) {}
    override fun debug(marker: Marker, format: String?, arg: Any?) {}
    override fun debug(marker: Marker, format: String?, arg1: Any?, arg2: Any?) {}
    override fun debug(marker: Marker, message: String, vararg objects: Any?) {}
    override fun debug(marker: Marker, msg: String?, t: Throwable?) {}
    override fun isInfoEnabled(): Boolean = false
    override fun info(msg: String?) {}
    override fun info(format: String?, arg: Any?) {}
    override fun info(format: String?, arg1: Any?, arg2: Any?) {}
    override fun info(message: String, vararg objects: Any?) {}
    override fun info(msg: String?, t: Throwable?) {}
    override fun isInfoEnabled(marker: Marker?): Boolean = false
    override fun info(marker: Marker, msg: String?) {}
    override fun info(marker: Marker, format: String?, arg: Any?) {}
    override fun info(marker: Marker, format: String?, arg1: Any?, arg2: Any?) {}
    override fun info(marker: Marker, message: String, vararg objects: Any?) {}
    override fun info(marker: Marker, msg: String?, t: Throwable?) {}
    override fun isWarnEnabled(): Boolean = false
    override fun warn(msg: String?) {}
    override fun warn(format: String?, arg: Any?) {}
    override fun warn(format: String?, arg1: Any?, arg2: Any?) {}
    override fun warn(message: String, vararg objects: Any?) {}
    override fun warn(msg: String?, t: Throwable?) {}
    override fun isWarnEnabled(marker: Marker?): Boolean = false
    override fun warn(marker: Marker, msg: String?) {}
    override fun warn(marker: Marker, format: String?, arg: Any?) {}
    override fun warn(marker: Marker, format: String?, arg1: Any?, arg2: Any?) {}
    override fun warn(marker: Marker, message: String, vararg objects: Any?) {}
    override fun warn(marker: Marker, msg: String?, t: Throwable?) {}
    override fun isErrorEnabled(): Boolean = false
    override fun error(msg: String?) {}
    override fun error(format: String?, arg: Any?) {}
    override fun error(format: String?, arg1: Any?, arg2: Any?) {}
    override fun error(message: String, vararg objects: Any?) {}
    override fun error(msg: String?, t: Throwable?) {}
    override fun isErrorEnabled(marker: Marker?): Boolean = false
    override fun error(marker: Marker, msg: String?) {}
    override fun error(marker: Marker, format: String?, arg: Any?) {}
    override fun error(marker: Marker, format: String?, arg1: Any?, arg2: Any?) {}
    override fun error(marker: Marker, message: String, vararg objects: Any?) {}
    override fun error(marker: Marker, msg: String?, t: Throwable?) {}
    override fun isLifecycleEnabled(): Boolean = false
    override fun lifecycle(message: String?) {}
    override fun lifecycle(message: String?, vararg objects: Any?) {}
    override fun lifecycle(message: String?, throwable: Throwable?) {}
    override fun isQuietEnabled(): Boolean = false
    override fun quiet(message: String?) {}
    override fun quiet(message: String?, vararg objects: Any?) {}
    override fun quiet(message: String?, throwable: Throwable?) {}
    override fun isEnabled(level: LogLevel?): Boolean = false
    override fun log(level: LogLevel?, message: String?) {}
    override fun log(level: LogLevel?, message: String?, vararg objects: Any?) {}
    override fun log(level: LogLevel?, message: String?, throwable: Throwable?) {}
  }
}