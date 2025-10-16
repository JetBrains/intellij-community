// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel

import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptEvaluationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptingHostConfigurationEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.script.experimental.util.PropertiesCollection.Key

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