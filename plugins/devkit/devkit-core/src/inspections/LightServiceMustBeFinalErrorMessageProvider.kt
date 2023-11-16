// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle

private val EP_NAME: ExtensionPointName<ErrorMessageProvider> =
  ExtensionPointName.create("DevKit.lang.lightServiceMustBeFinalErrorMessageProvider")

internal object LightServiceMustBeFinalErrorMessageProviders : LanguageExtension<ErrorMessageProvider>(EP_NAME.name)

internal class LightServiceMustBeFinalErrorMessageProvider : ErrorMessageProvider {
  override fun provideErrorMessage(): @Nls String {
    return DevKitBundle.message("inspection.light.service.must.be.final.message")
  }

  override fun isApplicableForKotlin(): Boolean {
    return false
  }
}
