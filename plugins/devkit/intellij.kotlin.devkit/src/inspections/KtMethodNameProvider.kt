// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import org.jetbrains.idea.devkit.inspections.MethodNameProvider
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UMethod

internal class KtMethodNameProvider : MethodNameProvider {
  override fun getName(method: UMethod): String? {
    return (method.sourcePsi as? KtNamedFunction)?.name
  }
}