/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.analysis.providers.ide

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.java.stubs.index.JavaFieldNameIndex
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.impl.java.stubs.index.JavaMethodNameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.providers.CompiledDeclarationProvider
import org.jetbrains.kotlin.analysis.providers.CompiledDeclarationProviderFactory
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

internal class KotlinIdeCompiledDeclarationProviderFactory(private val project: Project) : CompiledDeclarationProviderFactory() {
    override fun createCompiledDeclarationProvider(searchScope: GlobalSearchScope): CompiledDeclarationProvider {
        return KotlinIdeCompiledDeclarationProvider(project, searchScope)
    }
}

private class KotlinIdeCompiledDeclarationProvider(
    private val project: Project,
    private val searchScope: GlobalSearchScope
) : CompiledDeclarationProvider() {
    override fun getClassesByClassId(classId: ClassId): Collection<PsiClass> {
        return JavaFullClassNameIndex
            .getInstance()[classId.asIndexKey(), project, searchScope]
    }

    override fun getFunctions(callableId: CallableId): Collection<PsiMethod> {
        return JavaMethodNameIndex
            .getInstance()[callableId.callableName.identifier, project, searchScope]
            .filter { it.checkContainingClass(callableId) }
    }

    override fun getProperties(callableId: CallableId): Collection<PsiField> {
        return JavaFieldNameIndex
            .getInstance()[callableId.callableName.identifier, project, searchScope]
            .filter { it.checkContainingClass(callableId) }
    }

    companion object  {
        private fun PsiMember.checkContainingClass(callableId: CallableId): Boolean {
            val className = callableId.className?.shortName()?.identifier ?: return true
            return containingClass?.name == className
        }

        private fun ClassId.asIndexKey(): Int =
            asSingleFqName().hashCode()
    }
}
