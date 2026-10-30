// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.ide.actions.JavaQualifiedNameProvider
import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class KotlinQualifiedNameProvider : QualifiedNameProvider {
    override fun adjustElementToCopy(element: PsiElement) = null

    override fun getQualifiedName(element: PsiElement) = when (element) {
        is KtClassOrObject -> element.fqName?.asString()
        is KtFunction -> getJavaQualifiedName(LightClassUtil.getLightClassMethod(element))
            ?: (element as? KtNamedFunction)?.let { function -> function.name?.let { getMemberQualifiedName(function, it) } }

        is KtProperty -> {
            val lightClassPropertyMethods = LightClassUtil.getLightClassPropertyMethods(element)
            val lightElement: PsiElement? = lightClassPropertyMethods.getter ?: lightClassPropertyMethods.backingField
            getJavaQualifiedName(lightElement)
                ?: element.name?.let { getMemberQualifiedName(element, JvmAbi.getterName(it)) }
        }
        is KtParameter -> if (element.hasValOrVar()) getConstructorPropertyQualifiedName(element) else null
        is KtTypeAlias -> element.getClassId()?.asSingleFqName()?.asString()
        is KtNameReferenceExpression -> element.mainReference.resolve()?.let {
            QualifiedNameProviderUtil.getQualifiedName(it)
        }
        else -> null
    }

    /** Names a primary constructor `val`/`var` as a [KtProperty]: its getter or backing field, else from Kotlin PSI. */
    private fun getConstructorPropertyQualifiedName(parameter: KtParameter): String? {
        val lightClassPropertyMethods = LightClassUtil.getLightClassPropertyMethods(parameter)
        val lightElement: PsiElement? = lightClassPropertyMethods.getter ?: lightClassPropertyMethods.backingField
        getJavaQualifiedName(lightElement)?.let { return it }
        val name = parameter.name ?: return null
        val owner = (parameter.ownerFunction as? KtPrimaryConstructor)?.getContainingClassOrObject() ?: return null
        val memberName = if (owner is KtClass && owner.isAnnotation()) name else JvmAbi.getterName(name)
        return getMemberQualifiedName(parameter, memberName, owner)
    }

    private fun getJavaQualifiedName(element: PsiElement?) = element?.let { JavaQualifiedNameProvider().getQualifiedName(element) }

    /**
     * Names [declaration] from Kotlin PSI when it has no light element, as for a function with a value class parameter.
     * The format is the one of [JavaQualifiedNameProvider]: `<class>#<memberName>`. [container] is the class or object that
     * declares the member, null for a top-level one. A member of an object literal is named by its base type.
     */
    private fun getMemberQualifiedName(
        declaration: KtCallableDeclaration,
        memberName: String,
        container: KtClassOrObject? = declaration.containingClassOrObject,
    ): String? {
        val containerFqName = when {
            container == null -> (declaration.parent as? KtFile)?.javaFileFacadeFqName?.asString()
            container is KtObjectDeclaration && container.isObjectLiteral() -> getBaseClassQualifiedName(container)
            else -> container.fqName?.asString()
        }
        return containerFqName?.let { "$it#$memberName" }
    }

    private fun getBaseClassQualifiedName(objectLiteral: KtObjectDeclaration): String? {
        val entries = objectLiteral.superTypeListEntries
        val baseEntry = entries.firstOrNull { it is KtSuperTypeCallEntry } ?: entries.firstOrNull() ?: return null
        val baseClass = (baseEntry.typeReference?.typeElement as? KtUserType)?.referenceExpression?.mainReference?.resolve()
        return when (baseClass) {
            is KtClassOrObject -> baseClass.fqName?.asString()
            is KtConstructor<*> -> baseClass.getContainingClassOrObject().fqName?.asString()
            is PsiClass -> baseClass.qualifiedName
            is PsiMethod -> baseClass.containingClass?.qualifiedName
            else -> null
        }
    }

    override fun qualifiedNameToElement(fqn: String, project: Project) = null

    override fun insertQualifiedName(fqn: String, element: PsiElement, editor: Editor, project: Project) {
    }
}
