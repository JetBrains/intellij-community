// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import java.util.function.Consumer


fun GradleImportingTestCase.buildscript(configure: Consumer<GradleBuildScriptBuilder>) =
  GradleBuildScriptBuilder.buildscript(currentGradleVersion, configure)

fun GradleImportingTestCase.buildscript(configure: GradleBuildScriptBuilder.() -> Unit) =
  GradleBuildScriptBuilder.buildscript(currentGradleVersion, configure)
