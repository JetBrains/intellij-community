// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.overrides

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.roots.ui.util.CompositeAppearance
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.createSmartPointer
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isJavaSourceOrLibrary
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.findCallableMemberBySignature
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.parents
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

    context(KaSession)
    private fun resolveToSymbol(psiElement: PsiElement): KaSymbol? {
        return when (psiElement) {
            is KtNamedDeclaration -> psiElement.symbol
            is PsiClass -> psiElement.namedClassSymbol
            is PsiMember -> psiElement.callableSymbol
            else -> null
        }
    }

    context(KaSession)
    private fun getBaseSymbol() = baseElement.element?.let { e ->
        val symbol = resolveToSymbol(e)
        (symbol as? KaValueParameterSymbol)?.generatedPrimaryConstructorProperty ?: symbol
    } as? KaCallableSymbol

    context(KaSession)
    private fun getCurrentClassSymbol() = psiElement?.let { resolveToSymbol(it) } as? KaClassSymbol

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun getCurrentSymbol(): KaCallableSymbol? {
        val classSymbol = getCurrentClassSymbol() ?: return null
        val baseSymbol = getBaseSymbol() ?: return null
        val baseClassSymbol = baseSymbol.containingDeclaration as? KaClassSymbol ?: return null
        val substitution = createInheritanceTypeSubstitutor(classSymbol, baseClassSymbol) ?: return null
        val callableSignature = baseSymbol.substitute(substitution)

        return classSymbol.findCallableMemberBySignature(callableSignature)
    }

    internal fun calculateState(): Icon? {
        val element = psiElement ?: return null
        val module = element.getKaModule(project, useSiteModule = null)
        return analyze(module) {
            val classSymbol = getCurrentClassSymbol() ?: return@analyze null
            val callableSymbol = getCurrentSymbol() ?: return@analyze AllIcons.Hierarchy.MethodNotDefined

            if (callableSymbol.origin.isJavaSourceOrLibrary() || callableSymbol.origin == KaSymbolOrigin.JAVA_SYNTHETIC_PROPERTY ||
                callableSymbol.origin == KaSymbolOrigin.LIBRARY || callableSymbol.origin == KaSymbolOrigin.SOURCE
            ) {
                if (callableSymbol.modality == KaSymbolModality.ABSTRACT) return@analyze null
                return@analyze AllIcons.Hierarchy.MethodDefined
            }

            val isAbstractClass = classSymbol.modality == KaSymbolModality.ABSTRACT
            val hasBaseImplementation =
                callableSymbol.allOverriddenSymbols.any { it.modality != KaSymbolModality.ABSTRACT }
            if (isAbstractClass || hasBaseImplementation) AllIcons.Hierarchy.MethodNotDefined else AllIcons.Hierarchy.ShouldDefineMethod
        }
    }

    override fun update(): Boolean {
        var flags = Iconable.ICON_FLAG_VISIBILITY
        if (isMarkReadOnly) {
            flags = flags or Iconable.ICON_FLAG_READ_STATUS
        }

        var changes = super.update()

        val classPsi = psiElement
        if (classPsi == null) {
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
            @NlsSafe val classDescriptorAsString = (classPsi as PsiNamedElement).name
            addText(classDescriptorAsString, classNameAttributes)
            classPsi.parents.filter { it is PsiNamedElement }.forEach { parent ->
                when (parent) {
                    is KtClass, is KtCallableDeclaration, is PsiMember -> {
                        val name = (parent as? PsiNamedElement)?.name ?: return@forEach
                        addText(KotlinBundle.message("hierarchy.text.in", name), classNameAttributes)
                        if (parent is KtFunction || parent is PsiMethod) {
                            addText("()", classNameAttributes)
                        }
                    }

                    is KtFile -> {
                        @NlsSafe val parentDescriptorAsString = parent.packageDirective?.qualifiedName ?: ""
                        addText("  ($parentDescriptorAsString)", getPackageNameAttributes())
                        return@forEach
                    }

                    is PsiClassOwner -> {
                        @NlsSafe val parentDescriptorAsString = parent.packageName
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