// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.roots

import org.jetbrains.kotlin.gradle.scripting.shared.importing.KotlinDslScriptModel


data class GradleBuildRootData(
    val importTs: Long,
    val projectRoots: Collection<String>,
    val gradleHome: String,
    val javaHome: String?,
    val models: Collection<KotlinDslScriptModel>
)