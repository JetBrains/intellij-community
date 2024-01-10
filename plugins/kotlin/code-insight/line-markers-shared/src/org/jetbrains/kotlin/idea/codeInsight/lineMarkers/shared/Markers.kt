// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared

import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import com.intellij.openapi.roots.libraries.Library
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.base.facet.isHMPPEnabled
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoVariantsService
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibrarySourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.searching.kmp.findAllActualForExpect
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import javax.swing.Icon

val KtNamedDeclaration.expectOrActualAnchor: PsiElement
    @ApiStatus.Internal
    get() = nameIdentifier ?: when (this) {
        is KtConstructor<*> -> getConstructorKeyword() ?: valueParameterList?.leftParenthesis
        is KtObjectDeclaration -> getObjectKeyword()
        else -> null
    } ?: this

val PsiElement.markerDeclaration: KtDeclaration?
    @ApiStatus.Internal
    get() = (this as? KtDeclaration) ?: (parent as? KtDeclaration)

@ApiStatus.Internal
fun Document.areAnchorsOnOneLine(
    first: KtNamedDeclaration,
    second: KtNamedDeclaration?
): Boolean {
    if (second == null) return false
    val firstAnchor = first.expectOrActualAnchor
    val secondAnchor = second.expectOrActualAnchor
    return this.getLineNumber(firstAnchor.textRange.startOffset) == this.getLineNumber(secondAnchor.textRange.startOffset)
}

@ApiStatus.Internal
fun KtDeclaration.findMarkerBoundDeclarations(): Sequence<KtNamedDeclaration> {
    if (this !is KtClass && this !is KtParameter) return emptySequence()
    val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)

    fun <T : KtNamedDeclaration> Sequence<T>.takeBound(bound: KtNamedDeclaration) = takeWhile {
        document?.areAnchorsOnOneLine(bound, it) == true
    }

    return when (this) {
        is KtParameter -> {
            val propertyParameters = takeIf { hasValOrVar() }?.containingClassOrObject
                ?.primaryConstructorParameters
                ?: return emptySequence()

            propertyParameters
                .asSequence()
                .dropWhile { it !== this }
                .drop(1)
                .takeBound(this)
                .filter { it.hasValOrVar() }
        }

        is KtEnumEntry -> {
            val enumEntries = containingClassOrObject?.body?.enumEntries ?: return emptySequence()
            enumEntries.asSequence().dropWhile { it !== this }.drop(1).takeBound(this)
        }

        is KtClass -> {
            val boundParameters = primaryConstructor?.valueParameters.orEmpty()
                .asSequence()
                .takeBound(this)
                .filter { it.hasValOrVar() }

            val boundEnumEntries = this.takeIf { isEnum() }?.body?.enumEntries?.asSequence()?.takeBound(this).orEmpty()
            boundParameters + boundEnumEntries
        }
        else -> emptySequence()
    }
}

@ApiStatus.Internal
fun KtNamedDeclaration.areMarkersForbidden(
    document: Document? = PsiDocumentManager.getInstance(project).getDocument(containingFile)
): Boolean {
    when (this) {
        is KtPrimaryConstructor -> return true
        is KtParameter -> {
            if (document?.areAnchorsOnOneLine(this, containingClassOrObject) == true) {
                return true
            }
            if (hasValOrVar()) {
                val parameters = containingClassOrObject?.primaryConstructorParameters.orEmpty()
                val previousParameter = parameters.getOrNull(parameters.indexOf(this) - 1)
                if (document?.areAnchorsOnOneLine(this, previousParameter) == true) {
                    return true
                }
            }
        }
        is KtEnumEntry -> {
            if (document?.areAnchorsOnOneLine(this, containingClassOrObject) == true) {
                return true
            }
            val enumEntries = containingClassOrObject?.body?.enumEntries.orEmpty()
            val previousEnumEntry = enumEntries.getOrNull(enumEntries.indexOf(this) - 1)
            if (document?.areAnchorsOnOneLine(this, previousEnumEntry) == true) {
                return true
            }
        }
    }
    return false
}

private class ActualExpectedPsiElementCellRenderer(private val onlyModuleNames: Boolean) : PsiTargetPresentationRenderer<PsiElement>() {
    override fun getContainerText(element: PsiElement): String? = if (onlyModuleNames) null else element.moduleName()

    override fun getIcon(element: PsiElement): Icon = when (element.moduleInfo) {
        is LibrarySourceInfo, is LibraryInfo -> AllIcons.Nodes.PpLibFolder
        else -> AllIcons.Nodes.Module
    }

    override fun getElementText(element: PsiElement): String = if (onlyModuleNames) element.moduleName() else super.getElementText(element)

    @Nls
    private fun PsiElement.moduleName() = moduleInfo.nameForTooltip()
}

