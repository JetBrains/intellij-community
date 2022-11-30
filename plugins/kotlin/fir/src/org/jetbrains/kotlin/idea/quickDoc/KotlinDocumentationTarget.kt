// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickDoc

import com.google.common.html.HtmlEscapers
import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.lang.documentation.DocumentationResult
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KtParameterDefaultValueRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KtRendererBodyMemberScopeProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KtPropertyAccessorsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.classifiers.KtSingleTypeParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.kdoc.*
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.appendHighlighted
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.generateJavadoc
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.renderKDoc
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

internal class KotlinDocumentationTarget(val element: PsiElement, private val originalElement: PsiElement?) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val elementPtr = element.createSmartPointer()
        val originalElementPtr = originalElement?.createSmartPointer()
        return Pointer {
            val element = elementPtr.dereference() ?: return@Pointer null
            KotlinDocumentationTarget(element, originalElementPtr?.dereference())
        }
    }

    override fun presentation(): TargetPresentation {
        return targetPresentation(element)
    }

    override fun computeDocumentationHint(): String? {
        return computeLocalDocumentation(element, originalElement, true)
    }

    override val navigatable: Navigatable?
        get() = element as? Navigatable

    override fun computeDocumentation(): DocumentationResult? {
        @Suppress("HardCodedStringLiteral") val html =
            computeLocalDocumentation(element, originalElement, false) ?: return null
        return DocumentationResult.documentation(html)
    }

    companion object {
        internal val RENDERING_OPTIONS = KtDeclarationRendererForSource.WITH_SHORT_NAMES.with {
            returnTypeFilter = KtCallableReturnTypeFilter.ALWAYS
            propertyAccessorsRenderer = KtPropertyAccessorsRenderer.NONE
            bodyMemberScopeProvider = KtRendererBodyMemberScopeProvider.NONE
            singleTypeParameterRenderer = KtSingleTypeParameterSymbolRenderer.WITH_COMMA_SEPARATED_BOUNDS
            parameterDefaultValueRenderer = KtParameterDefaultValueRenderer.THREE_DOTS
        }
    }
}

private fun computeLocalDocumentation(element: PsiElement, originalElement: PsiElement?, quickNavigation: Boolean): String? {
    when {
      element is KtFunctionLiteral -> {
          val itElement = findElementWithText(originalElement, "it")
          val itReference = itElement?.getParentOfType<KtNameReferenceExpression>(false)
          if (itReference != null) {
              return buildString {
                  renderKotlinDeclaration(
                      element,
                      quickNavigation,
                      symbolFinder = { (it as? KtFunctionLikeSymbol)?.valueParameters?.firstOrNull() })
              }
          }
      }
      element is KtTypeReference -> {
          val declaration = element.parent
          if (declaration is KtCallableDeclaration &&
              declaration.receiverTypeReference == element &&
              findElementWithText(originalElement, "this") != null
          ) {
              return computeLocalDocumentation(declaration, originalElement, quickNavigation)
          }
      }
      element is KtClass && element.isEnum() -> {
          return buildString {
              renderEnumSpecialFunction(originalElement, element, quickNavigation)
          }
      }
      element is KtEnumEntry && !quickNavigation -> {
          val ordinal = element.containingClassOrObject?.body?.run { getChildrenOfType<KtEnumEntry>().indexOf(element) }

          val project = element.project
          return buildString {
              renderKotlinDeclaration(element, false) {
                  definition {
                      it.inherit()
                      ordinal?.let {
                          append("<br>")
                          appendHighlighted("// ", project) { asInfo }
                          appendHighlighted(KotlinBundle.message("quick.doc.text.enum.ordinal", ordinal), project) { asInfo }
                      }
                  }
              }
          }
      }
      element is KtDeclaration -> {
          return buildString {
              renderKotlinDeclaration(element, quickNavigation)
          }
      }
      element is KtLightDeclaration<*, *> -> {
          val origin = element.kotlinOrigin ?: return null
          return computeLocalDocumentation(origin, element, quickNavigation)
      }
      element is KtValueArgumentList -> {
          val referenceExpression = element.prevSibling as? KtSimpleNameExpression ?: return null
          val calledElement = referenceExpression.mainReference.resolve()
          if (calledElement is KtNamedFunction || calledElement is KtConstructor<*>) {
              // In case of Kotlin function or constructor
              return computeLocalDocumentation(calledElement as KtExpression, element, quickNavigation)
          }
          else if (calledElement is PsiMethod) {
              return JavaDocumentationProvider().generateDoc(calledElement, referenceExpression)
          }
      }
      element is KtCallExpression -> {
          val calledElement = element.referenceExpression()?.mainReference?.resolve() ?: return null
          return computeLocalDocumentation(calledElement, originalElement, quickNavigation)
      }
      element.isModifier() -> {
          when (element.text) {
              KtTokens.LATEINIT_KEYWORD.value -> return KotlinBundle.message("quick.doc.text.lateinit")
              KtTokens.TAILREC_KEYWORD.value -> return KotlinBundle.message("quick.doc.text.tailrec")
          }
      }
    }
    return null
}

