// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import java.io.File
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

@K1Deprecation
class MainKtsScriptDefinitionProvider : ScriptDefinitionsProvider {
    override val id: String = ".main.kts script"

    override fun getDefinitionClasses(): Iterable<String> = emptyList()

    override fun getDefinitionsClassPath(): Iterable<File> {
        return listOf(
          KotlinArtifacts.kotlinMainKts,
          KotlinArtifacts.kotlinScriptRuntime,
          KotlinArtifacts.kotlinStdlib,
          KotlinArtifacts.kotlinReflect
        )
    }

    override fun useDiscovery(): Boolean = true
}