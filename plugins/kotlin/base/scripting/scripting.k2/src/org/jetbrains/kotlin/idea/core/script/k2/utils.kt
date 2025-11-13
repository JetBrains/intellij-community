// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.platform.backend.workspace.virtualFile
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptEvaluationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptingHostConfigurationEntity
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.script.experimental.util.PropertiesCollection.Key

fun KotlinScriptEntity.toConfigurationResult(): ScriptCompilationConfigurationResult? {
    val virtualFile = virtualFileUrl.virtualFile ?: return null

    val result = if (configuration == null) {
        ResultWithDiagnostics.Failure(listOf())
    } else {
        ResultWithDiagnostics.Success<ScriptCompilationConfigurationWrapper>(
            ScriptCompilationConfigurationWrapper.FromCompilationConfiguration(
                VirtualFileScriptSource(virtualFile), configuration?.deserialize()
            ), reports.map { report -> report.toScriptDiagnostic() })
    }

    return result
}

fun ScriptCompilationConfiguration.asEntity(): ScriptCompilationConfigurationEntity = ScriptCompilationConfigurationEntity(this.serialize())

fun ScriptCompilationConfigurationEntity.deserialize(): ScriptCompilationConfiguration? {
    val params = ByteArrayInputStream(data).use { bis ->
        ObjectInputStream(bis).use { ois ->
            ois.readObject() as? LinkedHashMap<Key<Any>, Any?>
        }
    } ?: return null

    return ScriptCompilationConfiguration {
        params.forEach { (key, any) ->
            key.putIfNotNull(any)
        }
    }
}

private fun PropertiesCollection.serialize(): ByteArray = ByteArrayOutputStream().use { bos ->
    ObjectOutputStream(bos).use { oos ->
        oos.writeObject(notTransientData)
    }

    bos.toByteArray()
}

fun ScriptEvaluationConfiguration.asEntity() = ScriptEvaluationConfigurationEntity(this.serialize())

fun ScriptEvaluationConfigurationEntity.deserialize(): ScriptEvaluationConfiguration? {
    val params = ByteArrayInputStream(data).use { bis ->
        ObjectInputStream(bis).use { ois ->
            ois.readObject() as? LinkedHashMap<Key<Any>, Any?>
        }
    } ?: return null

    return ScriptEvaluationConfiguration {
        params.forEach { (key, any) ->
            key.putIfNotNull(any)
        }
    }
}

fun ScriptingHostConfiguration.asEntity() = ScriptingHostConfigurationEntity(this.serialize())

fun ScriptingHostConfigurationEntity.deserialize(): ScriptingHostConfiguration? {
    val params = ByteArrayInputStream(data).use { bis ->
        ObjectInputStream(bis).use { ois ->
            ois.readObject() as? LinkedHashMap<Key<Any>, Any?>
        }
    } ?: return null

    return ScriptingHostConfiguration {
        params.forEach { (key, any) ->
            key.putIfNotNull(any)
        }
    }
}