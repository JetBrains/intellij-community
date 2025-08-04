// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel

import com.google.gson.*
import org.jetbrains.kotlin.cli.common.arguments.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.platform.impl.FakeK2NativeCompilerArguments
import java.lang.reflect.Type

class ObservableCompilerSettings(val updateEntity: (CompilerSettings) -> Unit) : CompilerSettings() {
    override var additionalArguments: String = DEFAULT_ADDITIONAL_ARGUMENTS
        set(value) {
            checkFrozen()
            field = value
            updateEntity(this)
        }

    override var scriptTemplates: String = ""
        set(value) {
            checkFrozen()
            field = value
            updateEntity(this)
        }
    override var scriptTemplatesClasspath: String = ""
        set(value) {
            checkFrozen()
            field = value
            updateEntity(this)
        }
    override var copyJsLibraryFiles = true
        set(value) {
            checkFrozen()
            field = value
            updateEntity(this)
        }
    override var outputDirectoryForJsLibraryFiles: String = DEFAULT_OUTPUT_DIRECTORY
        set(value) {
            checkFrozen()
            field = value
            updateEntity(this)
        }

    override fun copyOf(): Freezable = copyCompilerSettings(this, ObservableCompilerSettings(updateEntity))
}

fun CompilerSettingsData.toCompilerSettings(notifier: (CompilerSettings) -> Unit): CompilerSettings =
    ObservableCompilerSettings(notifier).also {
        it.additionalArguments = this.additionalArguments
        it.scriptTemplates = this.scriptTemplates
        it.scriptTemplatesClasspath = this.scriptTemplatesClasspath
        it.copyJsLibraryFiles = this.copyJsLibraryFiles
        it.outputDirectoryForJsLibraryFiles = this.outputDirectoryForJsLibraryFiles
    }

fun CompilerSettings?.toCompilerSettingsData(): CompilerSettingsData? = this?.let {
    CompilerSettingsData(
        additionalArguments,
        scriptTemplates,
        scriptTemplatesClasspath,
        copyJsLibraryFiles,
        outputDirectoryForJsLibraryFiles
    )
}

fun ExternalSystemRunTask.serializeExternalSystemTestRunTask(): String {
    return when(this) {
        is ExternalSystemTestRunTask -> "ExternalSystemTestRunTask" + this.toStringRepresentation()
        is ExternalSystemNativeMainRunTask -> "ExternalSystemNativeMainRunTask" + this.toStringRepresentation()
        else -> error("Unsupported task type: ${this::class.java.simpleName}.")
    }
}

fun deserializeExternalSystemTestRunTask(deserializedTask: String): ExternalSystemRunTask {
    return when {
        deserializedTask.startsWith("ExternalSystemTestRunTask") ->
            deserializedTask.removePrefix("ExternalSystemTestRunTask").let {
                ExternalSystemTestRunTask.fromStringRepresentation(it)
            }
        deserializedTask.startsWith("ExternalSystemNativeMainRunTask") ->
            deserializedTask.removePrefix("ExternalSystemNativeMainRunTask").let {
                ExternalSystemNativeMainRunTask.fromStringRepresentation(it)
            }

        else -> error("Unsupported task type in provided string.")
    } ?: error("Task deserialization failed. Check that class type is present in `deserializeExternalSystemTestRunTask`")
}

object CompilerArgumentsSerializer {
    private val gson = GsonBuilder()
        .registerTypeAdapter(InternalArgument::class.java, InternalArgumentSerializer)
        .create()

    private val argumentsTypeMap = mapOf(
        "J" to K2JVMCompilerArguments::class.java,
        "S" to K2JSCompilerArguments::class.java,
        "M" to K2MetadataCompilerArguments::class.java,
        "N" to K2NativeCompilerArguments::class.java,
        "F" to FakeK2NativeCompilerArguments::class.java,
        "D" to CommonCompilerArguments.DummyImpl::class.java
    )

    fun serializeToString(commonCompilerArguments: CommonCompilerArguments?): String? = commonCompilerArguments?.let {
        val classIdentifier = argumentsTypeMap.entries.firstOrNull { it.value == commonCompilerArguments.javaClass }?.key
            ?: error("Class not found: ${commonCompilerArguments.javaClass}")

        classIdentifier + gson.toJson(commonCompilerArguments)
    }

    fun deserializeFromString(serializedArguments: String?): CommonCompilerArguments? = serializedArguments?.let {
        when {
            serializedArguments.isEmpty() -> null
            serializedArguments.isNotBlank() && serializedArguments[0].isLetter() -> {
                val classIdentifier = serializedArguments.substring(0, 1)
                val classType = argumentsTypeMap[classIdentifier]
                    ?: error("Class identifier not found: $classIdentifier")

                gson.fromJson(serializedArguments.substring(1), classType)
            }

            else -> error("Invalid serialization format: $serializedArguments")
        }
    }
}

private object InternalArgumentSerializer : JsonSerializer<InternalArgument>, JsonDeserializer<InternalArgument> {
    private const val TYPE = "type"

    private val argumentsTypeMap: Map<String, Class<out InternalArgument>> = mapOf(
        "M" to ManualLanguageFeatureSetting::class.java,
    )

    private val gson = Gson()

    override fun serialize(
        src: InternalArgument,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        val classIdentifier = argumentsTypeMap.entries.firstOrNull { it.value == src.javaClass }?.key
            ?: error("Class not found: ${src.javaClass}")
        val result = gson.toJsonTree(src).asJsonObject
        result.addProperty(TYPE, classIdentifier)
        return result
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): InternalArgument {
        val classIdentifier = json.asJsonObject[TYPE].asString
        val classType = argumentsTypeMap[classIdentifier] ?: error("Class identifier not found: $classIdentifier")
        return gson.fromJson(json, classType)
    }
}
