// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration.framework

import com.intellij.psi.JavaPsiFacade
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinTestFrameworkUtils.isAnnotated
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

abstract class AbstractKotlinTestFramework : KotlinTestFramework {
    abstract val markerClassFqn: String
    abstract val disabledTestAnnotation: String

    override val isSlow: Boolean = false

    override fun responsibleFor(declaration: KtNamedDeclaration): Boolean {
        val markerPsiClass = JavaPsiFacade.getInstance(declaration.project)
            .findClass(markerClassFqn, declaration.resolveScope)

        if (markerPsiClass == null) {
            return false
        }

        return when (declaration) {
            is KtClassOrObject -> isTestClass(declaration)
            is KtNamedFunction -> {
                val containingClass = declaration.containingClassOrObject ?: return false
                return isTestClass(containingClass) && isTestMethod(declaration)
            }
            else -> false
        }
    }

    override fun isTestClass(declaration: KtClassOrObject): Boolean {
        return when {
            declaration.isPrivate() -> false
            declaration.isAnnotation() -> false
            declaration.annotations.isNotEmpty() -> true // There might be magic test annotations
            declaration.superTypeListEntries.any { it is KtSuperTypeCallEntry } -> true
            declaration.declarations.any { it is KtNamedFunction && it.isPublic } -> true
            else -> false
        }
    }

    override fun isTestMethod(function: KtNamedFunction): Boolean {
        return when {
            function.isTopLevel -> false
            function.isLocal -> false
            function.hasModifier(KtTokens.PRIVATE_KEYWORD) -> false
            function.hasModifier(KtTokens.ABSTRACT_KEYWORD) -> false
            function.isExtensionDeclaration() -> false
            function.containingClassOrObject?.isObjectLiteral() == true -> false
            else -> true
        }
    }

    override fun isIgnoredMethod(function: KtNamedFunction): Boolean =
        function.isAnnotated("kotlin.test.Ignore") || function.isAnnotated(disabledTestAnnotation)


    override fun qualifiedName(declaration: KtNamedDeclaration): String? {
        return when (declaration) {
            is KtClassOrObject -> declaration.fqName?.asString()
            is KtNamedFunction -> declaration.containingClassOrObject?.fqName?.asString()
            else -> null
        }
    }
}