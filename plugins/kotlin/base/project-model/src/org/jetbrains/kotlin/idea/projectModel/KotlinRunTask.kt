// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

interface KotlinRunTask : Serializable {
    val taskName: String
    val compilationName: String
}

interface KotlinTestRunTask : KotlinRunTask

interface KotlinNativeMainRunTask : KotlinRunTask {
    val entryPoint: String
    val debuggable: Boolean
}