/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    assertEquals(buildFile, locationExternalSystemError.filePath)
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
