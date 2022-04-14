// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

internal interface GradleTestLocationCustomizer {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<GradleTestLocationCustomizer> = ExtensionPointName.create("org.jetbrains.plugins.gradle.testLocationCustomizer")
  }

  fun getLocationUrl(parent: SMTestProxy?, name: String?, displayName: String?,
                     fqClassName: String, project: Project, locationUrlFactory: (String?, String) -> String): String?
}