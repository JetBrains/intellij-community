// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import org.jetbrains.annotations.PropertyKey

/**
 * Annotation is used for a single UI DSL demo
 *
 * @param title tab name in the demo
 * @param description description that is shown above the demo
 * @param scrollbar true if the demo should be wrapped into a scrollbar pane
 */
@Target(AnnotationTarget.FUNCTION)
internal annotation class Demo(
  @PropertyKey(resourceBundle = "messages.DevkitUiDslBundle") val title: String,
  @PropertyKey(resourceBundle = "messages.DevkitUiDslBundle") val description: String,
  val scrollbar: Boolean = false,
)
