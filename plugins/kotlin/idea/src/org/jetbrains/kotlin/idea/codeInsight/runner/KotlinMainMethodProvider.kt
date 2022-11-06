// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.runner

import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.classes.KtLightClassBase
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacadeBase
import org.jetbrains.kotlin.asJava.classes.runReadAction
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.hasMain
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinMainMethodProvider: JavaMainMethodProvider {
    override fun isApplicable(clazz: PsiClass): Boolean {
        return clazz is KtLightClassBase
    }

    override fun hasMainMethod(clazz: PsiClass): Boolean {
        val lightClassBase = clazz.safeAs<KtLightClassBase>()
        val mainFunctionDetector = KotlinMainFunctionDetector.getInstance()
        if (lightClassBase is KtLightClassForFacadeBase) {
            return runReadAction { lightClassBase.files.any { mainFunctionDetector.hasMain(it) } }
        }
        val classOrObject = lightClassBase?.kotlinOrigin ?: return false
        return runReadAction { mainFunctionDetector.hasMain(classOrObject) }
    }

    override fun findMainInClass(clazz: PsiClass): PsiMethod? {
        val lightClassBase = clazz.safeAs<KtLightClassBase>()
        val mainFunctionDetector = KotlinMainFunctionDetector.getInstance()
        if (lightClassBase is KtLightClassForFacadeBase) {
            val files = lightClassBase.files
            for (file in files) {
                for (declaration in file.declarations) {
                    when(declaration) {
                        is KtNamedFunction -> if (runReadAction { mainFunctionDetector.isMain(declaration) }) {
                            return declaration.toLightMethods().firstOrNull()
                        }
                        is KtClassOrObject -> findMainFunction(declaration, mainFunctionDetector)?.let {
                            return it
                        }
                    }
                }
            }
            return null
        }
        val classOrObject = lightClassBase?.kotlinOrigin ?: return null

        return findMainFunction(classOrObject, mainFunctionDetector)
    }

    private fun findMainFunction(
        classOrObject: KtClassOrObject,
        mainFunctionDetector: KotlinMainFunctionDetector
    ): PsiMethod? {
        if (classOrObject is KtObjectDeclaration) {
            return findMainFunction(classOrObject, mainFunctionDetector)
        }

        for (companionObject in classOrObject.companionObjects) {
            findMainFunction(companionObject, mainFunctionDetector)?.let { return it }
        }
        return null
    }

    private fun findMainFunction(objectDeclaration: KtObjectDeclaration, mainFunctionDetector: KotlinMainFunctionDetector): PsiMethod? {
        if (objectDeclaration.isObjectLiteral()) return null
        val mainFunction =
            objectDeclaration.declarations.firstOrNull { it is KtNamedFunction && runReadAction { mainFunctionDetector.isMain(it) } } as? KtNamedFunction
        return mainFunction?.toLightMethods()?.firstOrNull()
    }
}