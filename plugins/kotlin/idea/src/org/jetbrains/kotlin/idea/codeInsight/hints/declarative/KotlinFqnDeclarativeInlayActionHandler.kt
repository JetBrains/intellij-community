// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints.declarative

import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import org.jetbrains.kotlin.idea.codeInsight.hints.resolveClass
import kotlin.let

class KotlinFqnDeclarativeInlayActionHandler : InlayActionHandler {
    companion object {
        const val HANDLER_NAME: String = "kotlin.fqn.class"
    }

    override fun handleClick(editor: Editor, payload: InlayActionPayload) {
        val project = editor.project ?: return
        val fqName = (payload as? StringInlayActionPayload)?.text ?: return
        (project.resolveClass(fqName)?.navigationElement as? Navigatable)?.let {
            it.navigate(true)
        }
    }
}