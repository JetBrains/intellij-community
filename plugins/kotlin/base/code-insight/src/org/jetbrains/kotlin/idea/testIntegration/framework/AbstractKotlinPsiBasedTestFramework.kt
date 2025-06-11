// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration.framework

import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.ThreeState
import com.intellij.util.ThreeState.*
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.KOTLIN_TEST_IGNORE
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

abstract class AbstractKotlinPsiBasedTestFramework : KotlinPsiBasedTestFramework {
    protected abstract val markerClassFqn: String
    protected abstract val disabledTestAnnotation: String
    protected abstract val allowTestMethodsInObject: Boolean

    protected open fun isFrameworkAvailable(element: KtElement): Boolean =
        isFrameworkAvailable(element, markerClassFqn, true)

    protected fun isFrameworkAvailable(element: KtElement, markerClassFqn: String, javaOnly: Boolean): Boolean {
        val module = element.module ?: return false
        val javaClassExists = JavaLibraryUtil.hasLibraryClass(module, markerClassFqn)
        if (javaOnly || javaClassExists) return javaClassExists
        val scope = module.getModuleWithDependenciesAndLibrariesScope(true)
        val processor = Processor<Any> { false }
        return !KotlinFullClassNameIndex.processElements(markerClassFqn, element.project, scope, processor) ||
                !KotlinTopLevelTypeAliasFqNameIndex.processElements(markerClassFqn, element.project, scope, processor)
    }

    override fun responsibleFor(declaration: KtNamedDeclaration): Boolean {
        if (!isFrameworkAvailable(declaration)) {
            return false
        }

        return when (declaration) {
            is KtClassOrObject -> checkTestClass(declaration) == YES
            is KtNamedFunction -> (declaration.containingClassOrObject?.let(::checkTestClass) ?: NO) == YES
                    && isTestMethod(declaration)

            else -> false
        }
    }

    override fun checkTestClass(declaration: KtClassOrObject): ThreeState {
        if (!isFrameworkAvailable(declaration)) {
            return NO
        }
        return when {
            declaration.isAnnotation() -> NO
            (declaration.isTopLevel() && declaration is KtObjectDeclaration) && !allowTestMethodsInObject -> NO
            declaration.annotationEntries.isNotEmpty() -> UNSURE
            declaration.superTypeListEntries.any { it is KtSuperTypeCallEntry } -> UNSURE
            declaration.declarations.any { it is KtNamedFunction && !it.isPrivate() } -> UNSURE
            declaration.declarations.any { it is KtClassOrObject && !it.isPrivate() } -> UNSURE
            else -> NO
        }
    }

    override fun isTestMethod(declaration: KtNamedFunction): Boolean {
        return when {
            declaration.isTopLevel -> false
            declaration.isLocal -> false
            declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) -> false
            declaration.hasModifier(KtTokens.ABSTRACT_KEYWORD) -> false
            declaration.isExtensionDeclaration() -> false
            else -> {
                val ktClassOrObject =
                    if (allowTestMethodsInObject) declaration.getStrictParentOfType<KtClassOrObject>() else declaration.containingClass()
                ktClassOrObject?.let(::checkTestClass) == YES
            }
        }
    }

    override fun isIgnoredMethod(declaration: KtNamedFunction): Boolean {
        return (isAnnotated(declaration, KOTLIN_TEST_IGNORE)
                || isAnnotated(declaration, disabledTestAnnotation)) && isTestMethod(declaration)
    }

    protected fun checkNameMatch(file: KtFile, fqNames: Set<String>, shortName: String): Boolean =
        fqNames.intersect(findFqNameCandidates(file, shortName)).isNotEmpty()

    private fun findFqNameCandidates(file: KtFile, shortName: String): Set<String> {
        val outer = shortName.substringBefore('.')
        val inner = shortName.substringAfter('.', "")
        fun append(innerPart: String?) = if (innerPart.isNullOrEmpty()) "" else ".$innerPart"

        // direct import
        for (importDirective in file.importDirectives) {
            if (!importDirective.isValidImport) continue
            val importedFqName = importDirective.importedFqName?.asString() ?: continue

            when {
                importDirective.aliasName == shortName -> return setOf(importedFqName)
                importedFqName.endsWith(".$shortName") -> return setOf(importedFqName)
                importDirective.aliasName == outer -> return setOf(importedFqName + append(inner))
                importedFqName.endsWith(".$outer") -> return setOf(importedFqName + append(inner))
            }
        }

        val candidates = mutableSetOf<String>()

        // in the current file
        findChildrenKtClassOrObject(file)
            .firstOrNull { it.name == outer }
            ?.fqName?.asString()
            ?.let { candidates.add(it + append(inner)) }

        // same package
        if (file.packageFqName.isRoot) {
            candidates.add(shortName)
        } else {
            candidates.add("${file.packageFqName}.$shortName")
        }

        // demand imports
        for (importDirective in file.importDirectives) {
            if (!importDirective.isValidImport) continue
            if (!importDirective.isAllUnder) continue
            val importedFqName = importDirective.importedFqName?.asString() ?: continue
            candidates.add("$importedFqName.$shortName")
        }

        return candidates
    }

    protected fun isAnnotated(element: KtAnnotated, fqName: String): Boolean {
        return isAnnotated(element, setOf(fqName))
    }

    protected fun isAnnotated(element: KtAnnotated, fqNames: Set<String>): Boolean {
        return findAnnotation(element, fqNames) != null
    }

    protected fun findAnnotation(element: KtAnnotated, fqNames: Set<String>): KtAnnotationEntry? {
        val annotationEntries = element.annotationEntries
        if (annotationEntries.isEmpty()) {
            return null
        }

        val file = element.containingKtFile

        for (annotationEntry in annotationEntries) {
            val shortName = annotationEntry.shortName ?: continue
            val fqName = annotationEntry.typeReference?.text
            if (fqName in fqNames || checkNameMatch(file, fqNames, shortName.asString())) {
                return annotationEntry
            }
        }

        for (annotationEntry in annotationEntries) {
            val shortName = annotationEntry.shortName ?: continue
            val fqName = annotationEntry.typeReference?.text ?: shortName.asString()

            val clazz = findChildrenKtClassOrObject(file)
                .firstOrNull { it.fqName?.asString()?.endsWith(".${fqName}") == true || it.fqName?.asString() == fqName }
                ?.toLightClass()
            if (clazz != null && MetaAnnotationUtil.isMetaAnnotated(clazz, fqNames)) return annotationEntry

            for (fq in findFqNameCandidates(file, fqName)) {
                val classes = JavaFullClassNameIndex.getInstance().getClasses(fq, element.project, element.resolveScope)
                for (cls in classes) {
                    if (MetaAnnotationUtil.isMetaAnnotated(cls, fqNames)) {
                        return annotationEntry
                    }
                }
            }
        }
        return null
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

    private fun findChildrenKtClassOrObject(file: KtFile): Collection<KtClassOrObject> {
        return CachedValuesManager.getCachedValue(file) {
            val result = PsiTreeUtil.findChildrenOfType(file, KtClassOrObject::class.java)
            CachedValueProvider.Result.create(result, file)
        }
    }
}