// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.IconManager
import com.intellij.util.PlatformIcons
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaParameterDefaultValueRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererModalityModifierProvider
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererOtherModifiersProvider
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.base.analysis.api.utils.defaultValue
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.predictImplicitModality
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.core.TemplateKind
import org.jetbrains.kotlin.idea.core.getFunctionBodyTextFromTemplate
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddMemberToSupertypeFixFactory.MemberData
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.modalityModifier
import org.jetbrains.kotlin.types.Variance
import javax.swing.Icon

object AddMemberToSupertypeFixFactory {

  internal class MemberData(val signaturePreview: String, val sourceCode: String, val targetClass: KtClass)

  val addMemberToSupertypeFixFactory: KotlinQuickFixFactory<KaFirDiagnostic.NothingToOverride> = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.NothingToOverride ->
    val element = diagnostic.psi
    if (element !is KtProperty && element !is KtNamedFunction) return@IntentionBased emptyList()

    val candidateMembers = getCandidateMembers(element)
    if (candidateMembers.isEmpty()) {
      return@IntentionBased emptyList()
    }

    val action = when (element) {
      is KtProperty -> AddPropertyToSupertypeFix(element, candidateMembers)
      is KtNamedFunction -> AddFunctionToSupertypeFix(element, candidateMembers)
      else -> null
    }
    listOfNotNull(action)
  }

  context(KaSession)
  private fun getCandidateMembers(memberElement: KtCallableDeclaration): List<MemberData> {
    val callableSymbol = memberElement.symbol as? KaCallableSymbol ?: return emptyList()
    val containingClass = callableSymbol.containingDeclaration as? KaClassSymbol
                          ?: return emptyList()
    val classSymbols = containingClass.defaultType.allSupertypes.mapNotNull { type ->
      (type.symbol as? KaClassSymbol)?.takeIf {
        it.origin == KaSymbolOrigin.SOURCE && it.isVisible(memberElement)
      }
    }.toList()
    return classSymbols.mapNotNull { createMemberData(it, memberElement) }
  }

  context(KaSession)
  @OptIn(KaExperimentalApi::class)
  private fun createMemberData(classSymbol: KaClassSymbol, memberElement: KtCallableDeclaration): MemberData? {
    val project = memberElement.project
    val callableSymbol = memberElement.symbol
    val targetClass = classSymbol.psi as? KtClass ?: return null
    val signaturePreview = callableSymbol.render(KaDeclarationRendererForSource.WITH_SHORT_NAMES.render(classSymbol))
    var sourceCode = callableSymbol.render(KaDeclarationRendererForSource.WITH_QUALIFIED_NAMES.render(classSymbol))
    if (memberElement is KtNamedFunction) {
      if (classSymbol.classKind != KaClassKind.INTERFACE && classSymbol.modality != KaSymbolModality.ABSTRACT && classSymbol.modality != KaSymbolModality.SEALED) {
        val returnType = (callableSymbol as KaNamedFunctionSymbol).returnType
        sourceCode += if (!returnType.isUnitType) {
          val bodyText = getFunctionBodyTextFromTemplate(project, TemplateKind.FUNCTION, callableSymbol.name.asString(),
                                                         callableSymbol.returnType.render(position = Variance.OUT_VARIANCE),
                                                         classSymbol.importableFqName)
          "{\n$bodyText\n}"
        }
        else {
          "{}"
        }
      }
    }
    else {
      val initializer = (memberElement as? KtProperty)?.initializer
      if (classSymbol.classKind == KaClassKind.CLASS && classSymbol.modality == KaSymbolModality.OPEN && initializer != null) {
        sourceCode += " = ${initializer.text}"
      }
    }
    return MemberData(signaturePreview, sourceCode, targetClass)
  }

  context(KaSession)
  @OptIn(KaExperimentalApi::class)
  private fun KaDeclarationRenderer.render(targetClassSymbol: KaClassSymbol) = with {
    modifiersRenderer = modifiersRenderer.with {
      modalityProvider = object : KaRendererModalityModifierProvider {
        override fun getModalityModifier(
          analysisSession: KaSession,
          symbol: KaDeclarationSymbol,
        ): KtModifierKeywordToken? {
          if (symbol.containingSymbol is KaClassSymbol) { // do not provide modifiers for parameters
            return if (targetClassSymbol.modality == KaSymbolModality.SEALED || targetClassSymbol.modality == KaSymbolModality.ABSTRACT) KtTokens.ABSTRACT_KEYWORD
            else KtTokens.OPEN_KEYWORD
          }
          return null
        }
      }
      otherModifiersProvider = object : KaRendererOtherModifiersProvider {
        override fun getOtherModifiers(
          analysisSession: KaSession,
          symbol: KaDeclarationSymbol,
        ): List<KtModifierKeywordToken> = buildList {
          addAll(KaRendererOtherModifiersProvider.ALL.getOtherModifiers(analysisSession, symbol))
          remove(KtTokens.OVERRIDE_KEYWORD) // keep expect/actual even when not approriate
        }
      }
    }
    //annotations would be copied as is
    annotationRenderer = annotationRenderer.with {
      annotationFilter = KaRendererAnnotationsFilter.NONE
    }
    parameterDefaultValueRenderer = object : KaParameterDefaultValueRenderer {
      override fun renderDefaultValue(
        analysisSession: KaSession,
        symbol: KaValueParameterSymbol,
        printer: PrettyPrinter,
      ) {
        val defaultValue = symbol.defaultValue
        if (defaultValue != null) {
          printer.append(defaultValue.text)
        }
      }
    }
  }
}

