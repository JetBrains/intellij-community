// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.util

import com.intellij.build.BuildView
import com.intellij.execution.runners.ExecutionEnvironment

val ExecutionEnvironment.buildView: BuildView
  get() = contentToReuse!!.executionConsole!! as BuildView