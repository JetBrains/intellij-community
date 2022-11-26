// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.serialization

import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmProject


/**
 * Used as service in [IdeaKpmProjectSerializationService].
 * The implementation of this interface is expected to be provided by the IntelliJ process and is not implemented
 * within this module directly, since this module's code is running inside the Gradle process as well.
 */
interface IdeaKpmProjectDeserializer {
    fun read(data: ByteArray): IdeaKpmProject?
}
