// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.util.daemonClassLoaderModel

import org.gradle.internal.build.BuildState
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class GradleDaemonClassLoaderModelBuilder : BuildScopeModelBuilder {
  override fun canBuild(modelName: String): Boolean =
    GradleDaemonClassLoaderModel::class.java.name == modelName

  override fun create(target: BuildState): GradleDaemonClassLoaderModel =
    GradleDaemonClassLoaderModelImpl()
}
