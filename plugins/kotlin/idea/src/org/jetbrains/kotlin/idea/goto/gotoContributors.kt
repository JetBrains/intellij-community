// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.goto

import com.intellij.ide.util.gotoByName.AbstractPrimeSymbolNavigationContributor
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.GotoClassContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass

/*
* Logic in IDEA that adds classes to "go to symbol" popup result goes around GotoClassContributor.
* For Kotlin classes it works using light class generation.
* We have to process Kotlin builtIn classes separately since no light classes are built for them.
* */
abstract class AbstractKotlinGotoSymbolContributor<T : NavigatablePsiElement>(
    private val helper: KotlinStringStubIndexHelper<T>,
    private val useOriginalScope: Boolean = false
) : ChooseByNameContributorEx, GotoClassContributor, PossiblyDumbAware {
    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode {
            helper.processAllKeys(scope, filter, processor)
        }
    }

    override fun isDumbAware(): Boolean = FileBasedIndex.isIndexAccessDuringDumbModeEnabled()

    override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        val project = parameters.project
        val scope =
            if (useOriginalScope) {
                parameters.searchScope
            } else {
                KotlinSourceFilterScope.projectFiles(parameters.searchScope, project)
            }
        val filter = parameters.idFilter
        val wrapProcessor = wrapProcessor(processor)
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode {
            helper.processElements(name, project, scope, filter, wrapProcessor)
        }
    }

    open fun wrapProcessor(processor: Processor<in T>): Processor<in T> = processor

    override fun getQualifiedName(item: NavigationItem): String? =
        ((item as? KtCallableDeclaration)?.receiverTypeReference?.typeElement as? KtUserType)?.referencedName?.let { receiverType ->
            "$receiverType.${item.name}"
        }

    override fun getQualifiedNameSeparator(): String = "."
}

class KotlinGotoClassContributor : AbstractKotlinGotoSymbolContributor<KtClassOrObject>(KotlinClassShortNameIndex) {
    override fun getQualifiedName(item: NavigationItem): String? =
        (item as? KtNamedDeclaration)?.fqName?.asString()

    override fun wrapProcessor(processor: Processor<in KtClassOrObject>): Processor<in KtClassOrObject> = Processor {
        if (it !is KtEnumEntry) {
            processor.process(it)
        } else {
            true
        }
    }
}
class KotlinGotoTypeAliasContributor: AbstractKotlinGotoSymbolContributor<KtTypeAlias>(KotlinTypeAliasShortNameIndex) {
    override fun getQualifiedName(item: NavigationItem): String? =
        (item as? KtNamedDeclaration)?.fqName?.asString()

}

class KotlinGotoFunctionSymbolContributor: AbstractKotlinGotoSymbolContributor<KtNamedFunction>(KotlinFunctionShortNameIndex) {
    // as LCs rely on resolve, hence indices
    override fun isDumbAware(): Boolean = false

    override fun wrapProcessor(processor: Processor<in KtNamedFunction>): Processor<KtNamedFunction> = Processor {
        val method = LightClassUtil.getLightClassMethod(it)
        if (method == null || it.name != method.name) {
            processor.process(it)
        } else {
            true
        }
    }
}

class KotlinGotoPropertySymbolContributor: AbstractKotlinGotoSymbolContributor<KtNamedDeclaration>(KotlinPropertyShortNameIndex) {

    // as LCs rely on resolve, hence indices
    override fun isDumbAware(): Boolean = false

    override fun wrapProcessor(processor: Processor<in KtNamedDeclaration>): Processor<KtNamedDeclaration> = Processor {
        if (LightClassUtil.getLightClassBackingField(it) == null || it.containingClass()?.isInterface() == true) {
            processor.process(it)
        } else {
            true
        }
    }
}

class KotlinGotoClassSymbolContributor: AbstractKotlinGotoSymbolContributor<KtClassOrObject>(KotlinClassShortNameIndex) {
    override fun wrapProcessor(processor: Processor<in KtClassOrObject>): Processor<KtClassOrObject> = Processor {
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

class KotlinGotoPrimeSymbolContributor : AbstractPrimeSymbolNavigationContributor(KotlinPrimeSymbolNameIndex.indexKey)