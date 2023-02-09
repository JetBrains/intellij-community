// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.serialize

import org.jetbrains.kotlin.gradle.idea.proto.tcs.IdeaKotlinClasspathSerializer
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinBooleanExtrasSerializer
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinExtrasSerializationExtensionBuilder
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinExtrasSerializer
import org.jetbrains.kotlin.gradle.idea.tcs.extras.*
import org.jetbrains.kotlin.idea.gradle.configuration.serialize.KotlinExtrasSerializationService
import org.jetbrains.kotlin.tooling.core.extrasKeyOf

/**
 * All extras owned and maintained by the Kotlin team.
 */
class KotlinGradlePluginExtrasSerializationService : KotlinExtrasSerializationService {
    override fun IdeaKotlinExtrasSerializationExtensionBuilder.extensions() {
        /* For adding debug Strings to entities */
        register(extrasKeyOf("kotlin.debug"), IdeaKotlinExtrasSerializer.javaIoSerializable())
        register(KlibExtra.key, IdeaKotlinExtrasSerializer.javaIoSerializable())
        register(isIdeaProjectLevelKey, IdeaKotlinBooleanExtrasSerializer)
        register(isNativeDistributionKey, IdeaKotlinBooleanExtrasSerializer)
        register(isNativeStdlibKey, IdeaKotlinBooleanExtrasSerializer)
        register(projectArtifactsClasspathKey, IdeaKotlinClasspathSerializer)
        register(sourcesClasspathKey, IdeaKotlinClasspathSerializer)
        register(documentationClasspathKey, IdeaKotlinClasspathSerializer)
    }
}
