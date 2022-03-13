/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.analysis.providers.ide

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.providers.KotlinCompiledDeclarationProvider
import org.jetbrains.kotlin.analysis.providers.KotlinCompiledDeclarationProviderFactory
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

internal class KotlinIdeCompiledDeclarationProviderFactory(private val project: Project) : KotlinCompiledDeclarationProviderFactory() {
    override fun createCompiledDeclarationProvider(searchScope: GlobalSearchScope): KotlinCompiledDeclarationProvider {
        return KotlinIdeCompiledDeclarationProvider(project, searchScope)
    }
}

private class KotlinIdeCompiledDeclarationProvider(
    private val project: Project,
    private val searchScope: GlobalSearchScope
) : KotlinCompiledDeclarationProvider() {
    override fun getClassesByClassId(classId: ClassId): Collection<KtClassOrObject> = emptyList()

    override fun getTopLevelFunctions(callableId: CallableId): Collection<KtNamedFunction> = emptyList()

    override fun getTopLevelProperties(callableId: CallableId): Collection<KtProperty> = emptyList()

}
