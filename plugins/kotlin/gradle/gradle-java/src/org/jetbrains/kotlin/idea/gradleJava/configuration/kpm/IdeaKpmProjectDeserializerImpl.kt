// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmProject
import org.jetbrains.kotlin.gradle.idea.proto.kpm.IdeaKpmProject
import org.jetbrains.kotlin.idea.gradleJava.configuration.serialize.IntellijIdeaKotlinSerializationContext
import org.jetbrains.kotlin.idea.gradleTooling.serialization.IdeaKpmProjectDeserializer


class IdeaKpmProjectDeserializerImpl : IdeaKpmProjectDeserializer {
    override fun read(data: ByteArray): IdeaKpmProject? {
        return IntellijIdeaKotlinSerializationContext().IdeaKpmProject(data)
    }
}
