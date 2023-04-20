// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.missingApi

import com.intellij.codeInspection.options.StringValidator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.idea.devkit.DevKitBundle

class BuildNumberValidator: StringValidator {
  override fun validatorId(): String = "devkit.buildNumber"

  override fun getErrorMessage(project: Project?, buildNumber: String): String? {
    return if (buildNumber.isNotBlank() && BuildNumber.fromStringOrNull(buildNumber) == null) {
      DevKitBundle.message("inspections.missing.recent.api.settings.invalid.build.number", buildNumber)
    } else null
  }
}