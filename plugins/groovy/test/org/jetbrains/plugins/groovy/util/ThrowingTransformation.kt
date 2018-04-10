/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.util

import com.intellij.openapi.Disposable
import com.intellij.testFramework.PlatformTestUtil.registerExtension
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext

object ThrowingTransformation : AstTransformationSupport {

  @JvmStatic
  fun disableTransformations(parentDisposable: Disposable) {
    registerExtension(AstTransformationSupport.EP_NAME, this, parentDisposable)
  }

  override fun applyTransformation(context: TransformationContext): Nothing {
    throw UnsupportedOperationException("Transformation requested for ${context.codeClass.name}")
  }
}
