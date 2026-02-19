// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.coverage

import com.intellij.coverage.JavaCoverageEngineExtension
import com.intellij.execution.configurations.RunConfigurationBase
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

class GradleCoverageExtension: JavaCoverageEngineExtension() {
  override fun isApplicableTo(conf: RunConfigurationBase<*>?): Boolean = conf is GradleRunConfiguration &&
                                                                                !conf.isCoverageDisabled

}