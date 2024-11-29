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

import org.gradle.tooling.CancellationToken
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.build.BuildEnvironment
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.ArrayList
import kotlin.collections.addAll

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
  ) : BuildEnvironment {
    override fun getBuildIdentifier(): BuildIdentifier = buildIdentifier
    override fun getGradle() = throw UnsupportedOperationException()
    override fun getJava() = throw UnsupportedOperationException()
  }

  class MockLongRunningOperation : LongRunningOperation {

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

    override fun setStandardOutput(outputStream: OutputStream) = throw UnsupportedOperationException()
    override fun setStandardError(outputStream: OutputStream) = throw UnsupportedOperationException()
    override fun setColorOutput(colorOutput: Boolean) = throw UnsupportedOperationException()
    override fun setStandardInput(inputStream: InputStream) = throw UnsupportedOperationException()
    override fun setJavaHome(javaHome: File?) = throw UnsupportedOperationException()
    override fun withSystemProperties(systemProperties: Map<String, String>) = throw UnsupportedOperationException()
    override fun withArguments(vararg arguments: String) = throw UnsupportedOperationException()
    override fun withArguments(arguments: Iterable<String>?) = throw UnsupportedOperationException()
    override fun addArguments(vararg arguments: String) = throw UnsupportedOperationException()
    override fun addArguments(arguments: Iterable<String>) = throw UnsupportedOperationException()
    override fun setEnvironmentVariables(envVariables: Map<String, String>?) = throw UnsupportedOperationException()
    override fun addProgressListener(listener: org.gradle.tooling.ProgressListener) = throw UnsupportedOperationException()
    override fun addProgressListener(listener: ProgressListener) = throw UnsupportedOperationException()
    override fun addProgressListener(listener: ProgressListener, operationTypes: Set<OperationType>) = throw UnsupportedOperationException()
    override fun addProgressListener(listener: ProgressListener, vararg operationTypes: OperationType) = throw UnsupportedOperationException()
    override fun withCancellationToken(cancellationToken: CancellationToken) = throw UnsupportedOperationException()
  }
}
