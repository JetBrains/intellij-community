// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.goto

import com.intellij.ide.util.gotoByName.AbstractPrimeSymbolNavigationContributor
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.GotoClassContributor
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
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
abstract class AbstractKotlinGotoSymbolContributor<T : PsiElement>(
    private val index: KotlinStringStubIndexExtension<T>,
    private val useOriginalScope: Boolean = false
) : ChooseByNameContributorEx, GotoClassContributor {
    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        StubIndex.getInstance().processAllKeys(index.key, processor, scope, filter)
    }

    override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        val project = parameters.project
        val scope =
            if (useOriginalScope) {
                parameters.searchScope
            } else {
                KotlinSourceFilterScope.projectFiles(parameters.searchScope, project)
            }
        val filter = parameters.idFilter
        index.processElements(name, project, scope, filter, wrapProcessor(processor))
    }

    open fun wrapProcessor(processor: Processor<in NavigationItem>): Processor<T> = Processor {
        processor.process(it as? NavigationItem ?: return@Processor true)
    }

    override fun getQualifiedName(item: NavigationItem): String? =
        ((item as? KtCallableDeclaration)?.receiverTypeReference?.typeElement as? KtUserType)?.referencedName?.let { receiverType ->
            "$receiverType.${item.name}"
        }

    override fun getQualifiedNameSeparator(): String = "."
}

class KotlinGotoFunctionSymbolContributor: AbstractKotlinGotoSymbolContributor<KtNamedFunction>(KotlinFunctionShortNameIndex) {
    override fun wrapProcessor(processor: Processor<in NavigationItem>): Processor<KtNamedFunction> = Processor {
        val method = LightClassUtil.getLightClassMethod(it)
        if (method == null || it.name != method.name) {
            processor.process(it)
        } else {
            true
        }
    }
}

class KotlinGotoPropertySymbolContributor: AbstractKotlinGotoSymbolContributor<KtNamedDeclaration>(KotlinPropertyShortNameIndex) {

    override fun wrapProcessor(processor: Processor<in NavigationItem>): Processor<KtNamedDeclaration> = Processor {
        if (LightClassUtil.getLightClassBackingField(it) == null || it.containingClass()?.isInterface() == true) {
            processor.process(it)
        } else {
            true
        }
    }
}

class KotlinGotoClassSymbolContributor: AbstractKotlinGotoSymbolContributor<KtClassOrObject>(KotlinClassShortNameIndex) {
    override fun wrapProcessor(processor: Processor<in NavigationItem>): Processor<KtClassOrObject> = Processor {
        if (it is KtEnumEntry || it.containingFile.virtualFile?.extension == KotlinBuiltInFileType.defaultExtension) {
            processor.process(it)
        } else {
            true
        }
    }
}

class KotlinGotoTypeAliasSymbolContributor: AbstractKotlinGotoSymbolContributor<KtTypeAlias>(KotlinTypeAliasShortNameIndex)

class KotlinGotoJvmNameSymbolContributor: AbstractKotlinGotoSymbolContributor<KtAnnotationEntry>(KotlinJvmNameAnnotationIndex, true) {
    override fun getQualifiedName(item: NavigationItem): String? =
        if (item is KtAnnotationEntry && item.shortName?.asString() == JvmFileClassUtil.JVM_NAME_SHORT) {
            JvmFileClassUtil.getLiteralStringFromAnnotation(item)
        } else {
            null
        }
}

class KotlinGotoPrimeSymbolContributor : AbstractPrimeSymbolNavigationContributor(KotlinPrimeSymbolNameIndex.key)