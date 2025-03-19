// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util

import com.intellij.gradle.toolingExtension.impl.initScript.util.GradleTasksUtil
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import org.junit.jupiter.api.Test

class GradleTasksUtilTest : GradleTasksUtilTestCase() {

  @Test
  fun `test resolve start tasks by task name in simple project`() {
    val projectRoot = tempDirectory.resolve("project")

    val startParameter = createStartParameter(projectRoot, listOf("task"))
    val gradle = createGradle(startParameter)
    val project = createProject(gradle, projectRoot, ":", "project")
    val task = createTask(":task", "task")

    gradle.rootProject = project
    project.tasks.add(task)

    val startTasks = GradleTasksUtil.getStartTasks(project)
    CollectionAssertions.assertEqualsOrdered(listOf(task), startTasks)
  }

  @Test
  fun `test resolve start tasks by task path in simple project`() {
    val projectRoot = tempDirectory.resolve("project")

    val startParameter = createStartParameter(projectRoot, listOf(":task"))
    val gradle = createGradle(startParameter)
    val project = createProject(gradle, projectRoot, ":", "project")
    val task = createTask(":task", "task")

    gradle.rootProject = project
    project.tasks.add(task)

    val startTasks = GradleTasksUtil.getStartTasks(project)
    CollectionAssertions.assertEqualsOrdered(listOf(task), startTasks)
  }

  @Test
  fun `test resolve start tasks by task names in simple project with several tasks`() {
    val projectRoot = tempDirectory.resolve("project")

    val startParameter = createStartParameter(projectRoot, listOf("task1", "task2"))
    val gradle = createGradle(startParameter)
    val project = createProject(gradle, projectRoot, ":", "project")
    val task1 = createTask(":task1", "task1")
    val task2 = createTask(":task2", "task2")
    val task3 = createTask(":task3", "task3")

    gradle.rootProject = project
    project.tasks.add(task1)
    project.tasks.add(task2)
    project.tasks.add(task3)

    val startTasks = GradleTasksUtil.getStartTasks(project)
    CollectionAssertions.assertEqualsOrdered(listOf(task1, task2), startTasks)
  }

  @Test
  fun `test resolve start tasks by task paths in simple project with several tasks`() {
    val projectRoot = tempDirectory.resolve("project")

    val startParameter = createStartParameter(projectRoot, listOf(":task1", ":task2"))
    val gradle = createGradle(startParameter)
    val project = createProject(gradle, projectRoot, ":", "project")
    val task1 = createTask(":task1", "task1")
    val task2 = createTask(":task2", "task2")
    val task3 = createTask(":task3", "task3")

    gradle.rootProject = project
    project.tasks.add(task1)
    project.tasks.add(task2)
    project.tasks.add(task3)

    val startTasks = GradleTasksUtil.getStartTasks(project)
    CollectionAssertions.assertEqualsOrdered(listOf(task1, task2), startTasks)
  }

  @Test
  fun `test resolve start tasks by task name prefix in simple project`() {
    val projectRoot = tempDirectory.resolve("project")

    val startParameter = createStartParameter(projectRoot, listOf("task1"))
    val gradle = createGradle(startParameter)
    val project = createProject(gradle, projectRoot, ":", "project")
    val task11 = createTask(":task11", "task11")
    val task12 = createTask(":task12", "task12")
    val task21 = createTask(":task21", "task21")
    val task22 = createTask(":task22", "task22")

    gradle.rootProject = project
    project.tasks.add(task11)
    project.tasks.add(task12)
    project.tasks.add(task21)
    project.tasks.add(task22)

    val startTasks = GradleTasksUtil.getStartTasks(project)
    CollectionAssertions.assertEqualsOrdered(listOf(task11, task12), startTasks)
  }

  @Test
  fun `test resolve start tasks by task path prefix in simple project`() {
    val projectRoot = tempDirectory.resolve("project")

    val startParameter = createStartParameter(projectRoot, listOf(":task1"))
    val gradle = createGradle(startParameter)
    val project = createProject(gradle, projectRoot, ":", "project")
    val task11 = createTask(":task11", "task11")
    val task12 = createTask(":task12", "task12")
    val task21 = createTask(":task21", "task21")
    val task22 = createTask(":task22", "task22")

    gradle.rootProject = project
    project.tasks.add(task11)
    project.tasks.add(task12)
    project.tasks.add(task21)
    project.tasks.add(task22)

    val startTasks = GradleTasksUtil.getStartTasks(project)
    CollectionAssertions.assertEqualsOrdered(listOf(task11, task12), startTasks)
  }

