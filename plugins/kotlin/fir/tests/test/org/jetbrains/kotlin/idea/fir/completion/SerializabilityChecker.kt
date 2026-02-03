// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.idea.completion.api.serialization.lookup.LookupModelConverter
import org.jetbrains.kotlin.idea.completion.api.serialization.lookup.model.LookupElementModel
import org.jetbrains.kotlin.idea.completion.impl.k2.serializableInsertionHandlerSerializersModule
import kotlin.coroutines.cancellation.CancellationException

/**
 * Checks that the given [LookupElement] can be serialized and deserialized for the needs of Kotlin LSP
 *
 * @see [LookupModelConverter]
 */
object SerializabilityChecker {
    fun checkLookupElement(lookupElement: LookupElement, project: Project) {
        try {
            val serialized = LookupModelConverter.serializeLookupElementForInsertion(lookupElement, lookupModelConverterConfig) ?: return
            checkSerialization(serialized)

            LookupModelConverter.deserializeLookupElementForInsertion(serialized, project)
        } catch (e: Throwable) {
            when {
                e is CancellationException || e is ControlFlowException -> throw e
                e::class.qualifiedName == "com.intellij.psi.stubs.UpToDateStubIndexMismatch" -> {
                    // KTIJ-34849
                    LOG.warn(e)
                    return
                }

                else -> throw AssertionError(
                    "LookupElement ${lookupElement::class.java} is not serializable. See the Kdoc of ${LookupModelConverter::class.qualifiedName} for more details.",
                    e
                )
            }
        }
    }

    private val LOG = logger<SerializabilityChecker>()

    private fun checkSerialization(serialized: LookupElementModel) {
        val encodedAsJsonString = json.encodeToString(LookupElementModel.serializer(), serialized)
        json.decodeFromString(LookupElementModel.serializer(), encodedAsJsonString)
    }

    private val lookupModelConverterConfig = LookupModelConverter.Config(safeMode = false)

    private val json = Json {
        serializersModule = serializableInsertionHandlerSerializersModule
        prettyPrint = true
    }
}