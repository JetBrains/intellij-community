// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTask
import org.junit.jupiter.api.Assertions

abstract class GradleCommandLineParserTestCase {

  fun GradleCommandLine.assertNoText() = apply {
    Assertions.assertEquals("", text)
  }

  fun GradleCommandLine.assertText(expectedText: String) = apply {
    Assertions.assertEquals(expectedText, text)
  }

  fun GradleCommandLine.assertNoTaskText() = apply {
    Assertions.assertEquals("", tasks.text)
  }

  fun GradleCommandLine.assertTaskText(expectedText: String) = apply {
    Assertions.assertEquals(expectedText, tasks.text)
  }

  fun GradleCommandLine.assertNoOptionText() = apply {
    Assertions.assertEquals("", options.text)
  }

  fun GradleCommandLine.assertOptionText(expectedText: String) = apply {
    Assertions.assertEquals(expectedText, options.text)
  }

  fun GradleCommandLine.assertNoTokens() = apply {
    CollectionAssertions.assertEmpty(tokens)
  }

  fun GradleCommandLine.assertTokens(vararg expectedTokens: String) = apply {
    CollectionAssertions.assertEqualsOrdered(expectedTokens.asList(), tokens)
  }

  fun GradleCommandLine.assertNoTaskTokens() = apply {
    CollectionAssertions.assertEmpty(tasks.tokens)
  }

  fun GradleCommandLine.assertTaskTokens(vararg expectedTokens: String) = apply {
    CollectionAssertions.assertEqualsOrdered(expectedTokens.asList(), tasks.tokens)
  }

  fun GradleCommandLine.assertNoOptionTokens() = apply {
    CollectionAssertions.assertEmpty(options.tokens)
  }

  fun GradleCommandLine.assertOptionTokens(vararg expectedTokens: String) = apply {
    CollectionAssertions.assertEqualsOrdered(expectedTokens.asList(), options.tokens)
  }

  fun GradleCommandLine.assertNoTasks() = apply {
    CollectionAssertions.assertEmpty(tasks)
  }

  fun GradleCommandLine.assertNoOptions() = apply {
    CollectionAssertions.assertEmpty(options)
  }

  fun GradleCommandLine.assertTasks(vararg expectedTasks: SimpleTask) = apply {
    CollectionAssertions.assertEqualsOrdered(expectedTasks.asList(), tasks.map(SimpleTask::valueOf))
  }

  fun GradleCommandLine.assertOptions(vararg expectedOptions: SimpleOption) = apply {
    CollectionAssertions.assertEqualsOrdered(expectedOptions.asList(), options.map(SimpleOption::valueOf))
  }

  data class SimpleTask(val name: String, val options: List<SimpleOption>) {

    constructor(name: String, vararg options: SimpleOption) : this(name, options.asList())

    companion object {
      fun valueOf(task: GradleCommandLineTask): SimpleTask {
        return SimpleTask(task.name, task.options.map(SimpleOption::valueOf))
      }
    }
  }

  sealed interface SimpleOption {
    data class ShortNotation(val name: String, val value: String) : SimpleOption
    data class LongNotation(val name: String, val value: String) : SimpleOption
    data class VarargNotation(val name: String, val values: List<String>) : SimpleOption {
      constructor(name: String, vararg values: String) : this(name, values.toList())
    }
    data class PropertyNotation(val name: String, val propertyName: String, val propertyValue: String) : SimpleOption

    companion object {
      fun valueOf(option: GradleCommandLineOption): SimpleOption {
        return when (option) {
          is GradleCommandLineOption.ShortNotation -> ShortNotation(option.name, option.value)
          is GradleCommandLineOption.LongNotation -> LongNotation(option.name, option.value)
          is GradleCommandLineOption.VarargNotation -> VarargNotation(option.name, option.values)
          is GradleCommandLineOption.PropertyNotation -> PropertyNotation(option.name, option.propertyName, option.propertyValue)
        }
      }
    }
  }
}