  @Test
  fun `test resolve start tasks by task name in multi-module project`() {
    val projectRoot = tempDirectory.resolve("project")
    val moduleRoot = projectRoot.resolve("module")

    val startParameter = createStartParameter(projectRoot, listOf("task"))
    val gradle = createGradle(startParameter)
    val rootProject = createProject(gradle, projectRoot, ":", "project")
    val moduleProject = createProject(gradle, moduleRoot, ":module", "module")
    val moduleProjectTask = createTask(":module:task", "task")

    gradle.rootProject = rootProject
    moduleProject.parent = rootProject
    moduleProject.tasks.add(moduleProjectTask)

    val startTasks = GradleTasksUtil.getStartTasks(moduleProject)
    CollectionAssertions.assertEqualsOrdered(listOf(moduleProjectTask), startTasks)
  }

  @Test
  fun `test resolve start tasks by task path in multi-module project`() {
    val projectRoot = tempDirectory.resolve("project")
    val moduleRoot = projectRoot.resolve("module")

    val startParameter = createStartParameter(projectRoot, listOf(":module:task"))
    val gradle = createGradle(startParameter)
    val rootProject = createProject(gradle, projectRoot, ":", "project")
    val moduleProject = createProject(gradle, moduleRoot, ":module", "module")
    val moduleProjectTask = createTask(":module:task", "task")

    gradle.rootProject = rootProject
    moduleProject.parent = rootProject
    moduleProject.tasks.add(moduleProjectTask)

    val startTasks = GradleTasksUtil.getStartTasks(moduleProject)
    CollectionAssertions.assertEqualsOrdered(listOf(moduleProjectTask), startTasks)
  }

  @Test
  fun `test resolve start tasks by task relative path in multi-module project`() {
    val projectRoot = tempDirectory.resolve("project")
    val moduleRoot = projectRoot.resolve("module")

    val startParameter = createStartParameter(projectRoot, listOf("module:task"))
    val gradle = createGradle(startParameter)
    val rootProject = createProject(gradle, projectRoot, ":", "project")
    val moduleProject = createProject(gradle, moduleRoot, ":module", "module")
    val moduleProjectTask = createTask(":module:task", "task")

    gradle.rootProject = rootProject
    moduleProject.parent = rootProject
    moduleProject.tasks.add(moduleProjectTask)

    val startTasks = GradleTasksUtil.getStartTasks(moduleProject)
    CollectionAssertions.assertEqualsOrdered(listOf(moduleProjectTask), startTasks)
  }

  @Test
  fun `test resolve start tasks by task name in multi-module project from module root`() {
    val projectRoot = tempDirectory.resolve("project")
    val moduleRoot = projectRoot.resolve("module")

    val startParameter = createStartParameter(moduleRoot, listOf("task"))
    val gradle = createGradle(startParameter)
    val rootProject = createProject(gradle, projectRoot, ":", "project")
    val moduleProject = createProject(gradle, moduleRoot, ":module", "module")
    val moduleProjectTask = createTask(":module:task", "task")

    gradle.rootProject = rootProject
    moduleProject.parent = rootProject
    moduleProject.tasks.add(moduleProjectTask)

    val startTasks = GradleTasksUtil.getStartTasks(moduleProject)
    CollectionAssertions.assertEqualsOrdered(listOf(moduleProjectTask), startTasks)
  }

  @Test
  fun `test resolve start tasks by task path in multi-module project from module root`() {
    val projectRoot = tempDirectory.resolve("project")
    val moduleRoot = projectRoot.resolve("module")

    val startParameter = createStartParameter(moduleRoot, listOf(":module:task"))
    val gradle = createGradle(startParameter)
    val rootProject = createProject(gradle, projectRoot, ":", "project")
    val moduleProject = createProject(gradle, moduleRoot, ":module", "module")
    val moduleProjectTask = createTask(":module:task", "task")

    gradle.rootProject = rootProject
    moduleProject.parent = rootProject
    moduleProject.tasks.add(moduleProjectTask)

    val startTasks = GradleTasksUtil.getStartTasks(moduleProject)
    CollectionAssertions.assertEqualsOrdered(listOf(moduleProjectTask), startTasks)
  }

