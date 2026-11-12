// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.LocationAwareExternalSystemException
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.gradle.internal.exceptions.LocationAwareException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * @author Vladislav.Soroka
 */
@TestApplication
class BaseProjectImportErrorHandlerTest {

  private val projectRoot by tempPathFixture()

  @Test
  fun testGetUserFriendlyError() {
    val causeMsg = "failed to find target current"
    val rootCause = IllegalStateException(causeMsg)
    val buildFile = "~/project/build.gradle"
    val locationError = LocationAwareException(rootCause, "Build file '$buildFile'", 86)
    val error = Throwable(locationError)

    val actualRootCause = BaseProjectImportErrorHandler()
      .getUserFriendlyError(null, error, projectRoot.toCanonicalPath(), null)

    val locationExternalSystemError = assertInstanceOf(LocationAwareExternalSystemException::class.java, actualRootCause)
    assertEquals(Path.of(buildFile), Path.of(locationExternalSystemError.filePath))
    assertEquals(-1, locationExternalSystemError.column)
    assertEquals(86, locationExternalSystemError.line)
  }

  @Test
  fun testGetUserFriendlyErrorWithClassNotFoundException() {
    val causeMsg = "com.mypackage.MyImaginaryClass"
    val rootCause = ClassNotFoundException(causeMsg)
    val error = Throwable(rootCause)

    val actualRootCause = BaseProjectImportErrorHandler()
      .getUserFriendlyError(null, error, projectRoot.toCanonicalPath(), null)

    assertThat(actualRootCause.message).contains("Unable to load class 'com.mypackage.MyImaginaryClass'.")
  }
}
