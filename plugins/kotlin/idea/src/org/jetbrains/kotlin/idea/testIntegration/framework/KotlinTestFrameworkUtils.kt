// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration.framework

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object KotlinTestFrameworkUtils {
    /**
     * Checks whether a class with [qualifiedName] exists in the (module + its dependencies) scope.
     * @param namedDeclaration defines the module.
     */
    fun hasClass(qualifiedName: String, namedDeclaration: KtNamedDeclaration) =
        JavaPsiFacade.getInstance(namedDeclaration.project)
            .findClass(qualifiedName, namedDeclaration.resolveScope) != null

    inline fun <reified T : Any, E : KtElement> cached(element: E, crossinline function: (E) -> T?): T? {
        return CachedValuesManager.getCachedValue(element) {
            CachedValueProvider.Result.create({ function(element) }, PsiModificationTracker.MODIFICATION_COUNT)
        }.invoke()
    }

    fun getTopmostClass(psiClass: KtClassOrObject): KtClassOrObject {
        var topLevelClass: KtClassOrObject? = psiClass
        while (topLevelClass != null && !topLevelClass.isTopLevel()) {
            topLevelClass = topLevelClass.getParentOfType(true) // call for anonymous object might result in 'null'
        }
        return topLevelClass ?: psiClass
    }

    fun KtAnnotated.isAnnotated(fqName: String): Boolean = annotationEntries.any {
        it.isFqName(fqName)
    }

    private fun KtAnnotationEntry?.isFqName(fqName: String): Boolean {
        val shortName = this?.shortName?.asString() ?: return false
        return containingKtFile.isResolvable(fqName, shortName)
    }

    fun KtFile.isResolvable(fqName: String, shortName: String): Boolean {
        return fqName.endsWith(shortName) && importDirectives.filter { it.isValidImport }.any {
            // TODO: aliases
            val name = it.importedFqName?.asString()
            name != null &&
                    fqName == if (!it.isAllUnder) name else "$name.$shortName"
        }
    }
}