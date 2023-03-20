// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.junit.Test

class GradleRunAnythingProviderTest : GradleRunAnythingProviderTestCase() {

  override fun getTestsTempDir() = "tmp${System.currentTimeMillis()}"

  @Test
  fun `test class completion`() {
    createTestJavaClass("ClassA")
    createTestJavaClass("ClassB")
    createTestJavaClass("ClassC")
    createTestJavaClass("ClassD")
    createTestJavaClass("ClassE")
    createTestJavaClass("ClassF")
    createTestJavaClass("ClassG")
    importProject {
      withJavaPlugin()
      withJUnit4()
    }

    val wcCompletions = arrayOf(
      "gradle test --tests *ClassA",
      "gradle test --tests *ClassB",
      "gradle test --tests *ClassC",
      "gradle test --tests *ClassD",
      "gradle test --tests *ClassE",
      "gradle test --tests *ClassF",
      "gradle test --tests *ClassG"
    )
    val wcFqnCompletions = arrayOf(
      "gradle test --tests *.ClassA",
      "gradle test --tests *.ClassB",
      "gradle test --tests *.ClassC",
      "gradle test --tests *.ClassD",
      "gradle test --tests *.ClassE",
      "gradle test --tests *.ClassF",
      "gradle test --tests *.ClassG"
    )
    val fqnCompletions = arrayOf(
      "gradle test --tests org.jetbrains.ClassA",
      "gradle test --tests org.jetbrains.ClassB",
      "gradle test --tests org.jetbrains.ClassC",
      "gradle test --tests org.jetbrains.ClassD",
      "gradle test --tests org.jetbrains.ClassE",
      "gradle test --tests org.jetbrains.ClassF",
      "gradle test --tests org.jetbrains.ClassG"
    )
    withVariantsFor("gradle test ") { assertCollection(it, "gradle test --tests") }
    withVariantsFor("gradle test -") { assertCollection(it, "gradle test --tests") }
    withVariantsFor("gradle test --") { assertCollection(it, "gradle test --tests") }
    withVariantsFor("gradle test --t") { assertCollection(it, "gradle test --tests") }
    withVariantsFor("gradle test --tests ") { assertCollection(it, *wcCompletions) }
    withVariantsFor("gradle test --tests *") { assertCollection(it, *wcCompletions) }
    withVariantsFor("gradle test --tests *.") { assertCollection(it, *wcFqnCompletions) }
    withVariantsFor("gradle test --tests *.Class") { assertCollection(it, *wcFqnCompletions) }
    withVariantsFor("gradle test --tests org.jetbrains.") { assertCollection(it, *fqnCompletions) }
    withVariantsFor("gradle test --tests org.jetbrains.Class") { assertCollection(it, *fqnCompletions) }
  }

  @Test
  fun `test regular project`() {
    withVariantsFor("") {
      assertCollection(it, getGradleOptions(), !getCommonTasks(), !getCommonTasks(":"))
    }
  }

  @Test
  fun `test single project`() {
    importProject {}
    withVariantsFor("") {
      assertCollection(it, getGradleOptions(), getCommonTasks(), getCommonTasks(":"))
    }
    importProject { withTask("my-task") }
    withVariantsFor("") {
      assertCollection(it, getGradleOptions(), getCommonTasks(), getCommonTasks(":"))
      assertCollection(it, "my-task", ":my-task")
    }
    withVariantsFor("wrapper ") {
      assertCollection(it, "wrapper my-task", "wrapper :my-task")
    }
    withVariantsFor("my-task ") {
      assertCollection(it, getGradleOptions("my-task "), getCommonTasks("my-task "), getCommonTasks("my-task :"))
    }
    withVariantsFor(":my-task ") {
      assertCollection(it, getGradleOptions(":my-task "), getCommonTasks(":my-task "), getCommonTasks(":my-task :"))
    }
  }

