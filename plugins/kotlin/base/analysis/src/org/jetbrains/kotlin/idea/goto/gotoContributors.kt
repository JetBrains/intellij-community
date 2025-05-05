// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.goto

import com.intellij.navigation.*
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.presentation.KotlinDefaultNamedDeclarationPresentation
import org.jetbrains.kotlin.idea.presentation.KotlinFunctionPresentation
import org.jetbrains.kotlin.idea.presentation.getPresentationInContainer
import org.jetbrains.kotlin.idea.presentation.getPresentationText
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import javax.swing.Icon

@ApiStatus.Internal
abstract class AbstractKotlinGotoSymbolContributor<T : NavigatablePsiElement>(
    private val helper: KotlinStringStubIndexHelper<T>,
    private val useOriginalScope: Boolean = false
) : ChooseByNameContributorEx, GotoClassContributor, DumbAware {

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode {
            helper.processAllKeys(scope, filter, processor)
        }
    }

    override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        val project = parameters.project
        val scope = if (useOriginalScope) {
            parameters.searchScope
        } else {
            KotlinSourceFilterScope.projectFiles(parameters.searchScope, project)
        }

        val wrapProcessor = Processor<T> { element ->
            processOriginalElement(processor, element).ifFalse { return@Processor false }
            processAdditionalElements(element, processor).ifFalse { return@Processor false }
            true
        }

        val filter = parameters.idFilter
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode {
            helper.processElements(name, project, scope, filter, wrapProcessor)
        }
    }

    protected open fun processOriginalElement(processor: Processor<in NavigationItem>, element: T): Boolean {
        return processor.process(element)
    }

    protected open fun processAdditionalElements(element: T, processor: Processor<in NavigationItem>): Boolean {
        return true
    }

    override fun getQualifiedNameSeparator(): String = "."
}


@ApiStatus.Internal
class KotlinGotoClassContributor : AbstractKotlinGotoSymbolContributor<KtClassOrObject>(KotlinClassShortNameIndex) {
    override fun getQualifiedName(item: NavigationItem): String? = (item as? KtClassOrObject)?.fqName?.asString()
}

@ApiStatus.Internal
class KotlinGotoTypeAliasContributor : AbstractKotlinGotoSymbolContributor<KtTypeAlias>(KotlinTypeAliasShortNameIndex) {
    override fun getQualifiedName(item: NavigationItem): String? = (item as? KtTypeAlias)?.fqName?.asString()
}

internal abstract class PsiElementBasedNavigationItem : PsiElementNavigationItem {
    abstract val target: KtAnnotated

    override fun canNavigate(): Boolean = target.canNavigate()
    override fun navigate(requestFocus: Boolean) = target.navigate(requestFocus)
    override fun getTargetElement(): PsiElement = target
}

private abstract class FacadeCallable : PsiElementBasedNavigationItem() {
    abstract override val target: KtCallableDeclaration
    override fun getName(): @NlsSafe String? = target.name

    fun getQualifiedName(): String? = target.getQualifiedNameInFacade()
}

@ApiStatus.Internal
class KotlinGotoFunctionSymbolContributor : AbstractKotlinGotoSymbolContributor<KtNamedFunction>(KotlinFunctionShortNameIndex) {
    override fun processAdditionalElements(element: KtNamedFunction, processor: Processor<in NavigationItem>): Boolean {
        if (element.isTopLevel) {
            return processor.process(FacadeFunction(element))
        }
        return true
    }

    private class FacadeFunction(override val target: KtNamedFunction) : FacadeCallable() {
        override fun getPresentation(): ItemPresentation {
            return object : KotlinFunctionPresentation(target) {
                override fun getLocationString(): String {
                    return getPresentationInContainer(target.containingKtFile.javaFileFacadeFqName.asString())
                }
            }
        }
    }

