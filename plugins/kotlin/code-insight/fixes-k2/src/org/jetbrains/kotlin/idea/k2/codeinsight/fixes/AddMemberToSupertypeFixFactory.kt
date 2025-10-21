// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.*
import com.intellij.openapi.project.Project
import com.intellij.ui.IconManager
import com.intellij.util.PlatformIcons
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.allSupertypes
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.containingSymbol
import org.jetbrains.kotlin.analysis.api.components.defaultType
import org.jetbrains.kotlin.analysis.api.components.importableFqName
import org.jetbrains.kotlin.analysis.api.components.isUnitType
import org.jetbrains.kotlin.analysis.api.components.render
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
import org.jetbrains.kotlin.idea.core.TemplateKind
import org.jetbrains.kotlin.idea.core.getFunctionBodyTextFromTemplate
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddMemberToSupertypeFixFactory.MemberData
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.modalityModifier
import org.jetbrains.kotlin.types.Variance
import javax.swing.Icon

internal object AddMemberToSupertypeFixFactory {

  internal class MemberData(val signaturePreview: String, val sourceCode: String, val targetClass: KtClass)

  val addMemberToSupertypeFixFactory: KotlinQuickFixFactory<KaFirDiagnostic.NothingToOverride> = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NothingToOverride ->
    val element = diagnostic.psi
    if (element !is KtProperty && element !is KtNamedFunction) return@ModCommandBased emptyList()

    val candidateMembers = getCandidateMembers(element)
    if (candidateMembers.isEmpty()) {
      return@ModCommandBased emptyList()
    }

    val action = when (element) {
      is KtProperty -> AddPropertyToSupertypeFix(element, candidateMembers)
      is KtNamedFunction -> AddFunctionToSupertypeFix(element, candidateMembers)
      else -> null
    }
    listOfNotNull(action)
  }

  context(_: KaSession)
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

  context(_: KaSession)
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

  context(_: KaSession)
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

internal sealed class AddMemberToSupertypeFix(
  private val element: KtCallableDeclaration,
  private val candidateMembers: List<MemberData>,
) : ModCommandAction {

  abstract val kind: String
  abstract val icon: Icon

  init {
    assert(candidateMembers.isNotEmpty())
  }

  private inner class AddSelectedMemberToSupertypeFix(
    element: KtCallableDeclaration,
    private val memberData: MemberData,
  ) : PsiUpdateModCommandAction<KtCallableDeclaration>(element) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.add.member.supertype.family", kind)

    override fun getPresentation(
      context: ActionContext,
      element: KtCallableDeclaration,
    ): Presentation = Presentation.of(actionName(memberData)).withIcon(icon)

    override fun invoke(
      context: ActionContext,
      element: KtCallableDeclaration,
      updater: ModPsiUpdater,
    ): Unit = addMember(context.project, element, memberData, updater)
  }

  override fun getPresentation(context: ActionContext): Presentation {
    val actionName = candidateMembers.singleOrNull()?.let { actionName(it) }
                     ?: KotlinBundle.message("fix.add.member.supertype.text", kind)
    return Presentation.of(actionName).withPriority(PriorityAction.Priority.LOW)
  }

  override fun getFamilyName(): @IntentionFamilyName String =
    KotlinBundle.message("fix.add.member.supertype.family", kind)

  override fun perform(context: ActionContext): ModCommand = ModCommand.chooseAction(
    KotlinBundle.message("fix.add.member.supertype.choose.type"),
    candidateMembers.map { candidateMember ->
      AddSelectedMemberToSupertypeFix(
        element,
        candidateMember,
      )
    },
  )
}

private fun addMember(
  project: Project,
  element: KtCallableDeclaration,
  memberData: MemberData,
  updater: ModPsiUpdater,
) {
  val memberElement: KtCallableDeclaration = KtPsiFactory(project).createDeclaration(memberData.sourceCode)
  val classBody = updater.getWritable(memberData.targetClass).getOrCreateBody()
  element.removeDefaultParameterValues()
  memberElement.copyAnnotationEntriesFrom(element)
  val insertedMemberElement = classBody.addBefore(memberElement, classBody.rBrace) as KtCallableDeclaration
  shortenReferences(insertedMemberElement)
  val modifierToken = insertedMemberElement.modalityModifier()?.node?.elementType as? KtModifierKeywordToken
                      ?: return
  if (insertedMemberElement.predictImplicitModality() == modifierToken) {
    RemoveModifierFixBase.invokeImpl(insertedMemberElement, modifierToken)
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

@Nls
private fun actionName(memberData: MemberData): String =
  KotlinBundle.message("fix.add.member.supertype.add.to", memberData.signaturePreview, memberData.targetClass.name.toString())


private class AddFunctionToSupertypeFix(
  element: KtNamedFunction,
  functions: List<MemberData>,
) : AddMemberToSupertypeFix(element, functions) {
  override val kind: String = "function"
  override val icon: Icon = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Function)
}

private class AddPropertyToSupertypeFix(
  element: KtProperty,
  properties: List<MemberData>,
) : AddMemberToSupertypeFix(element, properties) {
  override val kind: String = "property"
  override val icon: Icon = PlatformIcons.PROPERTY_ICON
}
