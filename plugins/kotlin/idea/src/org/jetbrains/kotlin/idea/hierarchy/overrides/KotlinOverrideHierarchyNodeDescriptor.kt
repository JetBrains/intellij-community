// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.hierarchy.overrides

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.roots.ui.util.CompositeAppearance
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMemberDescriptor
import org.jetbrains.kotlin.idea.util.getTypeSubstitution
import org.jetbrains.kotlin.idea.util.substitute
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.util.findCallableMemberBySignature
import java.awt.Font
import javax.swing.Icon

class KotlinOverrideHierarchyNodeDescriptor(
    parentNode: HierarchyNodeDescriptor?,
    klass: PsiElement,
    baseElement: PsiElement
) : HierarchyNodeDescriptor(klass.project, parentNode, klass, parentNode == null) {
    private val baseElement = baseElement.createSmartPointer()

    private var rawIcon: Icon? = null
    private var stateIcon: Icon? = null

    private fun resolveToDescriptor(psiElement: PsiElement): DeclarationDescriptor? {
        return when (psiElement) {
            is KtNamedDeclaration -> psiElement.unsafeResolveToDescriptor()
            is PsiMember -> psiElement.getJavaMemberDescriptor()
            else -> null
        }
    }

    private fun getBaseDescriptor() = baseElement.element?.let { resolveToDescriptor(it) } as? CallableMemberDescriptor

    private fun getCurrentClassDescriptor() = psiElement?.let { resolveToDescriptor(it) } as? ClassDescriptor

    private fun getCurrentDescriptor(): CallableMemberDescriptor? {
        val classDescriptor = getCurrentClassDescriptor() ?: return null
        val baseDescriptor = getBaseDescriptor() ?: return null
        val baseClassDescriptor = baseDescriptor.containingDeclaration as? ClassDescriptor ?: return null
        val substitution = getTypeSubstitution(baseClassDescriptor.defaultType, classDescriptor.defaultType) ?: return null
        return classDescriptor.findCallableMemberBySignature(baseDescriptor.substitute(substitution) as CallableMemberDescriptor)
    }

    internal fun calculateState(): Icon? {
        val classDescriptor = getCurrentClassDescriptor() ?: return null
        val callableDescriptor = getCurrentDescriptor() ?: return AllIcons.Hierarchy.MethodNotDefined

        if (callableDescriptor.kind == CallableMemberDescriptor.Kind.DECLARATION) {
            if (callableDescriptor.modality == Modality.ABSTRACT) return null
            return AllIcons.Hierarchy.MethodDefined
        }

        val isAbstractClass = classDescriptor.modality == Modality.ABSTRACT
        val hasBaseImplementation =
            DescriptorUtils.getAllOverriddenDeclarations(callableDescriptor).any { it.modality != Modality.ABSTRACT }
        return if (isAbstractClass || hasBaseImplementation) AllIcons.Hierarchy.MethodNotDefined else AllIcons.Hierarchy.ShouldDefineMethod
    }

    override fun update(): Boolean {
        var flags = Iconable.ICON_FLAG_VISIBILITY
        if (isMarkReadOnly) {
            flags = flags or Iconable.ICON_FLAG_READ_STATUS
        }

        var changes = super.update()

        val classPsi = psiElement
        val classDescriptor = getCurrentClassDescriptor()
        if (classPsi == null || classDescriptor == null) {
            val invalidPrefix = IdeBundle.message("node.hierarchy.invalid")
            if (!myHighlightedText.text.startsWith(invalidPrefix)) {
                myHighlightedText.beginning.addText(invalidPrefix, getInvalidPrefixAttributes())
            }
            return true
        }

        val newRawIcon = classPsi.getIcon(flags)
        val newStateIcon = calculateState()

        if (changes || newRawIcon !== rawIcon || newStateIcon !== stateIcon) {
            changes = true

            rawIcon = newRawIcon
            stateIcon = newStateIcon

            var newIcon = rawIcon

            if (myIsBase) {
                val icon = LayeredIcon(2)
                icon.setIcon(newIcon, 0)
                icon.setIcon(AllIcons.Actions.Forward, 1, -AllIcons.Actions.Forward.iconWidth / 2, 0)
                newIcon = icon
            }

            if (stateIcon != null) {
                newIcon = RowIcon(stateIcon, newIcon)
            }

            icon = newIcon
        }

        val oldText = myHighlightedText

        myHighlightedText = CompositeAppearance()
        var classNameAttributes: TextAttributes? = null
        if (myColor != null) {
            classNameAttributes = TextAttributes(myColor, null, null, null, Font.PLAIN)
        }

        with(myHighlightedText.ending) {
            @NlsSafe val classDescriptorAsString = classDescriptor.name.asString()
            addText(classDescriptorAsString, classNameAttributes)
            classDescriptor.parents.forEach { parentDescriptor ->
                when (parentDescriptor) {
                    is MemberDescriptor -> {
                        addText(KotlinBundle.message("hierarchy.text.in", parentDescriptor.name.asString()), classNameAttributes)
                        if (parentDescriptor is FunctionDescriptor) {
                            addText("()", classNameAttributes)
                        }
                    }
                    is PackageFragmentDescriptor -> {
                        @NlsSafe val parentDescriptorAsString = parentDescriptor.fqName.asString()
                        addText("  ($parentDescriptorAsString)", getPackageNameAttributes())
                        return@forEach
                    }
                }
            }
        }

        myName = myHighlightedText.text

        if (!Comparing.equal(myHighlightedText, oldText)) {
            changes = true
        }

        return changes
    }
}