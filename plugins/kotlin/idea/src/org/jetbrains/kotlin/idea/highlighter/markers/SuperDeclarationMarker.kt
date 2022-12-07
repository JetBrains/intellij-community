// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.impl.GutterTooltipBuilder
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.codeInsight.KtFunctionPsiElementCellRenderer
import org.jetbrains.kotlin.idea.core.getDirectlyOverriddenDeclarations
import org.jetbrains.kotlin.idea.search.usagesSearch.propertyDescriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference

object SuperDeclarationMarkerTooltip : Function<PsiElement, String> {
    override fun `fun`(element: PsiElement): String? {
        val ktDeclaration = element.getParentOfType<KtDeclaration>(false) ?: return null
        val (elementDescriptor, overriddenDescriptors) = resolveDeclarationWithParents(ktDeclaration)
        if (overriddenDescriptors.isEmpty()) return ""

        val isAbstract = elementDescriptor!!.modality == Modality.ABSTRACT

        val project = ktDeclaration.project

        val abstracts = hashSetOf<PsiElement>()
        val supers = overriddenDescriptors.mapNotNull {
            val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(
                project,
                it
            )
            if (declaration != null && it.modality == Modality.ABSTRACT) {
                abstracts.add(declaration)
            }
            declaration
        }

        val divider = GutterTooltipBuilder.getElementDivider(false, false, overriddenDescriptors.size)
        val reference = AtomicReference("")

        return KotlinGutterTooltipHelper.buildTooltipText(
            supers,
            { superMethod: PsiElement? ->
                val key =
                    if (abstracts.contains(superMethod) && !isAbstract) {
                        if (superMethod is KtProperty || superMethod is KtParameter) "tooltip.implements.property" else "tooltip.implements.function"
                    } else {
                        if (superMethod is KtProperty || superMethod is KtParameter) "tooltip.overrides.property" else "tooltip.overrides.function"
                    }
                reference.getAndSet(divider) + KotlinBundle.message(key) + " "
            },
            { true },
            IdeActions.ACTION_GOTO_SUPER
        )
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
