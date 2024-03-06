// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.gradle.execution.test.producer.GradleRunConfigurationProducerTestCase
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.util.runReadActionAndWait

abstract class GradleGroovyScriptRunConfigurationProducerTestCase : GradleRunConfigurationProducerTestCase() {

  protected val tasksVariableName = "myTasks"
  protected val projectVariableName = "myProject"

  protected class TaskData(
    val name: String,
    val nameElement: LeafPsiElement
  )

  protected class DeclarationWithMethod(
    val methodCall: String,
    val taskName: String
  )

  protected fun TestGradleBuildScriptBuilder.addVariables() {
    addPostfix("def $projectVariableName = getProject()")
    addPostfix("def $tasksVariableName = $projectVariableName.getTasks()")
  }

  protected fun TestGradleBuildScriptBuilder.withTaskDeclaringMethod(
    method: String,
    taskName: String
  ) {
    withPostfix {
      val arguments = listOfNotNull(argument(taskName))
      call(method, arguments) {
        callPrintln(taskName)
      }
    }
  }

  protected fun ScriptTreeBuilder.callPrintln(taskName: String) {
    call("doFirst") {
      code("println('$taskName task created with tasks.create')")
    }
  }

  protected open fun importAndGetTaskData(
    buildScriptFile: VirtualFile
  ): Map<String, TaskData> = runReadActionAndWait {
    importProject()
    val psiManager = PsiManager.getInstance(myProject)
    val psiFile = psiManager.findFile(buildScriptFile)!!
    val leafs = PsiTreeUtil.findChildrenOfType(psiFile, LeafPsiElement::class.java)
    leafs.mapNotNull { leaf ->
      val taskName = GradleGroovyRunnerUtil.getTaskNameIfContains(leaf)
      taskName?.let { TaskData(it, leaf) }
    }.associateBy(TaskData::name)
  }

  protected fun assertAllTasksHaveConfiguration(
    expectedTaskNames: Set<String>,
    taskDataMap: Map<String, TaskData>
  ) {
    assertTrue("The set of task names extracted from build script is different to expected",
               expectedTaskNames == taskDataMap.keys)
    for (taskName in expectedTaskNames) {
      assertProducerSupportsTask(taskDataMap[taskName]!!)
    }
  }

  private fun assertProducerSupportsTask(task: TaskData) {
    verifyRunConfigurationProducer(expectedSettings = task.name, task.nameElement)
  }

  private fun verifyRunConfigurationProducer(
    expectedSettings: String,
    vararg elements: PsiElement
  ) = runReadActionAndWait {
    val context = getContextByLocation(*elements)
    val configurationFromContext = getConfigurationFromContext(context)
    val producer = configurationFromContext.configurationProducer as GradleGroovyScriptRunConfigurationProducer
    val configuration = configurationFromContext.configuration as ExternalSystemRunConfiguration
    assertTrue("Configuration can be setup by producer from his context",
               producer.setupConfigurationFromContext(configuration, context, Ref(context.psiLocation)))
    assertTrue("Producer have to identify configuration that was created by him",
               producer.isConfigurationFromContext(configuration, context))

    producer.onFirstRun(configurationFromContext, context, Runnable {})
    assertEquals(expectedSettings, configuration.settings.toString())
  }
}
