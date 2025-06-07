// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.build.BuildView
import com.intellij.execution.runners.ExecutionEnvironment

val ExecutionEnvironment.buildView: BuildView
  get() = contentToReuse!!.executionConsole!! as BuildView