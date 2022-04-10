// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util

/**
 * Indicates that the annotated declaration and all of its child declarations must not be considered as public API. It is supposed to be used
 * only for IntelliJ platform and plugins which sources are located in intellij Git repository.
 * Such declarations may be renamed, changed or removed in any future release without prior notice.
 *
 * The annotation should be used together with [org.jetbrains.annotations.ApiStatus.Internal] to ensure that usages of the declaration are
 * also reported by the Plugin Verifier, and highlighted in Java code.
 */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.TYPEALIAS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.FIELD,
  AnnotationTarget.CONSTRUCTOR
)
annotation class IntellijInternalApi