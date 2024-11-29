// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.tests.modelBuilder

import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.util.ExceptionUtil
import org.junit.jupiter.api.Test

@Suppress("checkReturnValue")
class MessageBuilderTest : MessageBuilderTestCase() {

  @Test
  fun `test title building by message builder`() {
    assertMessageTitle(TEST_MESSAGE_GROUP) {
      withGroup(TEST_MESSAGE_GROUP)
    }
    assertMessageTitle("Title") {
      withGroup(TEST_MESSAGE_GROUP)
      withTitle("Title")
    }
    assertMessageTitle("Title") {
      withGroup(TEST_MESSAGE_GROUP)
      withTitle("Title")
      withText("Text")
    }
    assertMessageTitle("Text") {
      withGroup(TEST_MESSAGE_GROUP)
      withText("Text")
    }
  }

  @Test
  fun `test text building by message builder`() {
    assertMessageText(TEST_MESSAGE_GROUP) {
      withGroup(TEST_MESSAGE_GROUP)
    }
    assertMessageText("Title") {
      withGroup(TEST_MESSAGE_GROUP)
      withTitle("Title")
    }
    assertMessageText("Text") {
      withGroup(TEST_MESSAGE_GROUP)
      withTitle("Title")
      withText("Text")
    }
    assertMessageText("Text") {
      withGroup(TEST_MESSAGE_GROUP)
      withText("Text")
    }
  }

  @Test
  fun `test title with exception building by message builder`() {
    val exception = Exception("Exception message")
    val causeException = Exception("Exception cause")
    val exceptionWithCause = Exception("Exception with cause message", causeException)
    val esException = ExternalSystemException("ES exception message")
    val esExceptionWithCause = ExternalSystemException("ES exception with cause message: ", causeException)
    val exceptionWithEsException = Exception("Exception with cause ES exception message", esException)
    val exceptionWithEsExceptionWithCause = Exception("Exception with cause ES exception with cause message", esExceptionWithCause)

    assertMessageTitle("Title") {
      withGroup(TEST_MESSAGE_GROUP)
      withTitle("Title")
      withException(exception)
    }
    assertMessageTitle("Exception message") {
      withGroup(TEST_MESSAGE_GROUP)
      withText("Text")
      withException(exception)
    }
    assertMessageTitle("Exception message") {
      withGroup(TEST_MESSAGE_GROUP)
      withException(exception)
    }
    assertMessageTitle("Exception cause") {
      withGroup(TEST_MESSAGE_GROUP)
      withException(exceptionWithCause)
    }
    assertMessageTitle("ES exception message") {
      withGroup(TEST_MESSAGE_GROUP)
      withException(esException)
    }
    assertMessageTitle("ES exception with cause message: Exception cause") {
      withGroup(TEST_MESSAGE_GROUP)
      withException(esExceptionWithCause)
    }
    assertMessageTitle("ES exception message") {
      withGroup(TEST_MESSAGE_GROUP)
      withException(exceptionWithEsException)
    }
    assertMessageTitle("ES exception with cause message: Exception cause") {
      withGroup(TEST_MESSAGE_GROUP)
      withException(exceptionWithEsExceptionWithCause)
    }
  }

  @Test
  fun `test text with exception building by message builder`() {
    val exception = Exception("Exception message")
    val causeException = Exception("Exception cause")
    val exceptionWithCause = Exception("Exception with cause message", causeException)
    val esException = ExternalSystemException("ES exception message")
    val esExceptionWithCause = ExternalSystemException("ES exception with cause message: ", causeException)
    val exceptionWithEsException = Exception("Exception with cause ES exception message", esException)
    val exceptionWithEsExceptionWithCause = Exception("Exception with cause ES exception with cause message", esExceptionWithCause)

    assertMessageText(ExceptionUtil.getThrowableText(exception)) {
      withGroup(TEST_MESSAGE_GROUP)
      withTitle("Title")
      withException(exception)
    }
    assertMessageText("Text\n\n" + ExceptionUtil.getThrowableText(exception)) {
      withGroup(TEST_MESSAGE_GROUP)
      withText("Text")
      withException(exception)
    }
    assertMessageText(ExceptionUtil.getThrowableText(exception)) {
      withGroup(TEST_MESSAGE_GROUP)
      withException(exception)
    }
    assertMessageText(ExceptionUtil.getThrowableText(exceptionWithCause)) {
      withGroup(TEST_MESSAGE_GROUP)
      withException(exceptionWithCause)
    }
    assertMessageText(ExceptionUtil.getThrowableText(esException)) {
      withGroup(TEST_MESSAGE_GROUP)
      withException(esException)
    }
    assertMessageText(ExceptionUtil.getThrowableText(esExceptionWithCause)) {
      withGroup(TEST_MESSAGE_GROUP)
      withException(esExceptionWithCause)
    }
    assertMessageText(ExceptionUtil.getThrowableText(exceptionWithEsException)) {
      withGroup(TEST_MESSAGE_GROUP)
      withException(exceptionWithEsException)
    }
    assertMessageText(ExceptionUtil.getThrowableText(exceptionWithEsExceptionWithCause) + "\n" +
                      "Caused by: " + ExceptionUtil.getThrowableText(causeException)) {
      withGroup(TEST_MESSAGE_GROUP)
      withException(exceptionWithEsExceptionWithCause)
    }
  }

  @Test
  fun `test title with project by message builder`() {
    val rootProject = DummyProject("project", null)
    val project = DummyProject("module", rootProject)
    val subProject = DummyProject("sub-module", project)
    val exception = Exception("Exception message")

    assertMessageTitle("root project 'project': Title") {
      withGroup(TEST_MESSAGE_GROUP)
      withTitle("Title")
      withProject(rootProject)
    }
    assertMessageTitle("root project 'project': Text") {
      withGroup(TEST_MESSAGE_GROUP)
      withText("Text")
      withProject(rootProject)
    }
    assertMessageTitle("root project 'project': $TEST_MESSAGE_GROUP") {
      withGroup(TEST_MESSAGE_GROUP)
      withProject(rootProject)
    }
    assertMessageTitle("root project 'project': Exception message") {
      withGroup(TEST_MESSAGE_GROUP)
      withException(exception)
      withProject(rootProject)
    }
    assertMessageTitle("project ':module': Title") {
      withGroup(TEST_MESSAGE_GROUP)
      withTitle("Title")
      withProject(project)
    }
    assertMessageTitle("project ':module:sub-module': Title") {
      withGroup(TEST_MESSAGE_GROUP)
      withTitle("Title")
      withProject(subProject)
    }
  }

  @Test
  fun `test text with project by message builder`() {
    val rootProject = DummyProject("project", null)
    val exception = Exception("Exception message")

    assertMessageText("Text") {
      withGroup(TEST_MESSAGE_GROUP)
      withText("Text")
      withProject(rootProject)
    }
    assertMessageText(ExceptionUtil.getThrowableText(exception)) {
      withGroup(TEST_MESSAGE_GROUP)
      withException(exception)
      withProject(rootProject)
    }
  }
}