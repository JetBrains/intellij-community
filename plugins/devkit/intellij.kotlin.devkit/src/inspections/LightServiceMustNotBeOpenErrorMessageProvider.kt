// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import org.jetbrains.idea.devkit.inspections.ErrorMessageProvider
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle

internal class LightServiceMustNotBeOpenErrorMessageProvider : ErrorMessageProvider {
  override fun provideErrorMessage(): String {
    return DevKitKotlinBundle.message("inspection.light.service.must.not.be.open.message")
  }

  override fun isApplicableForKotlin(): Boolean {
    return true
  }
}