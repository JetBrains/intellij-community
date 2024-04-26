// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

interface KotlinSupportAvailability {

    companion object {
        val EP_NAME = ExtensionPointName<KotlinSupportAvailability>("org.jetbrains.kotlin.supportAvailability")

        private val handlers: List<KotlinSupportAvailability>
            get() = EP_NAME.extensionList

        fun isSupported(ktElement: KtElement): Boolean {
            return handlers.all { it.isSupported(ktElement) }
        }

        fun isSupported(project: Project, file: VirtualFile): Boolean {
            return handlers.all { it.isSupported(project, file) }
        }
    }

    @Nls
    fun name(): String

    fun isSupported(project: Project, file: VirtualFile): Boolean {
        if (!file.isValid || !file.isKotlinFileType()) return true
        return (file.toPsiFile(project) as? KtFile)?.let(::isSupported) != false
    }

    fun isSupported(ktElement: KtElement): Boolean

}