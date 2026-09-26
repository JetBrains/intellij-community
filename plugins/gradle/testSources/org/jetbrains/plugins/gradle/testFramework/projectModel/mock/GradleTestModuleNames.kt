// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectModel.mock

object GradleTestModuleNames {

  fun sourceSetName(sourceSetModuleIndex: Int): String =
    "source-set-$sourceSetModuleIndex"

  fun holderModuleName(holderModuleIndex: Int): String =
    "module-$holderModuleIndex"

  fun sourceSetModuleName(holderModuleIndex: Int, sourceSetModuleIndex: Int): String =
    holderModuleName(holderModuleIndex) + "." + sourceSetName(sourceSetModuleIndex)

  fun holderModuleId(holderModuleIndex: Int): String =
    ":" + holderModuleName(holderModuleIndex)

  fun sourceSetModuleId(holderModuleIndex: Int, sourceSetModuleIndex: Int): String =
    holderModuleId(holderModuleIndex) + ":" + sourceSetName(sourceSetModuleIndex)
}