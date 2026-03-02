// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared

import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.idea.base.facet.isHMPPEnabled
import org.jetbrains.kotlin.idea.base.platforms.KotlinNativeLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.detectLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.isKlibLibraryRootForPlatform
import org.jetbrains.kotlin.idea.base.projectStructure.bundledLibraryVariant
import org.jetbrains.kotlin.idea.base.projectStructure.extractLibraryVariantName
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.projectStructure.kmp.SourceModuleDependenciesFilterCandidate
import org.jetbrains.kotlin.idea.base.projectStructure.openapiLibrary
import org.jetbrains.kotlin.idea.base.projectStructure.openapiModule
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.searching.kmp.findAllActualForExpect
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
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

    override fun getIcon(element: PsiElement): Icon = when (element.getKaModule(element.project, useSiteModule = null)) {
        is KaLibraryModule, is KaLibrarySourceModule -> AllIcons.Nodes.PpLibFolder
        else -> AllIcons.Nodes.Module
    }

    override fun getElementText(element: PsiElement): String = if (onlyModuleNames) element.moduleName() else super.getElementText(element)

    @Nls
    private fun PsiElement.moduleName() = getKaModule(project, useSiteModule = null).nameForTooltip()
}

@Nls
@OptIn(KaExperimentalApi::class)
@Suppress("HardCodedStringLiteral")
fun KaModule.nameForTooltip(): String {
    when (this) {
        /* For hmpp modules, prefer the module name, if present */
        is KaSourceModule -> takeIf { openapiModule.isHMPPEnabled }?.openapiModule?.name?.let { return it }

        /* For libraries, we're trying to show artifact variant name */
        is KaLibrarySourceModule -> binaryLibrary.openapiLibrary?.extractVariantName(binaryLibrary)?.let { return it }
        is KaLibraryModule -> openapiLibrary?.extractVariantName(this)?.let { return it }
    }

    (this as? KaSourceModule)?.stableModuleName?.let { Name.guessByFirstCharacter(it) }?.asStringStripSpecialMarkers()?.let { return it }

    // We want to represent actual descriptors, so let's represent them by platform
    return targetPlatform.componentPlatforms.joinToString(", ", "{", "}") { it.platformName }
}

/*
    Supported formats:

    [prefix:] <groupId>:<artifactId>:<variant>:<version>
    [prefix:] <groupId>:<artifactId>-<variant>:<version>
 */
private fun Library.extractVariantName(binariesModuleInfo: KaLibraryModule?): String? {
    if (binariesModuleInfo == null) return null

    val project = binariesModuleInfo.project

    val isNativeStdLib = detectLibraryKind(this, project) == KotlinNativeLibraryKind &&
            getFiles(OrderRootType.CLASSES).singleOrNull {
                it.isKlibLibraryRootForPlatform(NativePlatforms.unspecifiedNativePlatform)
            }?.path?.endsWith(KONAN_STDLIB_NAME) == true
    bundledLibraryVariant(this, isNativeStdLib, project)?.displayName?.let { return it }

    return extractLibraryVariantName(name)
}

private fun Collection<KtDeclaration>.hasUniqueModuleNames() =
    distinctBy { it.getKaModule(it.project, useSiteModule = null).nameForTooltip() }.size == size

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

@OptIn(KaExperimentalApi::class)
fun hasExpectForActual(declaration: KtDeclaration): Boolean {
    return analyze(declaration) {
        val symbol: KaDeclarationSymbol = declaration.symbol
        symbol.getExpectsForActual().isNotEmpty()
    }
}

fun KtDeclaration.allNavigatableExpectedDeclarations(): List<SmartPsiElementPointer<KtDeclaration>> {
    return expectedDeclarationIfAny() + findMarkerBoundDeclarations().flatMap { it.expectedDeclarationIfAny() }
}

@OptIn(KaExperimentalApi::class)
internal fun KtDeclaration.expectedDeclarationIfAny(): List<SmartPsiElementPointer<KtDeclaration>> {
    val declaration = this
    return analyze(this) {
        val symbol: KaDeclarationSymbol = declaration.symbol
        (symbol.getExpectsForActual().mapNotNull { (it.psi as? KtDeclaration)?.createSmartPointer() })
    }
}

fun KtDeclaration.allNavigatableActualDeclarations(): Collection<SmartPsiElementPointer<KtDeclaration>> =
    findAllActualForExpect().toSet() + findMarkerBoundDeclarations().flatMap { it.findAllActualForExpect() }

fun KtElement.containingClassOrObjectOrSelf(): KtClassOrObject? = parentOfType(withSelf = true)
