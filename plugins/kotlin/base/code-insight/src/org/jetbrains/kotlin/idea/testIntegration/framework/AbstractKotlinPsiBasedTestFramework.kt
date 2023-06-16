// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration.framework

import com.intellij.java.library.JavaLibraryUtil
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.KOTLIN_TEST_IGNORE
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

abstract class AbstractKotlinPsiBasedTestFramework : KotlinPsiBasedTestFramework {
    abstract val markerClassFqn: String
    abstract val disabledTestAnnotation: String

    protected fun isFrameworkAvailable(element: KtElement): Boolean {
        val module = element.module ?: return false
        return JavaLibraryUtil.hasLibraryClass(module, markerClassFqn)
    }

    override fun responsibleFor(declaration: KtNamedDeclaration): Boolean {
        if (!isFrameworkAvailable(declaration)) {
            return false
        }

        return when (declaration) {
            is KtClassOrObject -> isTestClass(declaration)
            is KtNamedFunction -> (declaration.containingClassOrObject?.let(::isTestClass) ?: false) && isTestMethod(declaration)
            else -> false
        }
    }

    override fun isTestClass(declaration: KtClassOrObject): Boolean {
        return when {
            declaration.isPrivate() -> false
            declaration.isAnnotation() -> false
            declaration.annotations.isNotEmpty() -> true
            declaration.superTypeListEntries.any { it is KtSuperTypeCallEntry } -> true
            declaration.declarations.any { it is KtNamedFunction && it.isPublic } -> true
            else -> false
        }
    }

    override fun isTestMethod(declaration: KtNamedFunction): Boolean {
        return when {
            declaration.isTopLevel -> false
            declaration.isLocal -> false
            declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) -> false
            declaration.hasModifier(KtTokens.ABSTRACT_KEYWORD) -> false
            declaration.isExtensionDeclaration() -> false
            declaration.containingClassOrObject?.isObjectLiteral() == true -> false
            else -> declaration.containingClass()?.let(::isTestClass) ?: false
        }
    }

    override fun isIgnoredMethod(declaration: KtNamedFunction): Boolean {
        return isAnnotated(declaration, KOTLIN_TEST_IGNORE)
                || isAnnotated(declaration, disabledTestAnnotation)
    }

    protected fun checkNameMatch(file: KtFile, fqNames: Set<String>, shortName: String): Boolean {
        if ("${file.packageFqName}.$shortName" in fqNames) return true

        for (importDirective in file.importDirectives) {
            if (!importDirective.isValidImport) {
                continue
            }

            val importedFqName = importDirective.importedFqName?.asString() ?: continue

            if (!importDirective.isAllUnder) {
                if (importDirective.aliasName == shortName && importedFqName in fqNames) {
                    return true
                } else if (importedFqName in fqNames && importedFqName.endsWith(".$shortName")) {
                    return true
                }
            } else if ("$importedFqName.$shortName" in fqNames) {
                return true
            }
        }

        return false
    }

    protected fun isAnnotated(element: KtAnnotated, fqName: String): Boolean {
        return isAnnotated(element, setOf(fqName))
    }

    protected fun isAnnotated(element: KtAnnotated, fqNames: Set<String>): Boolean {
        val annotationEntries = element.annotationEntries
        if (annotationEntries.isEmpty()) {
            return false
        }

        val file = element.containingKtFile

        for (annotationEntry in annotationEntries) {
            val shortName = annotationEntry.shortName ?: continue
            if (checkNameMatch(file, fqNames, shortName.asString())) {
                return true
            }
        }

        return false
    }

    protected fun findAnnotatedFunction(classOrObject: KtClassOrObject?, fqNames: Set<String>): KtNamedFunction? {
        if (classOrObject == null) return null
        for (declaration in classOrObject.declarations) {
            val function = declaration as? KtNamedFunction ?: continue
            if (isAnnotated(function, fqNames)) {
                return function
            }
        }
        return null
    }
}