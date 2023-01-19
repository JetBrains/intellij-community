// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.serialization

import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinSerializationContext
import java.util.*

private val ideaKotlinSerializationContextThreadLocal = ThreadLocal<IdeaKotlinSerializationContext>()

fun <T> withIdeaKotlinSerializationContext(context: IdeaKotlinSerializationContext, action: () -> T): T {
    return try {
        ideaKotlinSerializationContextThreadLocal.set(context)
        action()
    } finally {
        ideaKotlinSerializationContextThreadLocal.remove()
    }
}

fun <T> withIdeaKotlinSerializationContext(classLoader: ClassLoader, action: () -> T): T {
    return withIdeaKotlinSerializationContext(
        context = ServiceLoader.load(IdeaKotlinSerializationContext::class.java, classLoader).firstOrNull()
            ?: error("Missing ${IdeaKotlinSerializationContext::class.java.name} in $classLoader"),
        action
    )
}

val ideaKotlinSerializationContextOrNull: IdeaKotlinSerializationContext? get() = ideaKotlinSerializationContextThreadLocal.get()

val ideaKotlinSerializationContext: IdeaKotlinSerializationContext
    get() = ideaKotlinSerializationContextOrNull ?: error("Missing ${IdeaKotlinSerializationContext::class.java.name}")
