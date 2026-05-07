// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

/**
 * Annotation is used for single UI DSL demo
 *
 * @param title tab name in the demo
 * @param description description that is shown above the demo
 * @param scrollbar true if the demo should be wrapped into scrollbar pane
 */
@Target(AnnotationTarget.FUNCTION)
internal annotation class Demo(val title: String, val description: String, val scrollbar: Boolean = false)
