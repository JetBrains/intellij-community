// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.v1.getKtFile
import org.jetbrains.kotlin.psi.KtFile
import java.io.Serializable

interface CachedConfigurationInputs: Serializable {
    fun isUpToDate(project: Project, file: VirtualFile, ktFile: KtFile? = null): Boolean

    object UpToDate: CachedConfigurationInputs {
        override fun isUpToDate(project: Project, file: VirtualFile, ktFile: KtFile?): Boolean = true
    }

    data class PsiModificationStamp(
        val fileModificationStamp: Long,
        val psiModificationStamp: Long
    ) : CachedConfigurationInputs {
        override fun isUpToDate(project: Project, file: VirtualFile, ktFile: KtFile?): Boolean =
            get(project, file, ktFile) == this

        companion object {
            fun get(project: Project, file: VirtualFile, ktFile: KtFile?): PsiModificationStamp {
                val actualKtFile = project.getKtFile(file, ktFile)
                return PsiModificationStamp(
                    file.modificationStamp,
                    actualKtFile?.modificationStamp ?: 0
                )
            }
        }
    }

    data class SourceContentsStamp(val source: String) : CachedConfigurationInputs {
        override fun isUpToDate(project: Project, file: VirtualFile, ktFile: KtFile?): Boolean {
            return get(file) == this
        }

        companion object {
            fun get(file: VirtualFile): SourceContentsStamp {
                val text = runReadAction {
                  FileDocumentManager.getInstance().getDocument(file)!!.text
                }

                return SourceContentsStamp(text)
            }
        }
    }
}