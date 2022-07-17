// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.goto

import com.intellij.ide.util.gotoByName.AbstractPrimeSymbolNavigationContributor
import com.intellij.navigation.GotoClassContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass

class KotlinGotoClassContributor : GotoClassContributor {
    override fun getQualifiedName(item: NavigationItem): String? {
        val declaration = item as? KtNamedDeclaration ?: return null
        return declaration.fqName?.asString()
    }

    override fun getQualifiedNameSeparator() = "."

    override fun getNames(project: Project, includeNonProjectItems: Boolean): Array<String> {
        val classes = KotlinClassShortNameIndex.getAllKeys(project)
        val typeAliases = KotlinTypeAliasShortNameIndex.getAllKeys(project)
        return (classes + typeAliases).toTypedArray()
    }

    override fun getItemsByName(name: String, pattern: String, project: Project, includeNonProjectItems: Boolean): Array<NavigationItem> {
        val globalScope = if (includeNonProjectItems) GlobalSearchScope.allScope(project) else GlobalSearchScope.projectScope(project)
        val scope = KotlinSourceFilterScope.projectFiles(globalScope, project)
        val classesOrObjects = KotlinClassShortNameIndex.get(name, project, scope)
        val typeAliases = KotlinTypeAliasShortNameIndex.get(name, project, scope)

        if (classesOrObjects.isEmpty() && typeAliases.isEmpty()) return NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY

        return (classesOrObjects + typeAliases).filter { it != null && it !is KtEnumEntry }.toTypedArray()
    }
}

/*
* Logic in IDEA that adds classes to "go to symbol" popup result goes around GotoClassContributor.
* For Kotlin classes it works using light class generation.
* We have to process Kotlin builtIn classes separately since no light classes are built for them.
* */
class KotlinGotoSymbolContributor : GotoClassContributor {
    override fun getNames(project: Project, includeNonProjectItems: Boolean): Array<String> = listOf(
        KotlinFunctionShortNameIndex,
        KotlinPropertyShortNameIndex,
        KotlinClassShortNameIndex,
        KotlinTypeAliasShortNameIndex,
        KotlinJvmNameAnnotationIndex
    ).flatMap {
        StubIndex.getInstance().getAllKeys(it.key, project)
    }.toTypedArray()

    override fun getItemsByName(name: String, pattern: String, project: Project, includeNonProjectItems: Boolean): Array<NavigationItem> {
        val baseScope = if (includeNonProjectItems) GlobalSearchScope.allScope(project) else GlobalSearchScope.projectScope(project)
        val noLibrarySourceScope = KotlinSourceFilterScope.projectFiles(baseScope, project)

        val result = ArrayList<NavigationItem>()
        result += KotlinFunctionShortNameIndex.get(name, project, noLibrarySourceScope).filter {
            val method = LightClassUtil.getLightClassMethod(it)
            method == null || it.name != method.name
        }
        result += KotlinPropertyShortNameIndex.get(name, project, noLibrarySourceScope).filter {
            LightClassUtil.getLightClassBackingField(it) == null ||
                    it.containingClass()?.isInterface() ?: false
        }
        result += KotlinClassShortNameIndex.get(name, project, noLibrarySourceScope).filter {
            it is KtEnumEntry || it.containingFile.virtualFile?.fileType == KotlinBuiltInFileType
        }
        result += KotlinTypeAliasShortNameIndex.get(name, project, noLibrarySourceScope)
        result += KotlinJvmNameAnnotationIndex.get(name, project, noLibrarySourceScope)

        return result.toTypedArray()
    }

    override fun getQualifiedName(item: NavigationItem): String? {
        if (item is KtCallableDeclaration) {
            val receiverType = (item.receiverTypeReference?.typeElement as? KtUserType)?.referencedName
            if (receiverType != null) {
                return "$receiverType.${item.name}"
            }
        } else if (item is KtAnnotationEntry) {
            if (item.shortName?.asString() == JvmFileClassUtil.JVM_NAME_SHORT) {
                return JvmFileClassUtil.getLiteralStringFromAnnotation(item)
            }
        }
        return null
    }

    override fun getQualifiedNameSeparator(): String = "."
}

class KotlinGotoPrimeSymbolContributor : AbstractPrimeSymbolNavigationContributor(KotlinPrimeSymbolNameIndex.key)