  @Test
  fun `test cannot resolve start tasks by task relative path in multi-module project from module root`() {
    val projectRoot = tempDirectory.resolve("project")
    val moduleRoot = projectRoot.resolve("module")

    val startParameter = createStartParameter(moduleRoot, listOf("module:task"))
    val gradle = createGradle(startParameter)
    val rootProject = createProject(gradle, projectRoot, ":", "project")
    val moduleProject = createProject(gradle, moduleRoot, ":module", "module")
    val moduleProjectTask = createTask(":module:task", "task")

    gradle.rootProject = rootProject
    moduleProject.parent = rootProject
    moduleProject.tasks.add(moduleProjectTask)

    val startTasks = GradleTasksUtil.getStartTasks(moduleProject)
    CollectionAssertions.assertEmpty(startTasks)
  }

  @Test
  fun `test resolve start tasks by task name in composite project`() {
    val projectRoot = tempDirectory.resolve("project")
    val includedBuildRoot = projectRoot.resolve("included-build")

    val rootStartParameter = createStartParameter(projectRoot, listOf("task"))
    val rootGradle = createGradle(rootStartParameter)
    val rootProject = createProject(rootGradle, projectRoot, ":", "project")
    val includedBuildStartParameter = createStartParameter(includedBuildRoot, emptyList())
    val includedBuildGradle = createGradle(includedBuildStartParameter)
    val includedBuildProject = createProject(includedBuildGradle, includedBuildRoot, ":", "included-build")
    val includedBuildProjectTask = createTask(":task", "task")

    rootGradle.rootProject = rootProject
    includedBuildGradle.parent = rootGradle
    includedBuildGradle.rootProject = includedBuildProject
    includedBuildProject.tasks.add(includedBuildProjectTask)

    val startTasks = GradleTasksUtil.getStartTasks(includedBuildProject)
    CollectionAssertions.assertEqualsOrdered(listOf(includedBuildProjectTask), startTasks)
  }

  @Test
  fun `test resolve start tasks by task path in composite project`() {
    val projectRoot = tempDirectory.resolve("project")
    val includedBuildRoot = projectRoot.resolve("included-build")

    val rootStartParameter = createStartParameter(projectRoot, listOf(":included-build:task"))
    val rootGradle = createGradle(rootStartParameter)
    val rootProject = createProject(rootGradle, projectRoot, ":", "project")
    val includedBuildStartParameter = createStartParameter(includedBuildRoot, emptyList())
    val includedBuildGradle = createGradle(includedBuildStartParameter)
    val includedBuildProject = createProject(includedBuildGradle, includedBuildRoot, ":", "included-build")
    val includedBuildProjectTask = createTask(":task", "task")

    rootGradle.rootProject = rootProject
    includedBuildGradle.parent = rootGradle
    includedBuildGradle.rootProject = includedBuildProject
    includedBuildProject.tasks.add(includedBuildProjectTask)

    val startTasks = GradleTasksUtil.getStartTasks(includedBuildProject)
    CollectionAssertions.assertEqualsOrdered(listOf(includedBuildProjectTask), startTasks)
  }

  @Test
  fun `test cannot resolve start tasks by task relative path in composite project`() {
    val projectRoot = tempDirectory.resolve("project")
    val includedBuildRoot = projectRoot.resolve("included-build")

    val rootStartParameter = createStartParameter(projectRoot, listOf("included-build:task"))
    val rootGradle = createGradle(rootStartParameter)
    val rootProject = createProject(rootGradle, projectRoot, ":", "project")
    val includedBuildStartParameter = createStartParameter(includedBuildRoot, emptyList())
    val includedBuildGradle = createGradle(includedBuildStartParameter)
    val includedBuildProject = createProject(includedBuildGradle, includedBuildRoot, ":", "included-build")
    val includedBuildProjectTask = createTask(":task", "task")

    rootGradle.rootProject = rootProject
    includedBuildGradle.parent = rootGradle
    includedBuildGradle.rootProject = includedBuildProject
    includedBuildProject.tasks.add(includedBuildProjectTask)

    val startTasks = GradleTasksUtil.getStartTasks(includedBuildProject)
    CollectionAssertions.assertEmpty(startTasks)
  }
}
