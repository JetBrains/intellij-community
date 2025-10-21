// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structureView

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.Queryable
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.ui.icons.RowIcon
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.idea.projectView.getStructureDeclarations
import org.jetbrains.kotlin.idea.structureView.AbstractKotlinStructureViewElement
import org.jetbrains.kotlin.idea.structureView.getStructureViewChildren
import org.jetbrains.kotlin.psi.*
import javax.swing.Icon
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class KotlinFirStructureViewElement(
    override val element: NavigatablePsiElement,
    ktElement: KtElement,
    private val isInherited: Boolean = false
) : PsiTreeElementBase<NavigatablePsiElement>(element), AbstractKotlinStructureViewElement, Queryable {

    private var kotlinPresentation by AssignableLazyProperty {
        KotlinFirStructureElementPresentation(isInherited, element, ktElement, createSymbolAndThen { it.createPointer() })
    }

    private var visibility by AssignableLazyProperty {
        createSymbolAndThen { Visibility(it) } ?: Visibility(null)
    }

    /**
     * @param element        represents node element, can be in current file or from super class (e.g. java)
     * @param inheritElement represents element in the current kotlin file
     */
    constructor(
        element: NavigatablePsiElement,
        inheritElement: KtElement,
        descriptor: KaSymbolPointer<*>,
        isInherited: Boolean,
    ) : this(element = element, ktElement = inheritElement, isInherited = isInherited) {
        if (element !is KtElement) {
            // Avoid storing descriptor in fields
            kotlinPresentation = KotlinFirStructureElementPresentation(isInherited, element, inheritElement, descriptor)
            analyze(inheritElement) {
                visibility = Visibility(descriptor.restoreSymbol())
            }
        }
    }

    override val accessLevel: Int?
        get() = visibility.accessLevel
    override val isPublic: Boolean
        get() = visibility.isPublic

    override fun getPresentation(): ItemPresentation = kotlinPresentation
    override fun getLocationString(): String? = kotlinPresentation.locationString
    override fun getIcon(open: Boolean): Icon? = kotlinPresentation.getIcon(open)
    override fun getPresentableText(): String? = kotlinPresentation.presentableText

    @OptIn(KaAllowAnalysisOnEdt::class)
    @TestOnly
    override fun putInfo(info: MutableMap<in String, in String?>) {
        // Sanity check for API consistency
        assert(presentation.presentableText == presentableText) { "Two different ways of getting presentableText" }
        assert(presentation.locationString == locationString) { "Two different ways of getting locationString" }

        info["text"] = presentableText
        info["location"] = locationString
        allowAnalysisOnEdt {
            info["icon"] = with(getIcon(false)) {
                (this as? RowIcon)?.allIcons?.joinToString(transform = Icon::toString) ?: this?.toString()
            }
            info["visibility"] = visibility.name ?: "none"
        }
    }

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        return element.getStructureViewChildren {
            KotlinFirStructureViewElement(it, it, isInherited = false)
        }
    }

    private fun PsiElement.collectLocalDeclarations(): List<KtDeclaration> {
        val result = mutableListOf<KtDeclaration>()

        acceptChildren(object : KtTreeVisitorVoid() {
            override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                result.add(classOrObject)
            }

            override fun visitNamedFunction(function: KtNamedFunction) {
                result.add(function)
            }
        })

        return result
    }

    private fun <T> createSymbolAndThen(modifier: KaSession.(KaSymbol) -> T): T? {
        val element = element
        return when {
            !element.isValid -> null
            element !is KtDeclaration -> null
            element is KtAnonymousInitializer -> null
            else -> runReadAction {
                if (!DumbService.isDumb(element.getProject())) {
                    analyze(element) {
                        modifier.invoke(this, element.symbol)
                    }
                } else {
                    null
                }
            }
        }
    }

    class Visibility(symbol: KaSymbol?) {
        private val visibility: KaSymbolVisibility? =
            (symbol as? KaValueParameterSymbol)?.generatedPrimaryConstructorProperty?.visibility
                ?: (symbol as? KaDeclarationSymbol)?.visibility

        val isPublic: Boolean
            get() = visibility == KaSymbolVisibility.PUBLIC

        val accessLevel: Int?
            get() = when (visibility) {
              KaSymbolVisibility.PUBLIC -> 1
              KaSymbolVisibility.INTERNAL -> 2
              KaSymbolVisibility.PROTECTED -> 3
              KaSymbolVisibility.PRIVATE -> 4
              else -> null
            }

        val name: String?
            get() = visibility?.name
    }
}

private class AssignableLazyProperty<in R, T : Any>(val init: () -> T) : ReadWriteProperty<R, T> {
    private var _value: T? = null

    override fun getValue(thisRef: R, property: KProperty<*>): T {
        return _value ?: init().also { _value = it }
    }

    override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
        _value = value
    }
}