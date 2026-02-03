// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.copyPaste

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class KotlinCopyPasteCoroutineScopeService(val coroutineScope: CoroutineScope) {
    companion object {
        fun getCoroutineScope(project: Project): CoroutineScope = project.service<KotlinCopyPasteCoroutineScopeService>().coroutineScope
    }
}