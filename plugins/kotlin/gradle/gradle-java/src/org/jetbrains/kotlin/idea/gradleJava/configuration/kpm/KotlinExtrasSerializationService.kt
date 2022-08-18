// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import org.jetbrains.kotlin.gradle.kpm.idea.serialize.IdeaKpmExtrasSerializationExtension
import org.jetbrains.kotlin.gradle.kpm.idea.serialize.IdeaKpmExtrasSerializer
import org.jetbrains.kotlin.idea.gradle.configuration.kpm.ExtrasSerializationService
import org.jetbrains.kotlin.tooling.core.extrasKeyOf

/**
 * All extras owned and maintained by the Kotlin team.
 */
class KotlinExtrasSerializationService : ExtrasSerializationService {
    override val extension = IdeaKpmExtrasSerializationExtension {
        /* For adding debug Strings to entities */
        register(extrasKeyOf("kotlin.debug"), IdeaKpmExtrasSerializer.javaIoSerializable())
    }
}
