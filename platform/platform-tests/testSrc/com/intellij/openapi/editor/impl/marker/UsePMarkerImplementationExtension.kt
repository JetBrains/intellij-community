// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.impl.RangeMarkerStorageImpl
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method

@TestOnly
class UsePMarkerImplementationExtension : InvocationInterceptor {
  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext?,
  ) {
    val annotation = invocationContext.executable.getAnnotation(UsePMarkerImplementation::class.java)
                     ?: invocationContext.targetClass.getAnnotation(UsePMarkerImplementation::class.java)
                     ?: error("The range marker implementation annotation is missing")
    RangeMarkerStorageImpl.usePMarkerImplementationIn<RuntimeException>(annotation.usePMarkerImplementation) {
      super.interceptTestMethod(invocation, invocationContext, extensionContext)
    }
  }
}

/** Runs an annotated JUnit 5 test with the selected range marker implementation. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@ExtendWith(
  UsePMarkerImplementationExtension::class
)
annotation class UsePMarkerImplementation(val usePMarkerImplementation: Boolean = true)
