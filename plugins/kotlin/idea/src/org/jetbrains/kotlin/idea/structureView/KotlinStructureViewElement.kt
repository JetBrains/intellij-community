// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structureView

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.Queryable
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.*
import javax.swing.Icon
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class KotlinStructureViewElement(
    override val element: NavigatablePsiElement,
    private val isInherited: Boolean = false
) : PsiTreeElementBase<NavigatablePsiElement>(element), Queryable, AbstractKotlinStructureViewElement {

    private var kotlinPresentation
            by AssignableLazyProperty {
                KotlinStructureElementPresentation(isInherited, element, countDescriptor())
            }

    var visibility
            by AssignableLazyProperty {
                Visibility(countDescriptor())
            }
        private set

    constructor(element: NavigatablePsiElement, descriptor: DeclarationDescriptor, isInherited: Boolean) : this(element, isInherited) {
        if (element !is KtElement) {
            // Avoid storing descriptor in fields
            kotlinPresentation = KotlinStructureElementPresentation(isInherited, element, descriptor)
            visibility = Visibility(descriptor)
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

    @TestOnly
    override fun putInfo(info: MutableMap<in String, in String?>) {
        // Sanity check for API consistency
        assert(presentation.presentableText == presentableText) { "Two different ways of getting presentableText" }
        assert(presentation.locationString == locationString) { "Two different ways of getting locationString" }

        info["text"] = presentableText
        info["location"] = locationString
    }

    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        return element.getStructureViewChildren { KotlinStructureViewElement(it, false) }
    }

    private fun countDescriptor(): DeclarationDescriptor? {
        val element = element
        return when {
            !element.isValid -> null
            element !is KtDeclaration -> null
            element is KtAnonymousInitializer -> null
            else -> runReadAction {
                if (!DumbService.isDumb(element.getProject())) {
                    element.resolveToDescriptorIfAny()
                } else null
            }
        }
    }

    class Visibility(descriptor: DeclarationDescriptor?) {
        private val visibility = (descriptor as? DeclarationDescriptorWithVisibility)?.visibility

        val isPublic: Boolean
            get() = visibility == DescriptorVisibilities.PUBLIC

        val accessLevel: Int?
            get() = when {
                visibility == DescriptorVisibilities.PUBLIC -> 1
                visibility == DescriptorVisibilities.INTERNAL -> 2
                visibility == DescriptorVisibilities.PROTECTED -> 3
                visibility?.let { DescriptorVisibilities.isPrivate(it) } == true -> 4
                else -> null
            }
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

@Deprecated("Use KotlinStructureViewUtilkt.getStructureDeclarations(KtClassOrObject) instead")
fun KtClassOrObject.getStructureDeclarations() =
     buildList {
        primaryConstructor?.let { add(it) }
        primaryConstructorParameters.filterTo(this) { it.hasValOrVar() }
        addAll(declarations)
    }

