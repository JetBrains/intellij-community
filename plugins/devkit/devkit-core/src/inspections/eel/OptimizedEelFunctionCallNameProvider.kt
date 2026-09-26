// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.eel

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

private val EP_NAME = ExtensionPointName.create<OptimizedEelFunctionCallNameProvider>(
  "DevKit.lang.optimizedEelFunctionCallNameProvider"
)

@ApiStatus.Internal
object OptimizedEelFunctionCallNameProviders : LanguageExtension<OptimizedEelFunctionCallNameProvider>(EP_NAME.name)

@ApiStatus.Internal
interface OptimizedEelFunctionCallNameProvider {
  fun getAliases(file: PsiFile, methodNames: Set<String>): Set<String>
}
