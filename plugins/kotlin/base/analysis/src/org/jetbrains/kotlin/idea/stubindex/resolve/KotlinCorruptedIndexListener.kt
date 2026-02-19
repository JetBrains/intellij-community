// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.stubindex.resolve

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.messages.Topic

@IntellijInternalApi
interface KotlinCorruptedIndexListener {
    fun corruptionDetected()

    companion object {
        @Topic.ProjectLevel
        val TOPIC = Topic(KotlinCorruptedIndexListener::class.java)
    }
}