    override fun getQualifiedName(item: NavigationItem): String? {
        return when (item) {
            is FacadeFunction -> item.getQualifiedName()
            is KtNamedFunction -> item.getQualifiedNameByReceiverType()
            else -> null
        }
    }
}


@ApiStatus.Internal
class KotlinGotoPropertySymbolContributor : AbstractKotlinGotoSymbolContributor<KtNamedDeclaration>(KotlinPropertyShortNameIndex) {
    override fun processAdditionalElements(element: KtNamedDeclaration, processor: Processor<in NavigationItem>): Boolean {
        if (element is KtProperty && element.isTopLevel) {
            return processor.process(FacadeProperty(element))
        }
        return true
    }

    private class FacadeProperty(override val target: KtProperty) : FacadeCallable() {
        override fun getPresentation(): ItemPresentation {
            return object : KotlinDefaultNamedDeclarationPresentation(target) {
                override fun getLocationString(): String {
                    return getPresentationInContainer(target.containingKtFile.javaFileFacadeFqName.asString())
                }
            }
        }
    }

    override fun getQualifiedName(item: NavigationItem): String? {
        return when (item) {
            is FacadeProperty -> item.getQualifiedName()
            is KtProperty -> item.getQualifiedNameByReceiverType()
            else -> null
        }
    }
}

internal class KotlinGotoJvmNameSymbolContributor :
    AbstractKotlinGotoSymbolContributor<KtAnnotationEntry>(KotlinJvmNameAnnotationIndex, true) {
    override fun getQualifiedName(item: NavigationItem): String? =
        if (item is KtAnnotationEntry && item.shortName?.asString() == JvmFileClassUtil.JVM_NAME_SHORT) {
            JvmFileClassUtil.getLiteralStringFromAnnotation(item)
        } else {
            null
        }
}


internal class KotlinGotoFacadeClassContributor : AbstractKotlinGotoSymbolContributor<KtFile>(KotlinFileFacadeShortNameIndex) {

    override fun processOriginalElement(
        processor: Processor<in NavigationItem>,
        element: KtFile
    ): Boolean { // do not process KtFiles directly, instead process them via FacadeClass in processAdditionalElements
        return true
    }

    override fun processAdditionalElements(element: KtFile, processor: Processor<in NavigationItem>): Boolean {
        return processor.process(FacadeClass(element))
    }

    internal class FacadeClass(override val target: KtFile) : PsiElementBasedNavigationItem() {
        private val javaFileFacadeFqName: FqName get() = target.javaFileFacadeFqName

        override fun getName(): @NlsSafe String = javaFileFacadeFqName.shortName().asString()

        fun getQualifiedName(): String = javaFileFacadeFqName.asString()

        override fun getPresentation(): ColoredItemPresentation {
            return object : ColoredItemPresentation {
                override fun getLocationString(): @NlsSafe String? {
                    val packageName = target.packageFqName.takeUnless { it.isRoot }?.asString() ?: return null
                    return getPresentationText(packageName)
                }

                override fun getPresentableText(): @NlsSafe String = javaFileFacadeFqName.shortName().asString()
                override fun getIcon(unused: Boolean): Icon = KotlinIcons.FILE
                override fun getTextAttributesKey(): TextAttributesKey? = null
            }
        }
    }

    override fun getQualifiedName(item: NavigationItem): String? = (item as? FacadeClass)?.getQualifiedName()
}

private fun KtCallableDeclaration.getQualifiedNameInFacade(): String? {
    val name = name ?: return null
    val ktFile = containingKtFile
    val facadeFqName = ktFile.javaFileFacadeFqName
    return "$facadeFqName.$name"
}

private fun KtCallableDeclaration.getQualifiedNameByReceiverType(): String? {
    val receiverTypeReference = receiverTypeReference ?: return null
    val userType = receiverTypeReference.typeElement as? KtUserType ?: return null
    val referencedName = userType.referencedName
    return "$referencedName.$name"
}