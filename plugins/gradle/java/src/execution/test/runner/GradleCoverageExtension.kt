// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.coverage.JavaCoverageEngineExtension
import com.intellij.execution.configurations.RunConfigurationBase
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

/**
 * Created by Nikita.Skvortsov
 * date: 23.08.2017.
 */

class GradleCoverageExtension: JavaCoverageEngineExtension() {
  override fun isApplicableTo(conf: RunConfigurationBase<*>?) = conf is GradleRunConfiguration &&
                                                                !conf.isCoverageDisabled

}