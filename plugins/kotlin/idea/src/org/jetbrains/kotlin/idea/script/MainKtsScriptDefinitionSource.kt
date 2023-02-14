// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.script

import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import java.io.File
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

class MainKtsScriptDefinitionSource : ScriptDefinitionsProvider {
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