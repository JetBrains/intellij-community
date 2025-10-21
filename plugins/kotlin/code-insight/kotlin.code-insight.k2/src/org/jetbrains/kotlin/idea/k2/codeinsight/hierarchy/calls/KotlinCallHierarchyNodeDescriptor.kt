// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy.calls

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.call.CallHierarchyNodeDescriptor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.roots.ui.util.CompositeAppearance
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.ui.LayeredIcon
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance
import java.awt.Font

class KotlinCallHierarchyNodeDescriptor(
    parentDescriptor: HierarchyNodeDescriptor?,
    element: KtElement,
    isBase: Boolean,
    navigateToReference: Boolean
) : HierarchyNodeDescriptor(element.project, parentDescriptor, element, isBase),
    Navigatable {
    private var usageCount = 1
    private val references: MutableSet<PsiReference> = HashSet()
    private val javaDelegate = CallHierarchyNodeDescriptor(myProject, null, element, isBase, navigateToReference)

    fun incrementUsageCount() {
        usageCount++
        javaDelegate.incrementUsageCount()
    }

    fun addReference(reference: PsiReference) {
        references.add(reference)
        javaDelegate.addReference(reference)
    }

    override fun isValid(): Boolean {
        val myElement = psiElement
        return myElement != null && myElement.isValid
    }

    override fun update(): Boolean {
        val oldText = myHighlightedText
        val oldIcon = icon

        val flags: Int = if (isMarkReadOnly) {
            Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS
        } else {
            Iconable.ICON_FLAG_VISIBILITY
        }

        var changes = super.update()

        val elementText = (psiElement as? KtElement)?.let { analyze(it) { renderElement(it) } }
        if (elementText == null) {
            val invalidPrefix = IdeBundle.message("node.hierarchy.invalid")
            if (!myHighlightedText.text.startsWith(invalidPrefix)) {
                myHighlightedText.beginning.addText(invalidPrefix, getInvalidPrefixAttributes())
            }
            return true
        }

        val targetElement = psiElement
        val elementIcon = targetElement!!.getIcon(flags)

        icon = if (changes && myIsBase) {
            LayeredIcon(2).apply {
                setIcon(elementIcon, 0)
                setIcon(AllIcons.General.Modified, 1, -AllIcons.General.Modified.iconWidth / 2, 0)
            }
        } else {
            elementIcon
        }

        val mainTextAttributes: TextAttributes? = if (myColor != null) {
            TextAttributes(myColor, null, null, null, Font.PLAIN)
        } else {
            null
        }

        myHighlightedText = CompositeAppearance()
        myHighlightedText.ending.addText(elementText, mainTextAttributes)
        if (usageCount > 1) {
            myHighlightedText.ending.addText(
                IdeBundle.message("node.call.hierarchy.N.usages", usageCount),
                getUsageCountPrefixAttributes(),
            )
        }

        @NlsSafe
        val packageName = KtPsiUtil.getPackageName(targetElement as KtElement) ?: ""

        myHighlightedText.ending.addText("  ($packageName)", getPackageNameAttributes())
        myName = myHighlightedText.text

        if (!(Comparing.equal(myHighlightedText, oldText) && Comparing.equal(icon, oldIcon))) {
            changes = true
        }

        return changes
    }

    override fun navigate(requestFocus: Boolean) {
        javaDelegate.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean {
        return javaDelegate.canNavigate()
    }

    override fun canNavigateToSource(): Boolean {
        return javaDelegate.canNavigateToSource()
    }

    companion object {
        context(_: KaSession)
        @NlsSafe
        private fun renderElement(element: PsiElement?): String? {
            when (element) {
                is KtFile -> {
                    return element.name
                }
                !is KtNamedDeclaration -> {
                    return null
                }
                else -> {
                    var declarationSymbol = element.symbol
                    val elementText: String?
                    when (element) {
                        is KtClassOrObject -> {
                            when {
                                element is KtObjectDeclaration && element.isCompanion() -> {
                                    val containingDescriptor = declarationSymbol.containingDeclaration
                                    if (containingDescriptor !is KaClassSymbol) return null
                                    declarationSymbol = containingDescriptor
                                    elementText = renderClassOrObject(declarationSymbol)
                                }
                                element is KtEnumEntry -> {
                                    elementText = element.name
                                }
                                else -> {
                                    elementText = if (element.name != null) {
                                        renderClassOrObject(declarationSymbol as KaClassSymbol)
                                    } else {
                                        KotlinBundle.message("hierarchy.text.anonymous")
                                    }
                                }
                            }
                        }
                        is KtNamedFunction, is KtConstructor<*> -> {
                            if (declarationSymbol !is KaFunctionSymbol) return null
                            elementText = renderNamedFunction(declarationSymbol)
                        }
                        is KtProperty -> {
                            elementText = element.name
                        }
                        else -> return null
                    }

                    if (elementText == null) return null
                    var containerText: String? = null
                    var containerDescriptor = declarationSymbol.containingDeclaration
                    while (containerDescriptor != null) {
                        if (containerDescriptor is KaPackageSymbol) {
                            break
                        }
                        val name = (containerDescriptor as? KaNamedSymbol)?.name?.takeUnless { containerDescriptor is KaVariableSymbol }
                        if (name != null && !name.isSpecial) {
                            val identifier = name.identifier
                            containerText = if (containerText != null) "$identifier.$containerText" else identifier
                        }
                        containerDescriptor = containerDescriptor.containingDeclaration
                    }
                    return if (containerText != null) "$containerText.$elementText" else elementText
                }
            }
        }

        context(_: KaSession)
        @OptIn(KaExperimentalApi::class)
        fun renderNamedFunction(symbol: KaFunctionSymbol): String? {
            val name = ((symbol as? KaNamedFunctionSymbol)?.name ?: ((symbol as? KaConstructorSymbol)?.containingDeclaration as? KaClassSymbol)?.name)?.asString() ?: return null
            val paramTypes =
                StringUtil.join(
                    symbol.valueParameters,
                    {
                        it.returnType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT)
                    },
                    ", "
                )
            return "$name($paramTypes)"
        }

        private fun renderClassOrObject(descriptor: KaClassSymbol): String {
            return descriptor.name?.asString() ?: KotlinBundle.message("hierarchy.text.anonymous")
        }
    }
}