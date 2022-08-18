// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.gradle.kpm.idea.IdeaKpmProject
import org.jetbrains.kotlin.gradle.kpm.idea.serialize.IdeaKpmExtrasSerializationExtension
import org.jetbrains.kotlin.gradle.kpm.idea.serialize.IdeaKpmExtrasSerializer
import org.jetbrains.kotlin.gradle.kpm.idea.serialize.IdeaKpmSerializationContext
import org.jetbrains.kotlin.gradle.kpm.idea.serialize.IdeaKpmSerializationLogger
import org.jetbrains.kotlin.gradle.kpm.idea.serialize.IdeaKpmSerializationLogger.Severity.ERROR
import org.jetbrains.kotlin.gradle.kpm.idea.serialize.IdeaKpmSerializationLogger.Severity.WARNING
import org.jetbrains.kotlin.idea.gradle.configuration.kpm.ExtrasSerializationService
import org.jetbrains.kotlin.idea.gradleTooling.serialization.IdeaKpmProjectDeserializer
import org.jetbrains.kotlin.kpm.idea.proto.IdeaKpmProject
import org.jetbrains.kotlin.tooling.core.Extras


class IdeaKpmProjectDeserializerImpl : IdeaKpmProjectDeserializer {
    override fun read(data: ByteArray): IdeaKpmProject? {
        return IdeaKpmSerializationContextImpl().IdeaKpmProject(data)
    }
}

private class IdeaKpmSerializationContextImpl : IdeaKpmSerializationContext {
    override val logger: IdeaKpmSerializationLogger = IntellijIdeaKpmSerializationLogger

    override val extrasSerializationExtension: IdeaKpmExtrasSerializationExtension = IdeaKpmCompositeExtrasSerializationExtension(
        ExtrasSerializationService.EP_NAME.extensionList.map { it.extension }
    )
}

private object IntellijIdeaKpmSerializationLogger : IdeaKpmSerializationLogger {
    val logger = Logger.getInstance(IntellijIdeaKpmSerializationLogger::class.java)

    override fun report(severity: IdeaKpmSerializationLogger.Severity, message: String, cause: Throwable?) {
        when (severity) {
            WARNING -> logger.warn(message, cause)
            ERROR -> logger.error(message, cause)
            else -> logger.warn(message, cause)
        }
    }
}

private class IdeaKpmCompositeExtrasSerializationExtension(
    private val extensions: List<IdeaKpmExtrasSerializationExtension>
) : IdeaKpmExtrasSerializationExtension {

    override fun <T : Any> serializer(key: Extras.Key<T>): IdeaKpmExtrasSerializer<T>? {
        val serializers = extensions.mapNotNull { it.serializer(key) }
        if (serializers.isEmpty()) return null
        if (serializers.size == 1) return serializers.single()

        IntellijIdeaKpmSerializationLogger.error(
            "Conflicting serializers for $key: $serializers"
        )

        return null
    }
}