@Nls
@Suppress("HardCodedStringLiteral")
fun ModuleInfo.nameForTooltip(): String {
    when (this) {
        /* For hmpp modules, prefer the module name, if present */
        is ModuleSourceInfo -> takeIf { module.isHMPPEnabled }?.module?.name?.let { return it }

        /* For libraries, we're trying to show artifact variant name */
        is LibrarySourceInfo -> library.extractVariantName(binariesModuleInfo as? LibraryInfo)?.let { return it }
        is LibraryInfo -> library.extractVariantName(this)?.let { return it }
    }

    stableName?.asStringStripSpecialMarkers()?.let { return it }

    // We want to represent actual descriptors, so let's represent them by platform
    return platform.componentPlatforms.joinToString(", ", "{", "}") { it.platformName }
}

/*
    Supported formats:

    <groupId>:<artifactId>:<variant>:<version>
    <groupId>:<artifactId>-<variant>:<version>
 */
private fun Library.extractVariantName(binariesModuleInfo: LibraryInfo?): String? {
    binariesModuleInfo?.let(LibraryInfoVariantsService::bundledLibraryVariant)?.displayName?.let { return it }

    val split = name.orEmpty().split(":")
    if (split.size != 3 && split.size != 4) {
        return null
    }

    return when (split.size) {
        3 -> split[1].substringAfterLast('-')
        4 -> split[2]
        else -> null
    }
}

private fun Collection<KtDeclaration>.hasUniqueModuleNames() = distinctBy { it.moduleInfo.nameForTooltip() }.size == size

@ApiStatus.Internal
fun buildNavigateToActualDeclarationsPopup(element: PsiElement?, allNavigatableActualDeclarations: KtDeclaration.() -> Collection<KtDeclaration>): NavigationPopupDescriptor? {
    return element?.markerDeclaration?.let {
        val navigatableActualDeclarations = it.allNavigatableActualDeclarations()
        if (navigatableActualDeclarations.isEmpty()) return null
        val name = it.name ?: ""
        return NavigationPopupDescriptor(
            navigatableActualDeclarations,
            KotlinLineMarkersSharedBundle.message("highlighter.title.choose.actual.for", name),
            KotlinLineMarkersSharedBundle.message("highlighter.title.actuals.for", name),
            ActualExpectedPsiElementCellRenderer(onlyModuleNames = navigatableActualDeclarations.hasUniqueModuleNames())
        )
    }
}

@ApiStatus.Internal
fun buildNavigateToExpectedDeclarationsPopup(element: PsiElement?, allNavigatableExpectedDeclarations: (KtDeclaration) -> Collection<KtDeclaration>): NavigationPopupDescriptor? {
    return element?.markerDeclaration?.let {
        val navigatableExpectedDeclarations = allNavigatableExpectedDeclarations(it)
        if (navigatableExpectedDeclarations.isEmpty()) return null
        val name = it.name ?: ""
        return NavigationPopupDescriptor(
            navigatableExpectedDeclarations,
            KotlinLineMarkersSharedBundle.message("highlighter.title.choose.expected.for", name),
            KotlinLineMarkersSharedBundle.message("highlighter.title.expected.for", name),
            ActualExpectedPsiElementCellRenderer(onlyModuleNames = navigatableExpectedDeclarations.hasUniqueModuleNames())
        )
    }
}

fun KtDeclaration.isExpectDeclaration(): Boolean {
    return when {
        hasExpectModifier() -> true
        else -> containingClassOrObject?.isExpectDeclaration() == true
    }
}

fun hasExpectForActual(declaration: KtDeclaration): Boolean {
    return analyze(declaration) {
        val symbol: KtDeclarationSymbol = declaration.getSymbol()
        symbol.getExpectsForActual().isNotEmpty()
    }
}

fun KtDeclaration.allNavigatableExpectedDeclarations(): List<SmartPsiElementPointer<KtDeclaration>> {
    return expectedDeclarationIfAny() + findMarkerBoundDeclarations().flatMap { it.expectedDeclarationIfAny() }
}

internal fun KtDeclaration.expectedDeclarationIfAny(): List<SmartPsiElementPointer<KtDeclaration>> {
    val declaration = this
    return analyze(this) {
        val symbol: KtDeclarationSymbol = declaration.getSymbol()
        (symbol.getExpectsForActual().mapNotNull { (it.psi as? KtDeclaration)?.createSmartPointer() })
    }
}

fun KtDeclaration.allNavigatableActualDeclarations(): Collection<SmartPsiElementPointer<KtDeclaration>> =
    findAllActualForExpect().toSet() + findMarkerBoundDeclarations().flatMap { it.findAllActualForExpect() }

fun KtElement.containingClassOrObjectOrSelf(): KtClassOrObject? = parentOfType(withSelf = true)
