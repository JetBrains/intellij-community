// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import org.jetbrains.annotations.ApiStatus

/**
 * Annotation is used for single UI DSL demo
 *
 * @param title tab name in the demo
 * @param description description that is shown above the demo
 * @param scrollbar true if the demo should be wrapped into scrollbar pane
 */
@ApiStatus.Internal
@Target(AnnotationTarget.FUNCTION)
internal annotation class Demo(val title: String, val description: String, val scrollbar: Boolean = false)
