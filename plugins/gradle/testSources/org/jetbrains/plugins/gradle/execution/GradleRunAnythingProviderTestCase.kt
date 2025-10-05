// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution

import com.intellij.build.BuildDuration
import com.intellij.build.BuildTreeConsoleView
import com.intellij.build.BuildTreeNode
import com.intellij.build.BuildView
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.actions.runAnything.RunAnythingContext
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Ref
import com.intellij.platform.testFramework.assertion.BuildViewAssertions
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.Semaphore
import org.apache.commons.cli.Option
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider
import org.junit.Assert
import org.junit.jupiter.api.Assertions
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit
import javax.swing.tree.DefaultMutableTreeNode

abstract class GradleRunAnythingProviderTestCase : GradleImportingTestCase() {

  private lateinit var myDataContext: DataContext
  private lateinit var provider: GradleRunAnythingProvider

  override fun setUp() {
    super.setUp()
    provider = GradleRunAnythingProvider()
    myDataContext = SimpleDataContext.getProjectContext(myProject)
  }

  fun getRootProjectTasks(prefix: String = "") =
    listOf("init", "wrapper").map { prefix + it }

  fun getCommonTasks(prefix: String = ""): List<String> {
    val tasks = mutableListOf(
      "projects", "buildEnvironment", "dependencyInsight",
      "dependencies", "help", "properties", "tasks"
    )
    if (isGradleOlderThan("9.0")) {
      /**
      The model and component tasks report on the structure of legacy software model objects configured for the project.
      Previously, these tasks were automatically added to the project for every build.
      These tasks are now only added to a project when a rule-based plugin is applied
      (such as those provided by Gradle’s support for building native software).
       **/
      tasks.add("components")
      tasks.add("model")
    }
    return tasks.map { prefix + it }
  }

  fun getGradleOptions(prefix: String = ""): List<String> {
    val options = GradleCommandLineOptionsProvider.getSupportedOptions().options.filterIsInstance<Option>()
    val longOptions = options.mapNotNull { it.longOpt }.map { "--$it" }
    val shortOptions = options.mapNotNull { it.opt }.map { "-$it" }
    return (longOptions + shortOptions).map { prefix + it }
  }

  fun withVariantsFor(command: String, moduleName: String, action: (List<String>) -> Unit) {
    val moduleManager = ModuleManager.getInstance(myProject)
    val module = moduleManager.findModuleByName(moduleName)
    requireNotNull(module) { "Module '$moduleName' not found at ${moduleManager.modules.map { it.name }}" }
    withVariantsFor(RunAnythingContext.ModuleContext(module), command, action)
  }

  fun withVariantsFor(command: String, action: (List<String>) -> Unit) {
    withVariantsFor(RunAnythingContext.ProjectContext(myProject), command, action)
  }

  fun executeAndWait(command: String): BuildView {
    return executeAndWait(RunAnythingContext.ProjectContext(myProject), command)
  }

  private fun withVariantsFor(context: RunAnythingContext, command: String, action: (List<String>) -> Unit) {
    val dataContext = SimpleDataContext.getSimpleContext(RunAnythingProvider.EXECUTING_CONTEXT, context, myDataContext)
    val variants = provider.getValues(dataContext, "gradle $command")
    action(variants.map { it.removePrefix("gradle ") })
  }

  private fun executeAndWait(context: RunAnythingContext, command: String): BuildView {
    val notificationRef = Ref<Notification>()
    myProject.messageBus.connect(testRootDisposable).subscribe(
      Notifications.TOPIC,
      object : Notifications {
        override fun notify(notification: Notification) {
          notificationRef.set(notification)
        }
      }
    )

    val targetDone = Semaphore(1)
    val result = Ref<BuildView>()
    myProject.messageBus.connect(testRootDisposable).subscribe(
      ExecutionManager.EXECUTION_TOPIC,
      object : ExecutionListener {
        override fun processStartScheduled(executorIdLocal: String, environmentLocal: ExecutionEnvironment) = Unit
        override fun processNotStarted(executorIdLocal: String, environmentLocal: ExecutionEnvironment) = targetDone.up()
        override fun processTerminated(
          executorIdLocal: String,
          environmentLocal: ExecutionEnvironment,
          handler: ProcessHandler,
          exitCode: Int
        ) {
          val executionConsole = environmentLocal.contentToReuse!!.executionConsole as BuildView
          result.set(executionConsole)
          targetDone.up()
        }
      }
    )
    val dataContext = SimpleDataContext.getSimpleContext(RunAnythingProvider.EXECUTING_CONTEXT, context, myDataContext)
    provider.execute(dataContext, "gradle $command")

    for (i in 0..5000) {
      if (targetDone.waitFor(1)) break
    }
    Assert.assertTrue(targetDone.waitFor(1))

    val buildView = result.get()!!
    val eventView = buildView.getView(BuildTreeConsoleView::class.java.name, BuildTreeConsoleView::class.java)
    val tree = eventView!!.tree
    runInEdtAndWait {
      dispatchAllEventsInIdeEventQueue()
      PlatformTestUtil.waitWhileBusy(tree)
    }

    val buildNode = Ref<BuildTreeNode>()
    for (i in 0..5000) {
      eventView.invokeLater {
        val treeModel = tree.model
        val rootNode = treeModel.root
        assertThat(treeModel.getChildCount(rootNode)).isEqualTo(1)
        buildNode.set((treeModel.getChild(rootNode, 0) as DefaultMutableTreeNode).userObject as BuildTreeNode)
      }.blockingGet(5, TimeUnit.SECONDS)

      if (buildNode.get().duration !is BuildDuration.InProgress) break

      TimeoutUtil.sleep(5)
      runInEdtAndWait {
        dispatchAllEventsInIdeEventQueue()
        PlatformTestUtil.waitWhileBusy(tree)
      }
    }
    Assert.assertFalse("The tree node is still in Running state", buildNode.get().duration is BuildDuration.InProgress)
    return buildView
  }

  fun createTestJavaClass(name: String) {
    createProjectSubFile(
      "src/test/java/org/jetbrains/$name.java",
      """
      package org.jetbrains;
      import org.junit.Test;
      public class $name {
        @Test public void test() {}
      }
    """.trimIndent()
    )
  }


  fun BuildView.assertExecutionTree(expected: String): BuildView = apply {
    BuildViewAssertions.assertBuildViewTreeText(this) {
      Assertions.assertEquals(expected.trim(), it.trim())
    }
  }

  fun BuildView.assertExecutionTreeNode(nodeText: String, assert: (String) -> Unit): BuildView = apply {
    BuildViewAssertions.assertBuildViewNodeConsoleText(this, nodeText, assert)
  }

  companion object {
    /**
     * It's sufficient to run the test against one gradle version
     */
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = arrayListOf(arrayOf(BASE_GRADLE_VERSION))
  }
}