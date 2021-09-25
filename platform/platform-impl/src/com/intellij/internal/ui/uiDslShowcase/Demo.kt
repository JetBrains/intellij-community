// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

@Target(AnnotationTarget.FUNCTION)
annotation class Demo(val title: String, val description: String, val scrollbar: Boolean = false)
