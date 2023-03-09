// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.serialize

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.gradle.idea.serialize.*
import org.jetbrains.kotlin.idea.gradle.configuration.serialize.KotlinExtrasSerializationService
import org.jetbrains.kotlin.tooling.core.Extras

class IntellijIdeaKotlinSerializationContext : IdeaKotlinSerializationContext {
    override val logger: IdeaKotlinSerializationLogger = IntellijIdeaSerializationLogger

    override val extrasSerializationExtension: IdeaKotlinExtrasSerializationExtension = IdeaCompositeExtrasSerializationExtension(
      KotlinExtrasSerializationService.EP_NAME.extensionList.map { it.extension }
    )
}

private object IntellijIdeaSerializationLogger : IdeaKotlinSerializationLogger {
    val logger = Logger.getInstance(IntellijIdeaSerializationLogger::class.java)

    override fun report(severity: IdeaKotlinSerializationLogger.Severity, message: String, cause: Throwable?) {
        when (severity) {
            IdeaKotlinSerializationLogger.Severity.WARNING -> logger.warn(message, cause)
            IdeaKotlinSerializationLogger.Severity.ERROR -> logger.error(message, cause)
            else -> logger.warn(message, cause)
        }
    }
}

private class IdeaCompositeExtrasSerializationExtension(
    private val extensions: List<IdeaKotlinExtrasSerializationExtension>
) : IdeaKotlinExtrasSerializationExtension {

    override fun <T : Any> serializer(key: Extras.Key<T>): IdeaKotlinExtrasSerializer<T>? {
        val serializers = extensions.mapNotNull { it.serializer(key) }
        if (serializers.isEmpty()) return null
        if (serializers.size == 1) return serializers.single()

        IntellijIdeaSerializationLogger.error(
            "Conflicting serializers for $key: $serializers"
        )

        return null
    }
}
