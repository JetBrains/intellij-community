// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import org.jetbrains.idea.devkit.inspections.ErrorMessageProvider
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle

internal class ExtensionClassShouldNotBeOpenErrorMessageProvider : ErrorMessageProvider {
  override fun provideErrorMessage(): String {
    return DevKitKotlinBundle.message("inspection.extension.class.should.not.be.open.text")
  }

  override fun isApplicableForKotlin(): Boolean {
    return true
  }
}