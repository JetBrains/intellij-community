// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.util

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext

object ThrowingTransformation : AstTransformationSupport {
  @JvmStatic
  fun disableTransformations(parentDisposable: Disposable) {
    AstTransformationSupport.EP_NAME.point.registerExtension(this, parentDisposable)
  }

  override fun applyTransformation(context: TransformationContext): Nothing {
    throw UnsupportedOperationException("Transformation requested for ${context.codeClass.name}")
  }
}
