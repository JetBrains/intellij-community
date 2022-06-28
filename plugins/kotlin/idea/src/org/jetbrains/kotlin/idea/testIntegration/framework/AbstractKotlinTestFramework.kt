// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration.framework

import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFrameworkUtils.hasClass
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFrameworkUtils.isAnnotated
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

abstract class AbstractKotlinTestFramework : KotlinTestFramework {

    abstract val markerClassFqn: String
    abstract val disabledTestAnnotation: String

    override val isSlow: Boolean = false

    override fun responsibleFor(declaration: KtNamedDeclaration): Boolean {
        if (!frameworkMarkerClassExists(declaration)) return false

        return when (declaration) {
            is KtClassOrObject -> return isTestClass(declaration)
            is KtNamedFunction -> {
                val containingClass = declaration.containingClassOrObject ?: return false
                if (!isTestClass(containingClass)) return false
                return isTestMethod(declaration)
            }
            else -> false
        }
    }

    private fun frameworkMarkerClassExists(declaration: KtNamedDeclaration): Boolean =
        hasClass(markerClassFqn, declaration)

    override fun isTestClass(declaration: KtClassOrObject): Boolean {
        return !with(declaration) {
            isPrivate()
                    || isAnnotation()
                    || (annotationEntries.isEmpty()
                    && superTypeListEntries.filterIsInstance<KtSuperTypeCallEntry>().isEmpty()
                    && declarations.filterIsInstance<KtNamedFunction>().none { it.isPublic })
        }
    }

    override fun isTestMethod(function: KtNamedFunction): Boolean {
        return !with(function) {
            isTopLevel
                    || !isPublic
                    || isAbstract()
                    || isLocal
                    || isExtensionDeclaration()
                    || getStrictParentOfType<KtObjectDeclaration>()?.isObjectLiteral() == true
        }
    }

    override fun isIgnoredMethod(function: KtNamedFunction): Boolean =
        function.isAnnotated("kotlin.test.Ignore")
                || function.isAnnotated(disabledTestAnnotation)


    override fun qualifiedName(declaration: KtNamedDeclaration): String? = when (declaration) {
        is KtClassOrObject -> declaration.fqName?.asString()
        is KtNamedFunction -> declaration.containingClassOrObject?.fqName?.asString()
        else -> null
    }
}