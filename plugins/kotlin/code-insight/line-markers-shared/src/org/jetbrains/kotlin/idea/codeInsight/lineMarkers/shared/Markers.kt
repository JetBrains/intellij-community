// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.lineMarkers.shared

import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier

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

class ActualExpectedPsiElementCellRenderer : PsiTargetPresentationRenderer<PsiElement>() {
    override fun getContainerText(element: PsiElement): String = ""
}

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
            ActualExpectedPsiElementCellRenderer()
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
            ActualExpectedPsiElementCellRenderer()
        )
    }
}

fun KtDeclaration.isEffectivelyActualDeclaration(checkConstructor: Boolean = true): Boolean = when {
    hasActualModifier() -> true
    this is KtEnumEntry || checkConstructor && this is KtConstructor<*> -> containingClass()?.hasActualModifier() == true
    else -> false
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

@RequiresBackgroundThread(generateAssertion = false)
internal fun KtDeclaration.findAllExpectForActual(searchScope: SearchScope = runReadAction { useScope }): Sequence<SmartPsiElementPointer<KtDeclaration>> {
    val declaration = this
    val scope = searchScope as? GlobalSearchScope ?: return emptySequence()
    val containingClassOrObjectOrSelf = containingClassOrObjectOrSelf()
    // covers cases like classes, class functions and class properties
    containingClassOrObjectOrSelf?.fqName?.let { fqName ->
        val classOrObjects = KotlinFullClassNameIndex.getAllElements(fqName.asString(), project, scope, filter = {
            it.matchesWithActual(containingClassOrObjectOrSelf)
        })
        return if (classOrObjects.isNotEmpty()) {
            classOrObjects.asSequence().mapNotNull { classOrObject ->
                when (declaration) {
                    is KtClassOrObject -> classOrObject
                    is KtNamedDeclaration -> classOrObject.declarations.firstOrNull {
                        it is KtNamedDeclaration && it.name == declaration.name && it.matchesWithActual(declaration)
                    }

                    else -> null
                }?.createSmartPointer()
            }
        } else {
            val typeAliases = KotlinTopLevelTypeAliasFqNameIndex.getAllElements(fqName.asString(), project, scope, filter = {
                it.matchesWithActual(containingClassOrObjectOrSelf)
            })
            typeAliases.asSequence().mapNotNull { classOrObject ->
                when (declaration) {
                    is KtClassOrObject -> classOrObject
                    else -> null
                }?.createSmartPointer()
            }
        }
    }
    // top level functions
    val packageFqName = declaration.containingKtFile.packageFqName
    val topLevelFqName = packageFqName.child(Name.identifier(declaration.name!!)).asString()
    return when (declaration) {
        is KtNamedFunction -> {
            KotlinTopLevelFunctionFqnNameIndex.getAllElements(topLevelFqName, project, scope) {
                it.matchesWithActual(declaration)
            }.asSequence().map(KtNamedFunction::createSmartPointer)
        }

        is KtProperty -> {
            KotlinTopLevelPropertyFqnNameIndex.getAllElements(topLevelFqName, project, scope) {
                it.matchesWithActual(declaration)
            }.asSequence().map(KtProperty::createSmartPointer)
        }

        else -> emptySequence()
    }
}

private fun KtDeclaration.matchesWithActual(actualDeclaration: KtDeclaration): Boolean {
    val declaration = this
    return declaration.hasActualModifier() && analyze(declaration) {
        val symbol: KtDeclarationSymbol = declaration.getSymbol()
        return symbol.getExpectsForActual().any { it.psi == actualDeclaration }
    }
}

fun KtDeclaration.allNavigatableActualDeclarations(): Collection<SmartPsiElementPointer<KtDeclaration>> =
    findAllExpectForActual().toSet() + findMarkerBoundDeclarations().flatMap { it.findAllExpectForActual() }

fun KtElement.containingClassOrObjectOrSelf(): KtClassOrObject? = parentOfType(withSelf = true)