  @Test
  fun `test multi-module project`() {
    createBuildFile { withTask("taskP") }
    createBuildFile("module") { withTask("taskM") }
    createBuildFile("composite") { withTask("taskC") }
    createBuildFile("composite/module") { withTask("taskCM") }
    createSettingsFile { include("module").includeBuild("composite") }
    createSettingsFile("composite") { include("module") }
    importProject()
    withVariantsFor("") {
      assertCollection(it, getGradleOptions())
      assertCollection(it, getRootProjectTasks(), getRootProjectTasks(":"))
      assertCollection(it, !getRootProjectTasks("module:"), !getRootProjectTasks(":module:"))
      assertCollection(it, !getRootProjectTasks("composite:"), getRootProjectTasks(":composite:"))
      assertCollection(it, !getRootProjectTasks("composite:module:"), !getRootProjectTasks(":composite:module:"))
      assertCollection(it, getCommonTasks(), getCommonTasks(":"))
      assertCollection(it, getCommonTasks("module:"), getCommonTasks(":module:"))
      assertCollection(it, !getCommonTasks("composite:"), getCommonTasks(":composite:"))
      assertCollection(it, !getCommonTasks("composite:module:"), getCommonTasks(":composite:module:"))
      assertCollection(it, "taskP", ":taskP", !"module:taskP", !":module:taskP")
      assertCollection(it, "taskM", !":taskM", "module:taskM", ":module:taskM")
      assertCollection(it, !"taskC", !":taskC", !"module:taskC", !":module:taskC")
      assertCollection(it, !"taskCM", !":taskCM", !"module:taskCM", !":module:taskCM")
      assertCollection(it, !"composite:taskC", ":composite:taskC", !"composite:module:taskC", !":composite:module:taskC")
      assertCollection(it, !"composite:taskCM", !":composite:taskCM", !"composite:module:taskCM", ":composite:module:taskCM")
    }
    withVariantsFor("", "project") {
      assertCollection(it, getGradleOptions())
      assertCollection(it, getRootProjectTasks(), getRootProjectTasks(":"))
      assertCollection(it, !getRootProjectTasks("module:"), !getRootProjectTasks(":module:"))
      assertCollection(it, !getRootProjectTasks("composite:"), getRootProjectTasks(":composite:"))
      assertCollection(it, !getRootProjectTasks("composite:module:"), !getRootProjectTasks(":composite:module:"))
      assertCollection(it, getCommonTasks(), getCommonTasks(":"))
      assertCollection(it, getCommonTasks("module:"), getCommonTasks(":module:"))
      assertCollection(it, !getCommonTasks("composite:"), getCommonTasks(":composite:"))
      assertCollection(it, !getCommonTasks("composite:module:"), getCommonTasks(":composite:module:"))
      assertCollection(it, "taskP", ":taskP", !"module:taskP", !":module:taskP")
      assertCollection(it, "taskM", !":taskM", "module:taskM", ":module:taskM")
      assertCollection(it, !"taskC", !":taskC", !"module:taskC", !":module:taskC")
      assertCollection(it, !"taskCM", !":taskCM", !"module:taskCM", !":module:taskCM")
      assertCollection(it, !"composite:taskC", ":composite:taskC", !"composite:module:taskC", !":composite:module:taskC")
      assertCollection(it, !"composite:taskCM", !":composite:taskCM", !"composite:module:taskCM", ":composite:module:taskCM")
    }
    withVariantsFor("", "project.module") {
      assertCollection(it, getGradleOptions())
      assertCollection(it, !getRootProjectTasks(), getRootProjectTasks(":"))
      assertCollection(it, !getRootProjectTasks("module:"), !getRootProjectTasks(":module:"))
      assertCollection(it, !getRootProjectTasks("composite:"), getRootProjectTasks(":composite:"))
      assertCollection(it, !getRootProjectTasks("composite:module:"), !getRootProjectTasks(":composite:module:"))
      assertCollection(it, getCommonTasks(), getCommonTasks(":"))
      assertCollection(it, !getCommonTasks("module:"), getCommonTasks(":module:"))
      assertCollection(it, !getCommonTasks("composite:"), getCommonTasks(":composite:"))
      assertCollection(it, !getCommonTasks("composite:module:"), getCommonTasks(":composite:module:"))
      assertCollection(it, !"taskP", ":taskP", !"module:taskP", !":module:taskP")
      assertCollection(it, "taskM", !":taskM", !"module:taskM", ":module:taskM")
      assertCollection(it, !"taskC", !":taskC", !"module:taskC", !":module:taskC")
      assertCollection(it, !"taskCM", !":taskCM", !"module:taskCM", !":module:taskCM")
      assertCollection(it, !"composite:taskC", ":composite:taskC", !"composite:module:taskC", !":composite:module:taskC")
      assertCollection(it, !"composite:taskCM", !":composite:taskCM", !"composite:module:taskCM", ":composite:module:taskCM")
    }
    withVariantsFor("", "composite") {
      assertCollection(it, getGradleOptions())
      assertCollection(it, getRootProjectTasks(), getRootProjectTasks(":"))
      assertCollection(it, !getRootProjectTasks("module:"), !getRootProjectTasks(":module:"))
      assertCollection(it, !getRootProjectTasks("composite:"), !getRootProjectTasks(":composite:"))
      assertCollection(it, !getRootProjectTasks("composite:module:"), !getRootProjectTasks(":composite:module:"))
      assertCollection(it, getCommonTasks(), getCommonTasks(":"))
      assertCollection(it, getCommonTasks("module:"), getCommonTasks(":module:"))
      assertCollection(it, !getCommonTasks("composite:"), !getCommonTasks(":composite:"))
      assertCollection(it, !getCommonTasks("composite:module:"), !getCommonTasks(":composite:module:"))
      assertCollection(it, !"taskP", !":taskP", !"module:taskP", !":module:taskP")
      assertCollection(it, !"taskM", !":taskM", !"module:taskM", !":module:taskM")
      assertCollection(it, "taskC", ":taskC", !"module:taskC", !":module:taskC")
      assertCollection(it, "taskCM", !":taskCM", "module:taskCM", ":module:taskCM")
      assertCollection(it, !"composite:taskC", !":composite:taskC", !"composite:module:taskC", !":composite:module:taskC")
      assertCollection(it, !"composite:taskCM", !":composite:taskCM", !"composite:module:taskCM", !":composite:module:taskCM")
    }
    withVariantsFor("", "composite.module") {
      assertCollection(it, getGradleOptions())
      assertCollection(it, !getRootProjectTasks(), getRootProjectTasks(":"))
      assertCollection(it, !getRootProjectTasks("module:"), !getRootProjectTasks(":module:"))
      assertCollection(it, !getRootProjectTasks("composite:"), !getRootProjectTasks(":composite:"))
      assertCollection(it, !getRootProjectTasks("composite:module:"), !getRootProjectTasks(":composite:module:"))
      assertCollection(it, getCommonTasks(), getCommonTasks(":"))
      assertCollection(it, !getCommonTasks("module:"), getCommonTasks(":module:"))
      assertCollection(it, !getCommonTasks("composite:"), !getCommonTasks(":composite:"))
      assertCollection(it, !getCommonTasks("composite:module:"), !getCommonTasks(":composite:module:"))
      assertCollection(it, !"taskP", !":taskP", !"module:taskP", !":module:taskP")
      assertCollection(it, !"taskM", !":taskM", !"module:taskM", !":module:taskM")
      assertCollection(it, !"taskC", ":taskC", !"module:taskC", !":module:taskC")
      assertCollection(it, "taskCM", !":taskCM", !"module:taskCM", ":module:taskCM")
      assertCollection(it, !"composite:taskC", !":composite:taskC", !"composite:module:taskC", !":composite:module:taskC")
      assertCollection(it, !"composite:taskCM", !":composite:taskCM", !"composite:module:taskCM", !":composite:module:taskCM")
    }
  }

