// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.stubindex.resolve

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface KotlinCorruptedIndexListener {
    fun corruptionDetected()

    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic(KotlinCorruptedIndexListener::class.java)
    }
}
