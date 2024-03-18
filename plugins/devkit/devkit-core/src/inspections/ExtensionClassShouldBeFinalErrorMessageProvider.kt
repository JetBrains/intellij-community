// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle

private val EP_NAME: ExtensionPointName<ErrorMessageProvider> =
  ExtensionPointName.create("DevKit.lang.extensionClassShouldBeFinalErrorMessageProvider")

internal object ExtensionClassShouldBeFinalErrorMessageProviders : LanguageExtension<ErrorMessageProvider>(EP_NAME.name)

interface ErrorMessageProvider : JvmProvider {
  fun provideErrorMessage(): @Nls String
}

internal class ExtensionClassShouldBeFinalErrorMessageProvider : ErrorMessageProvider {
  override fun provideErrorMessage(): @Nls String {
    return DevKitBundle.message("inspection.extension.class.should.be.final.text")
  }

  override fun isApplicableForKotlin(): Boolean {
    return false
  }
}
