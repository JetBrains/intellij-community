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
      DeclarationWithMethod(methodCall = "$tasksVariableName.create", taskName = "nameVarTasksCreate"),

      DeclarationWithMethod(methodCall = "project.task", taskName = "nameProjectTask"),
      DeclarationWithMethod(methodCall = "getProject().task", taskName = "nameGetProjectTask"),
      DeclarationWithMethod(methodCall = "$projectVariableName.task", taskName = "nameVarProjectTask"),
    )

    val taskNameNoParentheses = "nameTaskNoParentheses"
    val buildFile = createBuildFile {
      addVariables()
      declaredWithMethods.forEach {
        withTaskDeclaringMethod(it.methodCall, it.taskName)
      }
      withPostfix{
        call("task $taskNameNoParentheses") {
          callPrintln(taskNameNoParentheses)
        }
      }
    }

    val expectedTaskNames = declaredWithMethods
      .map(DeclarationWithMethod::taskName)
      .toMutableSet()
      .apply { add(taskNameNoParentheses) }
    val taskDataMap = importAndGetTaskData(buildFile)
    assertAllTasksHaveConfiguration(expectedTaskNames, taskDataMap)
  }

  @TargetVersions("4.9+")
  @Test
  fun `test tasks registering`() {
    val declaredWithMethods = listOf(
      DeclarationWithMethod(methodCall = "tasks.register", taskName = "nameTasksRegister"),
      DeclarationWithMethod(methodCall = "getTasks().register", taskName = "nameGetTasksRegister"),
      DeclarationWithMethod(methodCall = "project.tasks.register", taskName = "nameProjectTasksRegister"),
      DeclarationWithMethod(methodCall = "$projectVariableName.getTasks().register", taskName = "nameVarProjectGetTasksRegister"),
      DeclarationWithMethod(methodCall = "$tasksVariableName.register", taskName = "nameVarTasksRegister"),
    )
    val buildFile = createBuildFile {
      addVariables()
      declaredWithMethods.forEach {
        withTaskDeclaringMethod(it.methodCall, it.taskName)
      }
    }
    val taskDataMap = importAndGetTaskData(buildFile)
    val expectedTaskNames = declaredWithMethods.map(DeclarationWithMethod::taskName).toSet()
    assertAllTasksHaveConfiguration(expectedTaskNames, taskDataMap)
  }
}