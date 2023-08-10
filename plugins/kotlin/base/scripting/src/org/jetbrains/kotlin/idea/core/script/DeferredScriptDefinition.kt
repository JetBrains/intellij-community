// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.system.measureTimeMillis


private const val ATTEMPTS_REGISTRY_KEY = "kotlin.scripting.deferred.definition.attempts.num"

internal class DeferredScriptDefinition(
    val scriptCode: SourceCode,
    private val definitionsProvider: ScriptDefinitionsManager
) : ScriptDefinition() {

    private val definition : ScriptDefinition by lazy {
        val sleepMs = 100L
        var attemptsLeft = Registry.intValue(ATTEMPTS_REGISTRY_KEY, 20)
        var deferredDefinition: ScriptDefinition? = tryGetDefinition()

        val ms = measureTimeMillis {
            while (deferredDefinition == null && attemptsLeft > 0) {
                attemptsLeft--
                Thread.sleep(sleepMs)
                ProgressManager.checkCanceled()
                deferredDefinition = tryGetDefinition()
            }
        }

        deferredDefinition.takeIf { it != null }
            ?: error("Deferred definition for ${scriptCode.locationId} wasn't loaded after $ms ms. " +
                             "See registry key $ATTEMPTS_REGISTRY_KEY.")
    }

    private fun tryGetDefinition(): ScriptDefinition? {
        val deferredDef = this
        return with (definitionsProvider) { deferredDef.valueIfAny() }
    }


    override val annotationsForSamWithReceivers: List<String>
        get() = definition.annotationsForSamWithReceivers
    override val baseClassType: KotlinType
        get() = definition.baseClassType
    override val compilationConfiguration: ScriptCompilationConfiguration
        get() = definition.compilationConfiguration
    override val compilerOptions: Iterable<String>
        get() = definition.compilerOptions
    override val contextClassLoader: ClassLoader?
        get() = definition.contextClassLoader
    override val definitionId: String
        get() = definition.definitionId
    override val evaluationConfiguration: ScriptEvaluationConfiguration?
        get() = definition.evaluationConfiguration
    override val fileExtension: String
        get() = definition.fileExtension
    override val hostConfiguration: ScriptingHostConfiguration
        get() = definition.hostConfiguration
    override val legacyDefinition: KotlinScriptDefinition
        get() = definition.legacyDefinition
    override val name: String
        get() = definition.name

    override fun isScript(script: SourceCode): Boolean {
        return definition.isScript(script)
    }
}