// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution

import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GradleGroovyScriptRunConfigurationProducerTest : GradleGroovyScriptRunConfigurationProducerTestCase() {
  @Test
  fun `test base task declarations`() {
    val declaredWithMethods = listOf(
      DeclarationWithMethod(methodCall = "tasks.create", taskName = "nameTasksCreate"),
      DeclarationWithMethod(methodCall = "getTasks().create", taskName = "nameGetTasksCreate"),
      DeclarationWithMethod(methodCall = "project.tasks.create", taskName = "nameProjectTasksCreate"),
      DeclarationWithMethod(methodCall = "getProject().getTasks().create", taskName = "nameGetProjectGetTasksCreate"),
      DeclarationWithMethod(methodCall = "myTasks.create", taskName = "nameMyTasksCreate"),

      DeclarationWithMethod(methodCall = "project.task", taskName = "nameProjectTask"),
      DeclarationWithMethod(methodCall = "getProject().task", taskName = "nameGetProjectTask"),
      DeclarationWithMethod(methodCall = "myProject.task", taskName = "nameMyProjectTask"),
    )

    val taskNameNoParentheses = "nameTaskNoParentheses"
    val buildFile = createBuildFile {
      withPostfix {
        code("def myProject = getProject()")
        code("def myTasks = myProject.getTasks()")
        for ((methodCall, taskName) in declaredWithMethods) {
          call(methodCall, taskName) {
            call("doFirst") {
              call("println", "$taskName task created with $methodCall")
            }
          }
        }
        call("task $taskNameNoParentheses") {
          call("doFirst") {
            call("println('$taskNameNoParentheses task created as task taskName')")
          }
        }
      }
    }
    importProject()

    val expectedTaskNames = declaredWithMethods
      .map(DeclarationWithMethod::taskName)
      .toMutableSet()
      .apply { add(taskNameNoParentheses) }
    val taskDataMap = getTaskData(buildFile)
    assertAllTasksHaveConfiguration(expectedTaskNames, taskDataMap)
  }

  @Test
  @TargetVersions("4.9+")
  fun `test tasks registering`() {
    val declaredWithMethods = listOf(
      DeclarationWithMethod(methodCall = "tasks.register", taskName = "nameTasksRegister"),
      DeclarationWithMethod(methodCall = "getTasks().register", taskName = "nameGetTasksRegister"),
      DeclarationWithMethod(methodCall = "project.tasks.register", taskName = "nameProjectTasksRegister"),
      DeclarationWithMethod(methodCall = "myProject.getTasks().register", taskName = "nameVarProjectGetTasksRegister"),
      DeclarationWithMethod(methodCall = "myTasks.register", taskName = "nameVarTasksRegister"),
    )

    val buildFile = createBuildFile {
      withPostfix {
        code("def myProject = getProject()")
        code("def myTasks = myProject.getTasks()")
        for ((methodCall, taskName) in declaredWithMethods) {
          call(methodCall, taskName) {
            call("doFirst") {
              call("println", "$taskName task created with $methodCall")
            }
          }
        }
      }
    }
    importProject()
    val taskDataMap = getTaskData(buildFile)
    val expectedTaskNames = declaredWithMethods.map(DeclarationWithMethod::taskName).toSet()
    assertAllTasksHaveConfiguration(expectedTaskNames, taskDataMap)
  }

  @Test
  @TargetVersions("5.0+")
  fun `test tasks named`() {
    val declaredWithMethods = listOf(
      DeclarationWithMethod(methodCall = "tasks.named", taskName = "taskForNamed"),
      DeclarationWithMethod(methodCall = "getTasks().named", taskName = "taskForNamed"),
      DeclarationWithMethod(methodCall = "project.tasks.named", taskName = "taskForNamed"),
      DeclarationWithMethod(methodCall = "myProject.getTasks().named", taskName = "taskForNamed"),
      DeclarationWithMethod(methodCall = "myTasks.named", taskName = "taskForNamed"),
    )

    val buildFile = createBuildFile {
      withPostfix {
        code("def myProject = getProject()")
        code("def myTasks = myProject.getTasks()")
        withTask("taskForNamed")
        for ((methodCall, taskName) in declaredWithMethods) {
          call(methodCall, taskName) {
            call("doFirst") {
              call("println", "$taskName task configured with $methodCall")
            }
          }
        }
      }
    }
    importProject()
    val taskDataMap = getTaskData(buildFile)
    val expectedTaskNames = declaredWithMethods.map(DeclarationWithMethod::taskName).toSet()
    assertAllTasksHaveConfiguration(expectedTaskNames, taskDataMap)
  }
}