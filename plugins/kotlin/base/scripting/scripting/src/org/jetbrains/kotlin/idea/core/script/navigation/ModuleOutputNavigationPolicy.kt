// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.navigation

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.platform.backend.workspace.findEntitiesByVirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.navigation.KotlinClsCustomNavigationPolicy
import org.jetbrains.kotlin.idea.navigation.chooseCorrespondingCallable
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFullTypeAliasNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Leads from a compiled declaration in the production output of a module to the declaration in the sources of that module.
 *
 * A script definition can put a module output directory on the script classpath. The script resolves the classes there,
 * as the compiler does. The output directory belongs to no library with sources, so the standard policy stays on the class file.
 * The policy handles only the class roots of script libraries. Every other compiled declaration keeps the standard navigation.
 */
internal class ModuleOutputNavigationPolicy : KotlinClsCustomNavigationPolicy {
    override fun getNavigationElement(declaration: KtDeclaration): KtElement? {
        val module = scriptDependencyOutputModule(declaration) ?: return null
        return CachedValuesManager.getProjectPsiDependentCache(declaration) { findSourceDeclaration(it, module.moduleProductionSourceScope) }
    }

    /** The module whose production output holds [declaration], when that output is a class root of a script library. */
    private fun scriptDependencyOutputModule(declaration: KtDeclaration): Module? {
        val project = declaration.project
        val classFile = declaration.containingKtFile.virtualFile ?: return null
        val classRoot = ProjectFileIndex.getInstance(project).getClassRootForFile(classFile) ?: return null
        val workspaceModel = project.workspaceModel
        val snapshot = workspaceModel.currentSnapshot
        val entities = snapshot.getVirtualFileUrlIndex().findEntitiesByVirtualFile(classRoot, workspaceModel.getVirtualFileUrlManager()).toList()
        if (entities.none { it is KotlinScriptLibraryEntity }) return null
        return entities.filterIsInstance<JavaModuleSettingsEntity>()
            .firstOrNull { it.compilerOutput?.url == classRoot.url }
            ?.module?.findModule(snapshot)
    }

    private fun findSourceDeclaration(declaration: KtDeclaration, scope: GlobalSearchScope): KtElement? =
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
            when (declaration) {
                is KtClassLikeDeclaration -> findClassLike(declaration, scope)
                is KtCallableDeclaration -> findCallable(declaration, scope)
                else -> null
            }
        })

    private fun findClassLike(declaration: KtClassLikeDeclaration, scope: GlobalSearchScope): KtClassLikeDeclaration? {
        val fqName = declaration.getClassId()?.asFqNameString() ?: return null
        val project = declaration.project
        return KotlinFullClassNameIndex[fqName, project, scope].firstOrNull()
            ?: KotlinFullTypeAliasNameIndex[fqName, project, scope].firstOrNull()
    }

    private fun findCallable(declaration: KtCallableDeclaration, scope: GlobalSearchScope): KtElement? {
        val containingClass = declaration.containingClassOrObject
        if (containingClass != null) {
            val sourceClass = findClassLike(containingClass, scope) as? KtClassOrObject ?: return null
            val candidates =
                if (declaration is KtConstructor<*>) listOfNotNull(sourceClass.primaryConstructor) + sourceClass.secondaryConstructors
                else sourceClass.declarations
            val constructorProperty = (declaration as? KtProperty)?.let { property ->
                sourceClass.primaryConstructor?.valueParameters?.firstOrNull { it.hasValOrVar() && it.name == property.name }
            }
            // A member that the sources lack, or that changed after the build, still leads to its class.
            return chooseCorrespondingCallable(declaration, candidates) ?: constructorProperty ?: sourceClass
        }

        val fqName = declaration.fqName?.asString() ?: return null
        val project = declaration.project
        val candidates = when (declaration) {
            is KtNamedFunction -> KotlinTopLevelFunctionFqnNameIndex[fqName, project, scope]
            is KtProperty -> KotlinTopLevelPropertyFqnNameIndex[fqName, project, scope]
            else -> return null
        }
        return chooseCorrespondingCallable(declaration, candidates.toList())
    }
}
