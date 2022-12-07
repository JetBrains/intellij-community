// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.goto

import com.intellij.ide.util.gotoByName.AbstractPrimeSymbolNavigationContributor
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.GotoClassContributor
import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass

class KotlinGotoClassContributor : ChooseByNameContributorEx, GotoClassContributor {
    override fun getQualifiedName(item: NavigationItem): String? {
        val declaration = item as? KtNamedDeclaration ?: return null
        return declaration.fqName?.asString()
    }

    override fun getQualifiedNameSeparator() = "."

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        for (index in indices) {
            if (!StubIndex.getInstance().processAllKeys(index.key, processor, scope, filter)) break
        }
    }

    override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        val project = parameters.project
        val scope = KotlinSourceFilterScope.projectFiles(parameters.searchScope, project)
        val filter = parameters.idFilter
        val processor2: (t: NavigationItem) -> Boolean = {
            if (it !is KtEnumEntry) {
                processor.process(it)
            } else {
                true
            }
        }
        if (!KotlinClassShortNameIndex.processElements(name, project, scope, filter, processor2)) return
        KotlinTypeAliasShortNameIndex.processElements(name, project, scope, filter, processor2)
    }

    private val indices = listOf(KotlinClassShortNameIndex, KotlinTypeAliasShortNameIndex)
}

/*
* Logic in IDEA that adds classes to "go to symbol" popup result goes around GotoClassContributor.
* For Kotlin classes it works using light class generation.
* We have to process Kotlin builtIn classes separately since no light classes are built for them.
* */
class KotlinGotoSymbolContributor : ChooseByNameContributorEx, GotoClassContributor {
    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        for (index in indices) {
            if (!StubIndex.getInstance().processAllKeys(index.key, processor, scope, filter)) break
        }
    }

    override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        val project = parameters.project
        val scope = KotlinSourceFilterScope.projectFiles(parameters.searchScope, project)

        val filter = parameters.idFilter
        if (!KotlinFunctionShortNameIndex.processElements(name, project, scope, filter) {
            val method = LightClassUtil.getLightClassMethod(it)
            if (method == null || it.name != method.name) {
                processor.process(it)
            } else {
                true
            }
        }) return

        if (!KotlinPropertyShortNameIndex.processElements(name, project, scope, filter) {
            if (LightClassUtil.getLightClassBackingField(it) == null || it.containingClass()?.isInterface() == true) {
                processor.process(it)
            } else {
                true
            }
        }) return

        if (!KotlinClassShortNameIndex.processElements(name, project, scope, filter) {
            if (it is KtEnumEntry || it.containingFile.virtualFile?.extension == KotlinBuiltInFileType.defaultExtension) {
                processor.process(it)
            } else {
                true
            }
        }) return
        if (!KotlinTypeAliasShortNameIndex.processElements(name, project, scope, filter, processor)) return
        KotlinJvmNameAnnotationIndex.processElements(name, project, scope, filter, processor)
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

    private val indices = listOf(
        KotlinFunctionShortNameIndex,
        KotlinPropertyShortNameIndex,
        KotlinClassShortNameIndex,
        KotlinTypeAliasShortNameIndex,
        KotlinJvmNameAnnotationIndex
    )

}

class KotlinGotoPrimeSymbolContributor : AbstractPrimeSymbolNavigationContributor(KotlinPrimeSymbolNameIndex.key)
