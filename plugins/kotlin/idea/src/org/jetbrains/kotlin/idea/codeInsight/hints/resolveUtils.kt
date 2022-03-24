// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtTypeAlias

internal fun Project.resolveClass(fqNameString: String, scope: GlobalSearchScope = GlobalSearchScope.allScope(this)): PsiElement? =
    resolveFqNameOfKtClassByIndex(fqNameString, scope)
        ?: resolveFqNameOfJavaClassByIndex(fqNameString, scope)

private fun Project.resolveFqNameOfJavaClassByIndex(fqNameString: String, scope: GlobalSearchScope): PsiClass? {
    return JavaFullClassNameIndex.getInstance()[fqNameString, this, scope]
        .firstOrNull {
            it.qualifiedName == fqNameString
        }
}

private fun Project.resolveFqNameOfKtClassByIndex(fqNameString: String, scope: GlobalSearchScope): KtDeclaration? {
    val classesPsi = KotlinFullClassNameIndex.getInstance()[fqNameString, this, scope]
    val typeAliasesPsi = KotlinTopLevelTypeAliasFqNameIndex.getInstance()[fqNameString, this, scope]

    return scope.selectNearest(classesPsi, typeAliasesPsi)
}

private fun GlobalSearchScope.selectNearest(classesPsi: Collection<KtDeclaration>, typeAliasesPsi: Collection<KtTypeAlias>): KtDeclaration? {
    val scope = this
    return when {
        typeAliasesPsi.isEmpty() -> classesPsi.firstOrNull()
        classesPsi.isEmpty() -> typeAliasesPsi.firstOrNull()
        else -> (classesPsi.asSequence() + typeAliasesPsi.asSequence()).minWithOrNull(Comparator { o1, o2 ->
            scope.compare(o1.containingFile.virtualFile, o2.containingFile.virtualFile)
        })
    }
}