  @Test
  fun `test running commands with build options and tasks arguments`() {
    importProject {
      withTask("taskWithArgs", "ArgsTask") {
        call("doLast") {
          call("println", code("myArgs"))
        }
      }
      addPostfix("""
        class ArgsTask extends DefaultTask {
            @Input
            @Option(option = 'my_args', description = '')
            String myArgs
        }
      """.trimIndent())
    }
    executeAndWait("help")
      .assertExecutionTree("""
        |-
        | -successful
        |  :help
      """.trimMargin())

    executeAndWait("--unknown-option help")
      .assertExecutionTree("""
        |-
        | -failed
        |  Task '--unknown-option' not found in root project 'project'.
      """.trimMargin())

    if (isGradleNewerOrSameAs("7.0")) {
      executeAndWait("taskWithArgs")
        .assertExecutionTree("""
          |-
          | -failed
          |  :taskWithArgs
          |  A problem was found with the configuration of task ':taskWithArgs' (type 'ArgsTask').
        """.trimMargin())
    }
    else {
      executeAndWait("taskWithArgs")
        .assertExecutionTree("""
          |-
          | -failed
          |  :taskWithArgs
          |  No value has been specified for property 'myArgs'
        """.trimMargin())
    }

    // test known build CLI option before tasks and with task quoted argument with apostrophe (')
    // (<build_option> <task> <arg>='<arg_value>')
    executeAndWait("-q taskWithArgs --my_args='test args'")
      .assertExecutionTree("""
        |-
        | -successful
        |  :taskWithArgs
      """.trimMargin())
      .assertExecutionTreeNode("successful") {
        assertThat(it).matches("""
          |(\d+):(\d+):(\d+)( AM| PM)?: Executing 'taskWithArgs --my_args='test args' -q'...
          |
          |(?:Starting Gradle Daemon...
          |Gradle Daemon started in .* ms
          |)?test args
          |(\d+):(\d+):(\d+)( AM| PM)?: Execution finished 'taskWithArgs --my_args='test args' -q'.
          |
        """.trimMargin())
      }
      .assertExecutionTreeNode(":taskWithArgs") {
        assertEmpty(it) // tasks output routing is not available for quiet mode
      }

    // test known build CLI option before tasks and with task quoted argument with quote (")
    // (<build_option> <task> <arg>="<arg_value>")
    executeAndWait("--info taskWithArgs --my_args=\"test args\"")
      .assertExecutionTree("""
        |-
        | -successful
        |  :taskWithArgs
      """.trimMargin())
      .assertExecutionTreeNode(":taskWithArgs") {
        assertThat(it).matches("""
          |> Task :taskWithArgs
          |Caching disabled for task ':taskWithArgs' because:
          | {2}Build cache is disabled
          |Task ':taskWithArgs' is not up-to-date because:
          | {2}Task has not declared any outputs despite executing actions.
          |test args
          |
          |
        """.trimMargin())
      }

    // test with task argument and known build CLI option after tasks
    // (<task> <arg>=<arg_value> <build_option>)
    executeAndWait("taskWithArgs --my_args=test_args --quiet")
      .assertExecutionTree("""
        |-
        | -successful
        |  :taskWithArgs
      """.trimMargin())
      .assertExecutionTreeNode("successful") {
        assertThat(it).matches("""
          |(\d+):(\d+):(\d+)( AM| PM)?: Executing 'taskWithArgs --my_args=test_args --quiet'...
          |
          |(?:Starting Gradle Daemon...
          |Gradle Daemon started in .* ms
          |)?test_args
          |(\d+):(\d+):(\d+)( AM| PM)?: Execution finished 'taskWithArgs --my_args=test_args --quiet'.
          |
        """.trimMargin())
      }

    // test with task argument and known build CLI option after tasks
    // (<task> <arg> <arg_value> <build_option>)
    executeAndWait("taskWithArgs --my_args test_args --quiet")
      .assertExecutionTree("""
        |-
        | -successful
        |  :taskWithArgs
      """.trimMargin())
      .assertExecutionTreeNode("successful") {
        assertThat(it).matches("""
          |(\d+):(\d+):(\d+)( AM| PM)?: Executing 'taskWithArgs --my_args test_args --quiet'...
          |
          |(?:Starting Gradle Daemon...
          |Gradle Daemon started in .* ms
          |)?test_args
          |(\d+):(\d+):(\d+)( AM| PM)?: Execution finished 'taskWithArgs --my_args test_args --quiet'.
          |
        """.trimMargin())
      }
  }
}