// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.ClassKind
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateClassUtil
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class CreateKotlinClassAction(
    val element: KtElement,
    val kind: ClassKind,
    private val applicableParents: List<PsiElement>,
    val inner: Boolean,
    val open: Boolean,
    val name: String,
    private val superClassName: String?,
    private val paramList: String,
    private val returnTypeString: String
) : IntentionAction {
    override fun getText(): String = KotlinBundle.message("create.0.1", kind.description, name)
    override fun getFamilyName(): String = KotlinBundle.message("fix.create.from.usage.family")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (kind == ClassKind.DEFAULT) return false
        if (applicableParents.isEmpty()) return false
        applicableParents.forEach {
            if (it is PsiClass) {
                if (kind == ClassKind.OBJECT || kind == ClassKind.ENUM_ENTRY) return false
                if (it.isInterface && inner) return false
            }
        }
        return true
    }

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null || file !is KtFile) return
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return
        }

        CreateClassUtil.chooseAndCreateClass(project, editor, file, element, kind, applicableParents, name, text) { targetParent ->
            runCreateClassBuilder(
                file,
                element,
                targetParent,
                name,
                superClassName,
                paramList,
                returnTypeString
            )
        }
    }

    private fun runCreateClassBuilder(
        file: KtFile,
        element: KtElement,
        targetParent: PsiElement,
        className: String,
        superClassName: String?,
        paramList: String,
        returnTypeString: String
    ) {
        val callableInfo = NewCallableInfo(
            definitionAsString = "",
            parameterCandidates = listOf(), //todo
            candidatesOfRenderedReturnType = listOf(),   //todo
            containerClassFqName = FqName(className),
            isForCompanion = false,
            typeParameterCandidates = listOf(), //todo
            superClassCandidates = listOfNotNull(superClassName)
        )

        val declaration = CreateClassUtil.createClassDeclaration(
            file.project,
            paramList,
            returnTypeString,
            kind,
            className,
            applicableParents,
            open,
            inner,
            isInsideInnerOrLocalClass(targetParent), null
        )
        declaration.typeParameterList?.delete()
        val editor = CreateKotlinCallablePsiEditor(file.project, callableInfo)
        val anchor = element
        val insertContainer: PsiElement = targetParent
        editor.showEditor(declaration, anchor, false, targetParent, insertContainer)
    }
    private fun isInsideInnerOrLocalClass(containingElement: PsiElement): Boolean {
        val classOrObject = containingElement.getNonStrictParentOfType<KtClassOrObject>()
        return classOrObject is KtClass && (classOrObject.isInner() || classOrObject.isLocal)
    }
}