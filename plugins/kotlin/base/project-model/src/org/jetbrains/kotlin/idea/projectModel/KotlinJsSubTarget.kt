// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

interface KotlinJsSubTarget : Serializable {
    val name: String
    val testRuns: List<String>
}

interface KotlinJsBrowserSubTarget : KotlinJsSubTarget {
    val test: KotlinBrowserTestExtensions
}
