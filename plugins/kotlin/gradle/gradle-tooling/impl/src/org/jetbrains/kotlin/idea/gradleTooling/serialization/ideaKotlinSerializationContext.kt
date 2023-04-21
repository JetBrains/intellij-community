// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.serialization

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinSerializationContext
import org.jetbrains.kotlin.idea.gradleTooling.loadClassOrNull
import org.jetbrains.kotlin.idea.gradleTooling.serialization.IdeaKotlinSerializationContextExtensionPoint.ResolutionResult

internal val ideaKotlinSerializationContext: IdeaKotlinSerializationContext by lazy {
    when (val resolutionResult = IdeaKotlinSerializationContextExtensionPoint.resolve()) {
        is ResolutionResult.ConflictingExtensions ->
            throw NoIdeaKotlinSerializationContextException("Conflicting extensions found: ${resolutionResult.extensions}")

        is ResolutionResult.ExtensionsUnavailable ->
            throw NoIdeaKotlinSerializationContextException("Extensions unavailable: Not running within IntelliJ?")

        is ResolutionResult.MissingExtension ->
            throw NoIdeaKotlinSerializationContextException("No ${IdeaKotlinSerializationContext::class.java.simpleName} registered")

        is ResolutionResult.Resolved -> resolutionResult.context
    }
}

internal val ideaKotlinSerializationContextOrNull: IdeaKotlinSerializationContext? by lazy {
    (IdeaKotlinSerializationContextExtensionPoint.resolve() as? ResolutionResult.Resolved)?.context
}

private object IdeaKotlinSerializationContextExtensionPoint {
    private val isExtensionsAvailable =
        this::class.java.classLoader.loadClassOrNull("com.intellij.openapi.extensions.ExtensionPointName") != null

    private val EP_NAME_OR_NULL: ExtensionPointName<IdeaKotlinSerializationContext>? =
        if (isExtensionsAvailable) ExtensionPointName.create(
            "org.jetbrains.kotlin.idea.gradleTooling.serialization.IdeaKotlinSerializationContext"
        ) else null

    sealed interface ResolutionResult {
        object ExtensionsUnavailable : ResolutionResult
        object MissingExtension : ResolutionResult
        data class ConflictingExtensions(val extensions: List<IdeaKotlinSerializationContext>) : ResolutionResult
        data class Resolved(val context: IdeaKotlinSerializationContext) : ResolutionResult
    }

    fun resolve(): ResolutionResult {
        if (!isExtensionsAvailable || EP_NAME_OR_NULL == null) return ResolutionResult.ExtensionsUnavailable
        val extensions = EP_NAME_OR_NULL.extensionList
        if (extensions.isEmpty()) return ResolutionResult.MissingExtension
        if (extensions.size > 1) return ResolutionResult.ConflictingExtensions(extensions)
        return ResolutionResult.Resolved(extensions.first())
    }
}

internal class NoIdeaKotlinSerializationContextException(message: String) : IllegalStateException(message)