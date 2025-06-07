// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.tests.modelBuilder

import com.intellij.testFramework.common.mock.notImplemented
import org.gradle.api.Project
import org.jetbrains.plugins.gradle.tooling.DefaultMessageBuilder
import org.jetbrains.plugins.gradle.tooling.MessageBuilder
import org.junit.jupiter.api.Assertions
import java.io.File

abstract class MessageBuilderTestCase {

  protected fun assertMessageTitle(expectedTitle: String, configure: MessageBuilder.() -> Unit) {
    val messageBuilder = DefaultMessageBuilder()
    messageBuilder.configure()
    val message = messageBuilder.build()
    Assertions.assertEquals(expectedTitle, message.title)
  }

  protected fun assertMessageText(expectedText: String, configure: MessageBuilder.() -> Unit) {
    val messageBuilder = DefaultMessageBuilder()
    messageBuilder.configure()
    val message = messageBuilder.build()
    Assertions.assertEquals(expectedText, message.text)
  }

  protected fun createProject(projectName: String, parent: Project?): Project {
    return MockProject(projectName, parent)
  }

  private class MockProject(
    private val projectName: String,
    private val parent: Project?,
  ) : Project by notImplemented(Project::class.java) {

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
  }

  companion object {

    @JvmStatic
    protected val TEST_MESSAGE_GROUP = "gradle.test.group"
  }
}