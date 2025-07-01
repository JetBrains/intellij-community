// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.api.serialization.lookup.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiFileWithStubSupport
import com.intellij.psi.impl.source.StubbedSpine
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
sealed interface PsiElementModel {
    val virtualFileUrl: String
    val elementClass: String

    fun restore(project: Project): PsiElement?

    @Serializable
    @SerialName("ByPsi")
    data class ByPsi(
        override val virtualFileUrl: String,
        override val elementClass: String,
        val startOffset: Int,
        val endOffset: Int,
    ) : PsiElementModel {
        override fun restore(project: Project): PsiElement? {
            val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(virtualFileUrl) ?: return null
            val psiFile = virtualFile.findPsiFile(project) ?: return null
            var candidate: PsiElement? = psiFile.findElementAt(startOffset) ?: psiFile
            while (candidate != null && (candidate is PsiFile || candidate !is PsiFileSystemItem)) {
                if (matches(candidate)) {
                    return candidate
                }
                candidate = candidate.parent
            }
            return null
        }

        private fun matches(element: PsiElement): Boolean {
            return element::class.java.name == elementClass
                    && element.startOffset == startOffset
                    && element.endOffset == endOffset
        }
    }

    @Serializable
    @SerialName("ByStub")
    data class ByStub(
        override val virtualFileUrl: String,
        override val elementClass: String,
        val stubIndex: Int,
    ) : PsiElementModel {
        override fun restore(project: Project): PsiElement? {
            val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(virtualFileUrl) ?: return null
            val psiFile = virtualFile.findPsiFile(project) as? PsiFileWithStubSupport ?: return null

            val spine: StubbedSpine = psiFile.getStubbedSpine()
            val element = spine.getStubPsi(stubIndex) ?: return null
            if (element::class.java.name != elementClass) return null
            return element
        }
    }

    companion object {
        fun create(psi: PsiElement): PsiElementModel {
            val rangeResult = runCatching {
                psi.textRange // some elements cannot provide their range, e.g., they cannot be properly decompiled due to obfuscation
            }
            val range = rangeResult.getOrNull()
            if (range != null) {
                return ByPsi(
                    virtualFileUrl = psi.containingFile.originalFile.virtualFile.url,
                    elementClass = psi::class.java.name,
                    startOffset = range.startOffset,
                    endOffset = range.endOffset,
                )
            } else {
                if (psi !is StubBasedPsiElement<*>) {
                    throw IllegalStateException(
                        "Cannot serialize PSI element $psi of type ${psi::class.java} without range and stub",
                        rangeResult.exceptionOrNull()
                    )
                }
                val stubIndex = PsiAnchor.calcStubIndex(psi)
                return ByStub(
                    virtualFileUrl = psi.containingFile.originalFile.virtualFile.url,
                    elementClass = psi::class.java.name,
                    stubIndex = stubIndex,
                )
            }
        }
    }
}