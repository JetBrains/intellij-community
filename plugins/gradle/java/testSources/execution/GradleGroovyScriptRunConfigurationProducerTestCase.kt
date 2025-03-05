// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.assertj.core.api.Assertions
import org.jetbrains.plugins.gradle.util.runReadActionAndWait

abstract class GradleGroovyScriptRunConfigurationProducerTestCase : GradleRunConfigurationProducerTestCase() {

  protected class TaskData(
    val name: String,
    val nameElement: LeafPsiElement
  )

  protected data class DeclarationWithMethod(
    val methodCall: String,
    val taskName: String
  )

  protected open fun getTaskData(
    buildScriptFile: VirtualFile
  ): Map<String, TaskData> = runReadActionAndWait {
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
    Assertions.assertThat(taskDataMap.keys)
      .describedAs("The set of task names extracted from build script is different to expected")
      .containsExactlyInAnyOrderElementsOf(expectedTaskNames)
    for (taskName in expectedTaskNames) {
      val task = taskDataMap[taskName]!!
      verifyRunConfigurationProducer<GradleGroovyScriptRunConfigurationProducer>(expectedSettings = task.name, task.nameElement)
    }
  }
}
