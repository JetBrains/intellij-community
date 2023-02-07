// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.workspace

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.gradle.newTests.TestConfiguration
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.utils.Printer
import java.io.File

data class PrinterContext(
  val printer: Printer,
  val project: Project,
  val projectRoot: File,
  val testConfiguration: TestConfiguration,
  val kotlinGradlePluginVersion: KotlinToolingVersion,
)
