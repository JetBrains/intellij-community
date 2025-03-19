// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@Suppress("MemberVisibilityCanBePrivate")
object EditorConfigRegistry {
  const val EDITORCONFIG_STOP_AT_PROJECT_ROOT_KEY = "editor.config.stop.at.project.root"
  const val EDITORCONFIG_BREADCRUMBS_SUPPORT_KEY = "editor.config.breadcrumbs.support"
  const val EDITORCONFIG_DOTNET_SUPPORT_KEY = "editor.config.csharp.support"
  const val EDITORCONFIG_RESHARPER_SUPPORT_KEY = "editor.config.resharper.support"

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Calling this C# is too narrow. Use EDITORCONFIG_DOTNET_SUPPORT_KEY instead",
              ReplaceWith("EDITORCONFIG_DOTNET_SUPPORT_KEY", "org.editorconfig.EditorConfigRegistry.EDITORCONFIG_DOTNET_SUPPORT_KEY"))
  const val EDITORCONFIG_CSHARP_SUPPORT_KEY = EDITORCONFIG_DOTNET_SUPPORT_KEY

  private var forceSkipProjectRootInTest = false

  @TestOnly
  @JvmStatic
  fun setSkipProjectRootInTest(skip : Boolean) {
    forceSkipProjectRootInTest = skip
  }

  @JvmStatic
  fun shouldStopAtProjectRoot() =
    Registry.`is`(EDITORCONFIG_STOP_AT_PROJECT_ROOT_KEY, false) or
      (ApplicationManager.getApplication().isUnitTestMode && !forceSkipProjectRootInTest)

  @JvmStatic
  fun shouldSupportBreadCrumbs() = Registry.`is`(EDITORCONFIG_BREADCRUMBS_SUPPORT_KEY, false)

  @JvmStatic
  fun shouldSupportDotNet() = Registry.`is`(EDITORCONFIG_DOTNET_SUPPORT_KEY, false)

  @JvmStatic
  fun shouldSupportReSharper() = Registry.`is`(EDITORCONFIG_RESHARPER_SUPPORT_KEY, false)
}
