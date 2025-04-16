// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.model.Pointer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.createSmartPointer
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.kdoc.*
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.appendHighlighted
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.createHighlightingManager
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.generateJavadoc
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.renderKDoc
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
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

    override fun computePresentation(): TargetPresentation {
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
}

private fun computeLocalDocumentation(element: PsiElement, originalElement: PsiElement?, quickNavigation: Boolean): String? {
    when {
      element is PsiWhiteSpace -> {
          val itElement = findElementWithText(originalElement, StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier)
          val itReference = itElement?.getParentOfType<KtNameReferenceExpression>(false)
          if (itReference != null) {
              return buildString {
                  renderKotlinDeclaration(
                      itReference.mainReference.resolve() as KtFunctionLiteral,
                      quickNavigation,
                      symbolFinder = { (it as? KaFunctionSymbol)?.valueParameters?.firstOrNull() })
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
      element is KtLightMethod && element.kotlinOrigin is KtClass && (element.kotlinOrigin as KtClass).isEnum() -> {
          return buildString {
              renderEnumSpecialFunction(originalElement, element.kotlinOrigin as KtClass, quickNavigation)
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

context(KaSession)
private fun getContainerInfo(ktDeclaration: KtDeclaration): HtmlChunk {
    val containingSymbol = ktDeclaration.symbol.containingDeclaration
    val fqName = (containingSymbol as? KaClassLikeSymbol)?.classId?.asFqNameString()
        ?: (ktDeclaration.containingFile as? KtFile)?.packageFqName?.takeIf { !it.isRoot }?.asString()

    val fqNameSection = fqName?.let {
        @Nls val link = StringBuilder()
        val highlighted = if (DocumentationSettings.isSemanticHighlightingOfLinksEnabled()) {
            KDocRenderer.highlight(it, ktDeclaration.project) { asClassName }
        } else {
            it
        }

        DocumentationManagerUtil.createHyperlink(link, it, highlighted, false)
        HtmlChunk.fragment(
            HtmlChunk.tag("icon").attr(
                "src",
                if (ktDeclaration.isTopLevelKtOrJavaMember()) {
                    "AllIcons.Nodes.Package"
                } else {
                    "KotlinBaseResourcesIcons.ClassKotlin"
                }
            ),
            HtmlChunk.nbsp(),
            HtmlChunk.raw(link.toString()),
            HtmlChunk.br()
        )
    } ?: HtmlChunk.empty()

    val fileNameSection = ktDeclaration.navigationElement.containingFile
        ?.name
        ?.takeIf { ktDeclaration.isTopLevelKtOrJavaMember() }
        ?.let {
            HtmlChunk.fragment(
                HtmlChunk.tag("icon").attr("src", "KotlinBaseResourcesIcons.Kotlin_file"),
                HtmlChunk.nbsp(),
                HtmlChunk.text(it),
                HtmlChunk.br()
            )
        }
        ?: HtmlChunk.empty()

    return HtmlChunk.fragment(fqNameSection, fileNameSection)
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
            val symbol = referenceExpression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol?.symbol as? KaNamedSymbol
            val name = symbol?.name?.asString()
            if (name != null && symbol is KaDeclarationSymbol) {
                renderEnumSpecialSymbol(this, symbol, name, element, quickNavigation)
                return
            }
        }
    } else {
        val psiReferenceExpression =
            originalElement?.takeIf { it.language != KotlinLanguage.INSTANCE }?.getNonStrictParentOfType<PsiReferenceExpression>()
        if (psiReferenceExpression != null) {
            val psiMember = psiReferenceExpression.resolve() as? PsiMember
            val memberName = psiMember?.name
            if (psiMember != null && memberName != null) {
                analyze(element) {
                    val symbol = element.classSymbol ?: return@analyze
                    // TODO: Replace with `psiMember.callableSymbol` when KT-76834 is fixed
                    val callableSymbol = symbol.staticMemberScope.callables {
                        val name = it.asString()
                        name == memberName || JvmAbi.getterName(name) == memberName
                    }.firstOrNull()

                    if (callableSymbol is KaDeclarationSymbol) {
                        renderEnumSpecialSymbol(this, callableSymbol, memberName, element, quickNavigation)
                        return
                    }
                }
            }
        }
    }
    renderKotlinDeclaration(element, quickNavigation)
}

private fun StringBuilder.renderEnumSpecialSymbol(
    session: KaSession,
    symbol: KaDeclarationSymbol,
    name: String,
    element: KtClass,
    quickNavigation: Boolean
) {
    with(session) {
        val containingClass = symbol.containingDeclaration as? KaClassSymbol
        val superClasses = containingClass?.superTypes?.mapNotNull { t -> t.expandedSymbol }
        val kdoc = superClasses?.firstNotNullOfOrNull { superClass ->
            val navigationElement = superClass.psi?.navigationElement
            if (navigationElement is KtElement && navigationElement.containingKtFile.isCompiled) {
                null //no need to search documentation in decompiled code
            } else {
                navigationElement?.findDescendantOfType<KDoc> { doc ->
                    doc.getChildrenOfType<KDocSection>().any { it.findTagByName(name) != null }
                }
            }
        }

        renderKotlinSymbol(symbol, element, false, false) {
            if (!quickNavigation && kdoc != null) {
                description {
                    renderKDoc(kdoc.getDefaultSection())
                }
            }
        }
    }
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
    symbolFinder: KaSession.(KaSymbol) -> KaSymbol? = { it },
    preBuild: KDocTemplate.() -> Unit = {}
) {
    analyze(declaration) {
        // it's not possible to create symbol for function type parameter, so we need to process this case separately
        // see KTIJ-22404 and KTIJ-25653
        if (declaration is KtParameter && declaration.isFunctionTypeParameter) {
            val definition = KotlinIdeDeclarationRenderer(createHighlightingManager(declaration.project)).renderFunctionTypeParameter(declaration) ?: return

            insert(KDocTemplate()) {
                definition {
                    append(definition)
                }
            }
            return
        }

        val symbol = symbolFinder(declaration.symbol)
        if (symbol !is KaDeclarationSymbol) return

        renderKotlinSymbol(symbol, declaration, onlyDefinition, true, preBuild)
    }
}

context(KaSession)
private fun renderKDoc(
    symbol: KaSymbol,
    stringBuilder: StringBuilder,
) {
    val declaration = symbol.psi?.navigationElement as? KtElement
    val kDoc = findKDoc(symbol)
    if (kDoc != null) {
        stringBuilder.renderKDoc(kDoc.contentTag, kDoc.sections)
        return
    }
    if (declaration is KtSecondaryConstructor) {
        declaration.getContainingClassOrObject().findKDocByPsi()?.let {
            stringBuilder.renderKDoc(it.contentTag, it.sections)
        }
    } else if (declaration is KtFunction &&
        symbol is KaCallableSymbol &&
        symbol.allOverriddenSymbols.any { it.psi is PsiMethod }) {
        LightClassUtil.getLightClassMethod(declaration)?.let {
            stringBuilder.insert(KDocTemplate.DescriptionBodyTemplate.FromJava()) {
                body = generateJavadoc(it)
            }
        }
    }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun findKDoc(symbol: KaSymbol): KDocContent? {
    val ktElement = symbol.psi?.navigationElement as? KtElement
    ktElement?.findKDocByPsi()?.let {
        return it
    }

    if (symbol is KaCallableSymbol) {
        symbol.allOverriddenSymbols.forEach { overrider ->
            findKDoc(overrider)?.let {
                return it
            }
        }
    }

    if (symbol is KaValueParameterSymbol) {
        val containingSymbol = symbol.containingDeclaration as? KaNamedFunctionSymbol
        if (containingSymbol != null) {
            val idx = containingSymbol.valueParameters.indexOf(symbol)
            containingSymbol.getExpectsForActual().filterIsInstance<KaNamedFunctionSymbol>().mapNotNull { expectFunction ->
                findKDoc(expectFunction.valueParameters[idx])
            }.firstOrNull()?.let { return it }
        }
    }

    return (symbol as? KaDeclarationSymbol)?.getExpectsForActual()?.mapNotNull { declarationSymbol -> findKDoc(declarationSymbol) }?.firstOrNull()
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun @receiver:Nls StringBuilder.renderKotlinSymbol(symbol: KaDeclarationSymbol,
                                                           declaration: KtDeclaration,
                                                           onlyDefinition: Boolean,
                                                           passContainerInfo: Boolean = true,
                                                           preBuild: KDocTemplate.() -> Unit = {}) {
    insert(KDocTemplate()) {
        definition {
            append(symbol.render(KotlinIdeDeclarationRenderer(createHighlightingManager(declaration.project), symbol).renderer))
        }

        if (!onlyDefinition) {
            description {
                renderKDoc(symbol, this)
            }
        }

        if (passContainerInfo) {
            getContainerInfo(declaration).toString().takeIf { it.isNotBlank() }?.let { info ->
                containerInfo {
                    append(info)
                }
            }
        }
        preBuild()
    }
}