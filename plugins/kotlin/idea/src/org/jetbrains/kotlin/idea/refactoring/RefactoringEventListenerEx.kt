// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.util.messages.Topic

@Deprecated("Please use the frontend-agnostic KotlinRefactoringListener instead")
interface KotlinRefactoringEventListener {
    companion object {
        val EVENT_TOPIC = Topic.create("KOTLIN_REFACTORING_EVENT_TOPIC", KotlinRefactoringEventListener::class.java)
    }

    fun onRefactoringExit(refactoringId: String)
}