/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.execution.helper

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.common.mock.notImplemented
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.build.BuildEnvironment
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

@TestApplication
abstract class GradleExecutionHelperLogLeveTestCase {

  @TempDir
  protected lateinit var tempDirectory: Path

  fun createBuildEnvironment(workingDirectory: Path): MockBuildEnvironment {
    val buildIdentifier = MockBuildIdentifier(workingDirectory)
    return MockBuildEnvironment(buildIdentifier)
  }

  private class MockBuildIdentifier(
    private val workingDirectory: Path,
  ) : BuildIdentifier {
    override fun getRootDir(): File = workingDirectory.toFile()
  }

  class MockBuildEnvironment(
    private val buildIdentifier: BuildIdentifier,
  ) : BuildEnvironment by notImplemented<BuildEnvironment>() {
    override fun getBuildIdentifier(): BuildIdentifier = buildIdentifier
  }
}
