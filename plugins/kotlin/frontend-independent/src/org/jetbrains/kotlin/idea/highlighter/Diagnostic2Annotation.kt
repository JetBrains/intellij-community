// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

object Diagnostic2Annotation {
    @Nls
    fun getHtmlMessage(diagnostic: Diagnostic, renderMessage: (Diagnostic) -> String): String {
        var message = renderMessage(diagnostic)
        if (isApplicationInternalMode() || isUnitTestMode()) {
            val factoryName = diagnostic.factory.name
            message = if (message.startsWith("<html>")) {
                @Suppress("HardCodedStringLiteral")
                "<html>[$factoryName] ${message.substring("<html>".length)}"
            } else {
                "[$factoryName] $message"
            }
        }
        if (!message.startsWith("<html>")) {
            message = "<html><body>${XmlStringUtil.escapeString(message)}</body></html>"
        }
        return message
    }

    fun getMessage(diagnostic: Diagnostic, renderMessage: (Diagnostic) -> String): String {
        val message = renderMessage(diagnostic)
        return if (isApplicationInternalMode() || isUnitTestMode()) {
            "[${diagnostic.factory.name}] $message"
        } else message
    }

    fun getDefaultMessage(diagnostic: Diagnostic): String {
        val message = DefaultErrorMessages.render(diagnostic)
        return if (isApplicationInternalMode() || isUnitTestMode()) {
            "[${diagnostic.factory.name}] $message"
        } else message
    }
}