// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

data class GradleDaemonJvmCriteria(
  val version: String,
  val vendor: String?
)
