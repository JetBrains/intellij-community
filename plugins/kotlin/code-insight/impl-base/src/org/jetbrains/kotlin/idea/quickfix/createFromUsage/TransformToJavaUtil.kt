// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.createFromUsage

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.idea.refactoring.createJavaClass
import org.jetbrains.kotlin.idea.refactoring.createJavaField
import org.jetbrains.kotlin.idea.refactoring.createJavaMethod
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

object TransformToJavaUtil {
    fun transformToJavaMemberIfApplicable(declaration: KtNamedDeclaration, packageFqName: FqName, isExtension:Boolean, needStatic: Boolean, targetClass: PsiClass): Boolean {
        if (isExtension) return false

        if (!targetClass.canRefactorElement()) return false

        val project = declaration.project

        val newJavaMember = convertToJava(declaration, packageFqName, targetClass) ?: return false

        val modifierList = newJavaMember.modifierList!!
        if (newJavaMember is PsiMethod || newJavaMember is PsiClass) {
            modifierList.setModifierProperty(PsiModifier.FINAL, false)
        }

        modifierList.setModifierProperty(PsiModifier.STATIC, needStatic)

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(newJavaMember)

        val descriptor = OpenFileDescriptor(project, targetClass.containingFile.virtualFile)
        val targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)!!
        targetEditor.selectionModel.removeSelection()

        when (newJavaMember) {
            is PsiMethod -> CreateFromUsageUtils.setupEditor(newJavaMember, targetEditor)
            is PsiField -> targetEditor.caretModel.moveToOffset(newJavaMember.endOffset - 1)
            is PsiClass -> {
                val constructor = newJavaMember.constructors.firstOrNull()
                val superStatement = constructor?.body?.statements?.firstOrNull() as? PsiExpressionStatement
                val superCall = superStatement?.expression as? PsiMethodCallExpression
                if (superCall != null) {
                    val lParen = superCall.argumentList.firstChild
                    targetEditor.caretModel.moveToOffset(lParen.endOffset)
                } else {
                    targetEditor.caretModel.moveToOffset(newJavaMember.nameIdentifier?.startOffset ?: newJavaMember.startOffset)
                }
            }
        }
        targetEditor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)

        return true
    }

    private fun getClassKind(declaration: KtDeclaration) : org.jetbrains.kotlin.descriptors.ClassKind {
        return when {
            declaration is KtObjectDeclaration -> org.jetbrains.kotlin.descriptors.ClassKind.OBJECT
            declaration is KtEnumEntry -> org.jetbrains.kotlin.descriptors.ClassKind.ENUM_ENTRY
            declaration is KtClass && declaration.isAnnotation() -> org.jetbrains.kotlin.descriptors.ClassKind.ANNOTATION_CLASS
            declaration is KtClass && declaration.isEnum() -> org.jetbrains.kotlin.descriptors.ClassKind.ENUM_CLASS
            declaration is KtClass && declaration.isInterface() -> org.jetbrains.kotlin.descriptors.ClassKind.INTERFACE
            else -> org.jetbrains.kotlin.descriptors.ClassKind.CLASS
        }
    }
    fun convertToJava(declaration: KtNamedDeclaration, packageFqName: FqName, targetClass: PsiClass): PsiMember? {
        val psiFactory = KtPsiFactory(declaration.project)

        psiFactory.createPackageDirectiveIfNeeded(packageFqName)?.let {
            declaration.containingFile.addBefore(it, null)
        }

        val adjustedDeclaration = when (declaration) {
            is KtNamedFunction, is KtProperty -> {
                val klass = psiFactory.createClass("class Foo {}")
                klass.body!!.add(declaration)
                (declaration.replace(klass) as KtClass).body!!.declarations.first()
            }
            else -> declaration
        }

        return when (adjustedDeclaration) {
            is KtNamedFunction, is KtSecondaryConstructor -> {
                createJavaMethod(adjustedDeclaration as KtFunction, targetClass)
            }
            is KtProperty -> {
                createJavaField(adjustedDeclaration, targetClass)
            }
            is KtClass -> {
                createJavaClass(adjustedDeclaration, targetClass, getClassKind(adjustedDeclaration))
            }
            else -> null
        }
    }
}