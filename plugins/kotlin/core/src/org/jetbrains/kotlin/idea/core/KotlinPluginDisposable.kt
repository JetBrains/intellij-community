// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

class KotlinPluginDisposable : Disposable {
    @Volatile
    var disposed: Boolean = false

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinPluginDisposable = project.service<KotlinPluginDisposable>()
    }

    override fun dispose() {
        disposed = true
    }
}