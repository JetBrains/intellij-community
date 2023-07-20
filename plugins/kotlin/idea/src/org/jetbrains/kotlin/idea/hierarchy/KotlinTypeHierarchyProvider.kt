// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.hierarchy

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.hierarchy.type.JavaTypeHierarchyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.asJava.toFakeLightClass
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class KotlinTypeHierarchyProvider : JavaTypeHierarchyProvider() {
    private fun getOriginalPsiClassOrCreateLightClass(classOrObject: KtClassOrObject, module: Module?): PsiClass? {
        val fqName = classOrObject.fqName
        if (fqName != null && module?.platform.isJvm()) {
            val javaClassId = JavaToKotlinClassMap.mapKotlinToJava(fqName.toUnsafe())
            if (javaClassId != null) {
                return JavaPsiFacade.getInstance(classOrObject.project).findClass(
                    javaClassId.asSingleFqName().asString(),
                    GlobalSearchScope.allScope(classOrObject.project)
                )
            }
        }
        return classOrObject.toLightClass() ?: classOrObject.toFakeLightClass()
    }

    private fun getTargetByReference(
        project: Project,
        editor: Editor,
        module: Module?
    ): PsiClass? {
        return when (val target = TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().allAccepted)) {
            is PsiClass -> target
            is KtConstructor<*> -> getOriginalPsiClassOrCreateLightClass(target.getContainingClassOrObject(), module)
            is KtClassOrObject -> getOriginalPsiClassOrCreateLightClass(target, module)
            is KtNamedFunction -> { // Factory methods
                val functionName = target.name ?: return null
                val returnTypeText = target.typeReference?.text
                if (returnTypeText?.substringAfter(".") != functionName) return null
                val classOrObject = KotlinClassShortNameIndex.get(functionName, project, GlobalSearchScope.allScope(project)).singleOrNull()
                    ?: return null
                getOriginalPsiClassOrCreateLightClass(classOrObject, module)
            }
            else -> null
        }
    }

    private fun getTargetByContainingElement(editor: Editor, file: PsiFile): PsiClass? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null
        val classOrObject = element.getNonStrictParentOfType<KtClassOrObject>() ?: return null
        return getOriginalPsiClassOrCreateLightClass(classOrObject, file.module)
    }

    override fun getTarget(dataContext: DataContext): PsiClass? {
        val project = PlatformDataKeys.PROJECT.getData(dataContext) ?: return null

        val editor = PlatformDataKeys.EDITOR.getData(dataContext)
        if (editor != null) {
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
            if (!RootKindFilter.projectAndLibrarySources.matches(file)) return null
            val psiElement = getTargetByReference(project, editor, file.module) ?: getTargetByContainingElement(editor, file)
            if (psiElement is PsiNamedElement && psiElement.name == null) {
                return null
            }
            return psiElement
        }

        val element = LangDataKeys.PSI_ELEMENT.getData(dataContext)
        if (element is KtClassOrObject) return getOriginalPsiClassOrCreateLightClass(element, element.module)

        return null
    }
}

