// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.tests.modelBuilder

import org.jetbrains.plugins.gradle.tooling.DefaultMessageBuilder
import org.jetbrains.plugins.gradle.tooling.MessageBuilder
import org.junit.jupiter.api.Assertions

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

  companion object {

    @JvmStatic
    protected val TEST_MESSAGE_GROUP = "gradle.test.group"
  }
}