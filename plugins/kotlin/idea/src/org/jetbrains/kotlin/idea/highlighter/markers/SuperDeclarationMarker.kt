// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyAccessorDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.codeInsight.KtFunctionPsiElementCellRenderer
import org.jetbrains.kotlin.idea.core.getDirectlyOverriddenDeclarations
import org.jetbrains.kotlin.idea.search.usagesSearch.propertyDescriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.RenderingFormat
import java.awt.event.MouseEvent

object SuperDeclarationMarkerTooltip : Function<PsiElement, String> {
    override fun `fun`(element: PsiElement): String? {
        val ktDeclaration = element.getParentOfType<KtDeclaration>(false) ?: return null
        val (elementDescriptor, overriddenDescriptors) = resolveDeclarationWithParents(ktDeclaration)
        if (overriddenDescriptors.isEmpty()) return ""

        val isAbstract = elementDescriptor!!.modality == Modality.ABSTRACT

        val renderer = DescriptorRenderer.withOptions {
            textFormat = RenderingFormat.HTML
            withDefinedIn = false
            startFromName = true
            withoutSuperTypes = true
        }

        val containingStrings = overriddenDescriptors.map {
            val declaration = it.containingDeclaration
            val memberKind = if (it is PropertyAccessorDescriptor || it is PropertyDescriptor)
                KotlinBundle.message("highlighter.tool.tip.text.property")
            else
                KotlinBundle.message("highlighter.tool.tip.text.function")

            val isBaseAbstract = it.modality == Modality.ABSTRACT
            KotlinBundle.message(
                "highlighter.text.in",
                "${if (!isAbstract && isBaseAbstract)
                    KotlinBundle.message("highlighter.text.implements")
                else
                    KotlinBundle.message("highlighter.text.overrides")
                } $memberKind",
                renderer.render(declaration)
            )
        }


        return containingStrings.sorted().joinToString(separator = "<br/>")
    }
}

class SuperDeclarationMarkerNavigationHandler : GutterIconNavigationHandler<PsiElement>, TestableLineMarkerNavigator {
    override fun navigate(e: MouseEvent?, element: PsiElement?) {
        e?.let { getTargetsPopupDescriptor(element)?.showPopup(e) }
    }

    override fun getTargetsPopupDescriptor(element: PsiElement?): NavigationPopupDescriptor? {
        val declaration = element?.getParentOfType<KtDeclaration>(false) ?: return null

        val (elementDescriptor, overriddenDescriptors) = resolveDeclarationWithParents(declaration)
        if (overriddenDescriptors.isEmpty()) return null

        val superDeclarations = ArrayList<NavigatablePsiElement>()
        for (overriddenMember in overriddenDescriptors) {
            val declarations = DescriptorToSourceUtilsIde.getAllDeclarations(element.project, overriddenMember)
            superDeclarations += declarations.filterIsInstance<NavigatablePsiElement>()
        }

        val elementName = elementDescriptor!!.name
        return NavigationPopupDescriptor(
            superDeclarations,
            KotlinBundle.message("overridden.marker.overrides.choose.implementation.title", elementName),
            KotlinBundle.message("overridden.marker.overrides.choose.implementation.find.usages", elementName),
            KtFunctionPsiElementCellRenderer()
        )
    }
}

data class ResolveWithParentsResult(
    val descriptor: CallableMemberDescriptor?,
    val overriddenDescriptors: Collection<CallableMemberDescriptor>
)

fun resolveDeclarationWithParents(element: KtDeclaration): ResolveWithParentsResult {
    val descriptor = if (element is KtParameter)
        element.propertyDescriptor
    else
        element.resolveToDescriptorIfAny()

    if (descriptor !is CallableMemberDescriptor) return ResolveWithParentsResult(null, listOf())

    return ResolveWithParentsResult(descriptor, descriptor.getDirectlyOverriddenDeclarations())
}
