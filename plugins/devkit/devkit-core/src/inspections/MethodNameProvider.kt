// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UMethod

private val EP_NAME = ExtensionPointName.create<MethodNameProvider>("DevKit.lang.methodNameProvider")

internal object MethodNameProviders : LanguageExtension<MethodNameProvider>(EP_NAME.name)

@IntellijInternalApi
@ApiStatus.Internal
interface MethodNameProvider {
  fun getName(method: UMethod): String?
}