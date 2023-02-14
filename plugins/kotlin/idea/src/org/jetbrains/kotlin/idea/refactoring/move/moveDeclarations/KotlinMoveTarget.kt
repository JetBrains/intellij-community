// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.core.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.util.projectStructure.getModule
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

interface KotlinMoveTarget {
    val targetContainerFqName: FqName?
    val targetFileOrDir: VirtualFile?

    fun getOrCreateTargetPsi(originalPsi: PsiElement): KtElement
    fun getTargetPsiIfExists(originalPsi: PsiElement): KtElement?

    /**
     * Check possible errors and return corresponding message, or null if no errors are detected.
     * <code>null</code> means no additional verification is needed.
     */
    @NlsContexts.DialogMessage
    fun verify(file: PsiFile): String? = null

}

object EmptyKotlinMoveTarget : KotlinMoveTarget {
    override val targetContainerFqName: FqName? = null
    override val targetFileOrDir: VirtualFile? = null

    override fun getOrCreateTargetPsi(originalPsi: PsiElement): KtElement = throw UnsupportedOperationException()
    override fun getTargetPsiIfExists(originalPsi: PsiElement): KtElement? = null
}

class KotlinMoveTargetForExistingElement(val targetElement: KtElement) : KotlinMoveTarget {
    override val targetContainerFqName = targetElement.containingKtFile.packageFqName

    override val targetFileOrDir: VirtualFile = targetElement.containingKtFile.virtualFile

    override fun getOrCreateTargetPsi(originalPsi: PsiElement) = targetElement

    override fun getTargetPsiIfExists(originalPsi: PsiElement) = targetElement
}

class KotlinMoveTargetForCompanion(val targetClass: KtClass) : KotlinMoveTarget {
    override val targetContainerFqName = targetClass.companionObjects.firstOrNull()?.fqName
        ?: targetClass.fqName!!.child(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)

    override val targetFileOrDir: VirtualFile = targetClass.containingKtFile.virtualFile

    override fun getOrCreateTargetPsi(originalPsi: PsiElement) = targetClass.getOrCreateCompanionObject()

    override fun getTargetPsiIfExists(originalPsi: PsiElement) = targetClass.companionObjects.firstOrNull()
}

/**
 * Assumes that a target of move is another file (existent or not).
 * @param targetDir directory where a target file exists or to be created, used for various checks
 * @param targetFileProvider is called to get a target file (new or existing). Original file is available as an argument.
 * Result of the call is cached.
 */
class KotlinMoveTargetForDeferredFile(
    targetPackageFqName: FqName,
    targetDir: VirtualFile?,
    private val targetFileProvider: (KtFile) -> KtFile = { it }
) : KotlinMoveTarget {
    override val targetFileOrDir: VirtualFile? = targetDir
    override val targetContainerFqName: FqName = targetPackageFqName

    private val createdFiles = HashMap<KtFile, KtFile>()

    override fun getOrCreateTargetPsi(originalPsi: PsiElement): KtElement {
        val file = originalPsi.containingFile ?: error("PSI element in not contained in any file: $originalPsi")
        val originalFile = file as KtFile
        return createdFiles.getOrPut(originalFile) { targetFileProvider(originalFile) }
    }

    override fun getTargetPsiIfExists(originalPsi: PsiElement): KtElement? = null
}

class KotlinDirectoryMoveTarget(
    targetPackageFqName: FqName,
    override val targetFileOrDir: VirtualFile
) : KotlinMoveTarget {

    override val targetContainerFqName = targetPackageFqName

    override fun getOrCreateTargetPsi(originalPsi: PsiElement): KtFile {
        val file = originalPsi.containingFile ?: error("PSI element in not contained in any file: $originalPsi")
        return file as KtFile
    }

    override fun getTargetPsiIfExists(originalPsi: PsiElement): KtElement? = null
}

fun KotlinMoveTarget.getTargetModule(project: Project) = targetFileOrDir?.getModule(project)