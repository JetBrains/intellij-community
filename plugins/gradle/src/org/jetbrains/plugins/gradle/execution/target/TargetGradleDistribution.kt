// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.value.TargetValue
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener

internal class TargetGradleDistribution(distribution: Distribution,
                                        val gradleHome: TargetValue<String>? = null) : Distribution by distribution {
  override fun getToolingImplementationClasspath(progressLoggerFactory: ProgressLoggerFactory?,
                                                 progressListener: InternalBuildProgressListener?,
                                                 parameters: ConnectionParameters?,
                                                 cancellationToken: BuildCancellationToken?): ClassPath {
    throw IllegalStateException("Target Gradle distribution should not be resolved on host environment.")
  }
}