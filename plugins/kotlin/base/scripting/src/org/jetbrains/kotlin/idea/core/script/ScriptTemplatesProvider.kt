// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("PackageDirectoryMismatch")
package org.jetbrains.kotlin.script

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.io.File
import kotlin.script.experimental.dependencies.DependenciesResolver

@ApiStatus.ScheduledForRemoval
@Deprecated("Use ScriptDefinitionContributor EP and loadDefinitionsFromTemplates top level function")
internal interface ScriptTemplatesProvider {

    // for resolving ambiguities
    val id: String

    @Deprecated("Parameter isn't used for resolving priorities anymore. " +
                "com.intellij.openapi.extensions.LoadingOrder constants can be used to order providers when registered from Intellij plugin.",
                ReplaceWith("0"))
    val version: Int
        get() = 0

    val isValid: Boolean

    val templateClassNames: Iterable<String>

    val resolver: DependenciesResolver? get() = null

    val filePattern: String? get() = null

    val templateClasspath: List<File>

    // TODO: need to provide a way to specify this in compiler/repl .. etc
    /*
     * Allows to specify additional jars needed for DependenciesResolver (and not script template).
     * Script template dependencies naturally become (part of) dependencies of the script which is not always desired for resolver dependencies.
     * i.e. gradle resolver may depend on some jars that 'built.gradle.kts' files should not depend on.
     */
    val additionalResolverClasspath: List<File> get() = emptyList()

    val environment: Map<String, Any?>?

    companion object {
        val EP_NAME: ExtensionPointName<ScriptTemplatesProvider> =
                ExtensionPointName.create("org.jetbrains.kotlin.scriptTemplatesProvider")
    }
}