internal abstract class AddMemberToSupertypeFix(
    element: KtCallableDeclaration,
    private val candidateMembers: List<MemberData>,
) : KotlinQuickFixAction<KtCallableDeclaration>(
  element), LowPriorityAction {

  init {
    assert(candidateMembers.isNotEmpty())
  }

  abstract val kind: String
  abstract val icon: Icon

  override fun getText(): String =
    candidateMembers.singleOrNull()?.let { actionName(it) } ?: KotlinBundle.message("fix.add.member.supertype.text", kind)

  override fun getFamilyName() = KotlinBundle.message("fix.add.member.supertype.family", kind)

  override fun startInWriteAction(): Boolean = false

  override fun invoke(project: Project, editor: Editor?, file: KtFile) {
    if (candidateMembers.size == 1 || editor == null || !editor.component.isShowing) {
      addMember(candidateMembers.first(), project)
    }
    else {
      JBPopupFactory.getInstance().createListPopup(createMemberPopup(project)).showInBestPositionFor(editor)
    }
  }

  private fun addMember(memberData: MemberData, project: Project) {
    project.executeWriteCommand(KotlinBundle.message("fix.add.member.supertype.progress", kind)) {
      element?.removeDefaultParameterValues()
      val classBody = memberData.targetClass.getOrCreateBody()
      val memberElement: KtCallableDeclaration = KtPsiFactory(project).createDeclaration(memberData.sourceCode)
      memberElement.copyAnnotationEntriesFrom(element)
      val insertedMemberElement = classBody.addBefore(memberElement, classBody.rBrace) as KtCallableDeclaration
      shortenReferences(insertedMemberElement)
      val modifierToken = insertedMemberElement.modalityModifier()?.node?.elementType as? KtModifierKeywordToken
                          ?: return@executeWriteCommand
      if (insertedMemberElement.predictImplicitModality() == modifierToken) {
        RemoveModifierFixBase(insertedMemberElement, modifierToken, true).invoke()
      }
    }
  }

  private fun KtCallableDeclaration.removeDefaultParameterValues() {
    valueParameters.forEach {
      it.defaultValue?.delete()
      it.equalsToken?.delete()
    }
  }

  private fun KtCallableDeclaration.copyAnnotationEntriesFrom(member: KtCallableDeclaration?) {
    member?.annotationEntries?.reversed()?.forEach { addAnnotationEntry(it) }
  }

  private fun createMemberPopup(project: Project): ListPopupStep<*> {
    return object : BaseListPopupStep<MemberData>(KotlinBundle.message("fix.add.member.supertype.choose.type"), candidateMembers) {
      override fun isAutoSelectionEnabled() = false

      override fun onChosen(selectedValue: MemberData, finalChoice: Boolean): PopupStep<*>? {
        if (finalChoice) {
          addMember(selectedValue, project)
        }
        return FINAL_CHOICE
      }

      override fun getIconFor(value: MemberData) = icon
      override fun getTextFor(value: MemberData) = actionName(value)
    }
  }

  @Nls
  private fun actionName(memberData: MemberData): String =
    KotlinBundle.message("fix.add.member.supertype.add.to", memberData.signaturePreview, memberData.targetClass.name.toString())
}

internal class AddFunctionToSupertypeFix(element: KtNamedFunction, functions: List<MemberData>) : AddMemberToSupertypeFix(element,
                                                                                                                          functions) {
  override val kind: String = "function"
  override val icon: Icon = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Function)
}

internal class AddPropertyToSupertypeFix(element: KtProperty, properties: List<MemberData>) : AddMemberToSupertypeFix(element, properties) {

  override val kind: String = "property"
  override val icon: Icon = PlatformIcons.PROPERTY_ICON
}