private fun getContainerInfo(ktDeclaration: KtDeclaration): HtmlChunk {
    analyze(ktDeclaration) {
        val containingSymbol = ktDeclaration.getSymbol().getContainingSymbol()
        val fqName = (containingSymbol as? KtClassLikeSymbol)?.classIdIfNonLocal?.asFqNameString()
            ?: (ktDeclaration.containingFile as? KtFile)?.packageFqName?.takeIf { !it.isRoot }?.asString()
        val fqNameSection = fqName
            ?.let {
                @Nls val link = StringBuilder()
                val highlighted =
                    if (DocumentationSettings.isSemanticHighlightingOfLinksEnabled()) KDocRenderer.highlight(it, ktDeclaration.project) { asClassName }
                    else it
                DocumentationManagerUtil.createHyperlink(link, it, highlighted, false, false)
                HtmlChunk.fragment(
                    HtmlChunk.icon("class", KotlinIcons.CLASS),
                    HtmlChunk.nbsp(),
                    HtmlChunk.raw(link.toString()),
                    HtmlChunk.br()
                )
            } ?: HtmlChunk.empty()
        val fileNameSection = ktDeclaration
            .containingFile
            ?.name
            ?.takeIf { containingSymbol == null }
            ?.let {
                HtmlChunk.fragment(
                    HtmlChunk.icon("file", KotlinIcons.FILE),
                    HtmlChunk.nbsp(),
                    HtmlChunk.text(it),
                    HtmlChunk.br()
                )
            }
            ?: HtmlChunk.empty()
        return HtmlChunk.fragment(fqNameSection, fileNameSection)
    }
}

private fun @receiver:Nls StringBuilder.renderEnumSpecialFunction(
    originalElement: PsiElement?,
    element: KtClass,
    quickNavigation: Boolean
) {
    val referenceExpression = originalElement?.getNonStrictParentOfType<KtReferenceExpression>()
    if (referenceExpression != null) {
        // When caret on special enum function (e.g. SomeEnum.values<caret>())
        // element is not an KtReferenceExpression, but KtClass of enum
        // so reference extracted from originalElement
        analyze(referenceExpression) {
            val symbol = referenceExpression.mainReference.resolveToSymbol() as? KtNamedSymbol
            val name = symbol?.name?.asString()
            if (name != null) {
                val containingClass = symbol.getContainingSymbol() as? KtClassOrObjectSymbol
                val superClasses = containingClass?.superTypes?.mapNotNull { t -> t.expandedClassSymbol }
                val kdoc = superClasses?.firstNotNullOfOrNull { superClass ->
                    superClass.psi?.navigationElement?.findDescendantOfType<KDoc> { doc ->
                        doc.getChildrenOfType<KDocSection>().any { it.findTagByName(name) != null }
                    }
                }

                renderKotlinDeclaration(element, false) {
                    if (!quickNavigation && kdoc != null) {
                        description {
                            renderKDoc(kdoc.getDefaultSection())
                        }
                    }
                }
                return
            }
        }
    }
    renderKotlinDeclaration(element, quickNavigation)
}

private fun findElementWithText(element: PsiElement?, text: String): PsiElement? {
    return when {
        element == null -> null
        element.text == text -> element
        element.prevLeaf()?.text == text -> element.prevLeaf()
        else -> null
    }
}

internal fun PsiElement?.isModifier() =
    this != null && parent is KtModifierList && KtTokens.MODIFIER_KEYWORDS_ARRAY.firstOrNull { it.value == text } != null

private fun @receiver:Nls StringBuilder.renderKotlinDeclaration(
    declaration: KtDeclaration,
    onlyDefinition: Boolean,
    symbolFinder: KtAnalysisSession.(KtSymbol) -> KtSymbol? = { it },
    preBuild: KDocTemplate.() -> Unit = {}
) {
    analyze(declaration) {
        val symbol = symbolFinder(declaration.getSymbol())
        if (symbol is KtDeclarationSymbol) {
            insert(KDocTemplate()) {
                definition {
                    append(HtmlEscapers.htmlEscaper().escape(symbol.render(KotlinDocumentationTarget.RENDERING_OPTIONS)))
                }

                if (!onlyDefinition) {
                    description {
                        renderKDoc(symbol, this)
                    }
                }
                getContainerInfo(declaration).toString().takeIf { it.isNotBlank() }?.let { info ->
                    containerInfo {
                        append(info)
                    }
                }
                preBuild()
            }
        }
    }
}

private fun KtAnalysisSession.renderKDoc(
    symbol: KtSymbol,
    stringBuilder: StringBuilder,
) {
    val declaration = symbol.psi as? KtElement
    val kDoc = findKDoc(symbol)
    if (kDoc != null) {
        stringBuilder.renderKDoc(kDoc.contentTag, kDoc.sections)
        return
    }
    if (declaration is KtSecondaryConstructor) {
        declaration.getContainingClassOrObject().findKDoc()?.let {
            stringBuilder.renderKDoc(it.contentTag, it.sections)
        }
    } else if (declaration is KtFunction && 
        symbol is KtCallableSymbol && 
        symbol.getAllOverriddenSymbols().any { it.psi is PsiMethod }) {
        LightClassUtil.getLightClassMethod(declaration)?.let {
            stringBuilder.insert(KDocTemplate.DescriptionBodyTemplate.FromJava()) {
                body = generateJavadoc(it)
            }
        }
    }
}

private fun KtAnalysisSession.findKDoc(symbol: KtSymbol): KDocContent? {
    val ktElement = symbol.psi as? KtElement
    ktElement?.findKDoc()?.let {
        return it
    }

    if (symbol is KtCallableSymbol) {
        symbol.getAllOverriddenSymbols().forEach { overrider ->
            findKDoc(overrider)?.let {
                return it
            }
        }
    }
    return null
}