// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.serialization

import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinSerializationContext
import java.util.*

fun IdeaKotlinSerializationContext(classLoader: ClassLoader): IdeaKotlinSerializationContext {
    return ServiceLoader.load(IdeaKotlinSerializationContext::class.java, classLoader).firstOrNull()
        ?: error("Missing ${IdeaKotlinSerializationContext::class.java.name}")
}