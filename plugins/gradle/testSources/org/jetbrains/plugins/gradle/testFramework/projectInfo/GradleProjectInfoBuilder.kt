// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectInfo

import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl

interface GradleProjectInfoBuilder: GradleModuleInfoBuilder {

  val projectName: String

  fun compositeInfo(
    name: String,
    relativePath: String,
    gradleDsl: GradleDsl? = null,
    configure: GradleProjectInfoBuilder.() -> Unit = {},
  )

  fun moduleInfo(
    ideName: String,
    relativePath: String,
    gradleDsl: GradleDsl? = null,
    configure: GradleModuleInfoBuilder.() -> Unit = {},
  )

  fun rootModuleInfo(configure: GradleModuleInfoBuilder.() -> Unit)
}