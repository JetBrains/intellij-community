// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting.roots

import org.jetbrains.kotlin.idea.gradleJava.scripting.importing.KotlinDslScriptModel

data class GradleBuildRootData(
    val importTs: Long,
    val projectRoots: Collection<String>,
    val gradleHome: String,
    val javaHome: String?,
    val models: Collection<KotlinDslScriptModel>
)