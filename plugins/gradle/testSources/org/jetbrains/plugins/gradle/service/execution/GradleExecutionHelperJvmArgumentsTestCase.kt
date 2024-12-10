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
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.testFramework.utils.notImplemented
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.build.BuildEnvironment
import java.io.File
import java.nio.file.Path

abstract class GradleExecutionHelperJvmArgumentsTestCase {

  fun createBuildEnvironment(workingDirectory: Path): BuildEnvironment {
    val buildIdentifier = MockBuildIdentifier(workingDirectory)
    return MockBuildEnvironment(buildIdentifier)
  }

  fun createOperation(): MockLongRunningOperation {
    return MockLongRunningOperation()
  }

  private class MockBuildIdentifier(
    private val workingDirectory: Path,
  ) : BuildIdentifier {
    override fun getRootDir(): File = workingDirectory.toFile()
  }

  private class MockBuildEnvironment(
    private val buildIdentifier: BuildIdentifier,
  ) : BuildEnvironment by notImplemented<BuildEnvironment>() {
    override fun getBuildIdentifier(): BuildIdentifier = buildIdentifier
  }

  class MockLongRunningOperation : LongRunningOperation by notImplemented<LongRunningOperation>() {

    var jvmArguments: MutableList<String> = ArrayList()
      private set

    override fun setJvmArguments(vararg jvmArguments: String) = apply {
      this.jvmArguments = jvmArguments.toMutableList()
    }

    override fun setJvmArguments(jvmArguments: Iterable<String>?) = apply {
      this.jvmArguments = jvmArguments?.toMutableList() ?: ArrayList()
    }

    override fun addJvmArguments(vararg jvmArguments: String) = apply {
      this.jvmArguments.addAll(jvmArguments)
    }

    override fun addJvmArguments(jvmArguments: Iterable<String>) = apply {
      this.jvmArguments.addAll(jvmArguments)
    }
  }
}
