// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.extensions

import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

interface KotlinTestFrameworkProvider {
    companion object {
        val EP_NAME: ExtensionPointName<KotlinTestFrameworkProvider> =
            ExtensionPointName.create("org.jetbrains.kotlin.idea.testFrameworkProvider")

        private inline fun findSingleJavaTestClassInFile(file: KtFile, predicate: (PsiClass) -> Boolean): KtLightClass? {
            var found: KtLightClass? = null

            for (declaration in file.declarations) {
                if (declaration is KtClass) {
                    val javaClass = declaration.toLightClass()
                    if (javaClass != null && predicate(javaClass)) {
                        if (found != null) {
                            // There should be exactly one class
                            return null
                        }

                        found = javaClass
                    }
                }
            }

            return found
        }
    }

    val canRunJvmTests: Boolean

    fun isProducedByJava(configuration: ConfigurationFromContext): Boolean
    fun isProducedByKotlin(configuration: ConfigurationFromContext): Boolean

    fun isTestJavaClass(testClass: PsiClass): Boolean
    fun isTestJavaMethod(testMethod: PsiMethod): Boolean

    fun getJavaTestEntity(element: PsiElement, checkMethod: Boolean): JavaTestEntity? {
        val testFunction = if (checkMethod) element.getParentOfType<KtNamedFunction>(strict = false) else null
        val owner = PsiTreeUtil.getParentOfType(testFunction ?: element, KtClassOrObject::class.java, KtDeclarationWithBody::class.java)

        var testClass = (owner as? KtClass)?.toLightClass()
        if (testClass == null || !isTestJavaClass(testClass)) {
            val file = element.containingFile as? KtFile ?: return null
            testClass = findSingleJavaTestClassInFile(file, ::isTestJavaClass) ?: return null
        }

        if (testFunction != null) {
            for (testMethod in testClass.methods) {
                if (testMethod.navigationElement == testFunction) {
                    if (isTestJavaMethod(testMethod)) {
                        return JavaTestEntity(testClass, testMethod)
                    }
                    break
                }
            }
        }

        return JavaTestEntity(testClass, null)
    }

    class JavaTestEntity(val testClass: PsiClass, val testMethod: PsiMethod?)
}