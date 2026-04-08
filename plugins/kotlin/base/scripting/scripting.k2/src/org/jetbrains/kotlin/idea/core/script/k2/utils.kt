// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.lang.Xxh3
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationData
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationId
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptEvaluationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptingHostConfigurationEntity
import org.jetbrains.kotlin.scripting.definitions.isNonScript
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.FileScriptSource
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
 * @return The entity ID (existing if content matches, newly created otherwise)
 * @throws IllegalStateException if all tags (0..[Short.MAX_VALUE]) are exhausted for the given hash
 *         (extremely unlikely in practice)
 */
fun ScriptCompilationConfiguration.getOrCreateScriptConfigurationId(
    storage: MutableEntityStorage,
    entitySource: EntitySource
): ScriptCompilationConfigurationId {
    val data = asBytes()
    val hash = Xxh3.hash(data)

    for (tagInt in Byte.MIN_VALUE..Byte.MAX_VALUE) {
        val tag = tagInt.toByte()
        val configurationId = ScriptCompilationConfigurationId(hash = hash, tag = tag)
        val existingEntity = storage.resolve(configurationId)
        if (existingEntity == null) {
            storage addEntity ScriptCompilationConfigurationEntity(data, configurationId, entitySource)
            return configurationId
        } else {
            val existingBytes = existingEntity.data
            if (existingBytes.contentEquals(data)) {
                return configurationId
            }
        }
    }

    errorWithAttachment("Exhausted tags for script compilation configuration") {
        notTransientData.forEach { (key, value) ->
            withEntry(key.name, value?.toString())
        }
    }
}

@Suppress("IO_FILE_USAGE")
fun getVirtualFile(scriptSourceCode: SourceCode): VirtualFile? = when (scriptSourceCode) {
    is VirtualFileScriptSource -> scriptSourceCode.virtualFile
    is FileScriptSource -> VfsUtil.findFileByIoFile(scriptSourceCode.file, false)
    else -> null
}?.takeIf { !it.isNonScript() }