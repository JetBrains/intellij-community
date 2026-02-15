// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.lang.Xxh3
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationData
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationIdentity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptEvaluationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptingHostConfigurationEntity
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.script.experimental.util.PropertiesCollection.Key

fun ScriptCompilationConfiguration.asEntity(): ScriptCompilationConfigurationData = ScriptCompilationConfigurationData(this.asBytes())

fun ScriptCompilationConfigurationData.deserialize(): ScriptCompilationConfiguration? {
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

fun ByteArray.asCompilationConfiguration(): ScriptCompilationConfiguration? {
    val params = ByteArrayInputStream(this).use { bis ->
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

fun PropertiesCollection.asBytes(): ByteArray = ByteArrayOutputStream().use { bos ->
    ObjectOutputStream(bos).use { oos ->
        oos.writeObject(notTransientData)
    }

    bos.toByteArray()
}

fun ScriptEvaluationConfiguration.asEntity() = ScriptEvaluationConfigurationEntity(this.asBytes())

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

fun ScriptingHostConfiguration.asEntity() = ScriptingHostConfigurationEntity(this.asBytes())

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

/**
 * Gets or creates a unique entity ID for a script compilation configuration using content-based deduplication.
 *
 * This function implements a deduplication system that:
 * 1. Serializes the configuration to bytes
 * 2. Computes an XXHash3 (64-bit) hash of the serialized data
 * 3. Uses the hash with a tag-based collision resolution strategy to find or create a unique entity
 *
 * If an entity with the same content already exists in storage, its ID is returned (deduplication).
 * Otherwise, a new entity is created with the next available tag for the given hash.
 *
 * **Hash Collision Resolution:**
 * - The function tries tags from 0 to [Short.MAX_VALUE] to handle hash collisions
 * - For each candidate ID (hash + tag), it checks if an entity exists
 * - If exists, performs byte-level comparison to distinguish true duplicates from hash collisions
 * - If content matches, returns existing ID; if different (collision), tries next tag
 *
 * @param configuration The script compilation configuration to store in workspace model
 * @param entitySource The entity source for tracking changes in the workspace model
 * @return The entity ID (existing if content matches, newly created otherwise)
 * @throws IllegalStateException if all tags (0..[Short.MAX_VALUE]) are exhausted for the given hash
 *         (extremely unlikely in practice)
 */
fun MutableEntityStorage.getOrCreateScriptConfigurationIdentity(
    configuration: ScriptCompilationConfiguration,
    entitySource: EntitySource
): ScriptCompilationConfigurationIdentity {
    val data = configuration.asBytes()
    val hash = Xxh3.hash(data)

    for (tagInt in 0..<Short.MAX_VALUE) {
        val tag = tagInt.toByte()
        val identity = ScriptCompilationConfigurationIdentity(hash = hash, tag = tag)
        val existingEntity = this.resolve(identity)
        if (existingEntity == null) {
            this addEntity ScriptCompilationConfigurationEntity(data, identity, entitySource)
            return identity
        } else {
            val existingBytes = existingEntity.data
            if (existingBytes.contentEquals(data)) {
                return identity
            }
        }
    }

    errorWithAttachment("Exhausted tags for configuration=$configuration")
}