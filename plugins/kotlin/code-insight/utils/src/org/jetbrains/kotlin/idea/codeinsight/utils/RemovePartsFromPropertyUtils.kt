// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.codeInspection.util.IntentionName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

object RemovePartsFromPropertyUtils {

    @IntentionName
    fun getRemovePartsFromPropertyActionName(
        removeInitializer: Boolean,
        removeGetter: Boolean,
        removeSetter: Boolean
    ): String {
        val chunks = ArrayList<String>(3).apply {
            if (removeGetter) add(KotlinBundle.message("text.getter"))
            if (removeSetter) add(KotlinBundle.message("text.setter"))
            if (removeInitializer) add(KotlinBundle.message("text.initializer"))
        }

        fun concat(head: String, tail: String): String {
            return "$head and $tail"
        }

        val partsText = when (chunks.size) {
            0 -> ""
            1 -> chunks.single()
            2 -> concat(chunks[0], chunks[1])
            else -> concat(chunks.dropLast(1).joinToString(", "), chunks.last())
        }

        return KotlinBundle.message("remove.0.from.property", partsText)
    }
}
