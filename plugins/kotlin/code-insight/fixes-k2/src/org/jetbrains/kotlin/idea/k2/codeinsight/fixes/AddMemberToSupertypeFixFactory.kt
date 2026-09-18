// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.project.Project
import com.intellij.ui.IconManager
import com.intellij.util.PlatformIcons
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.rendering.KaPiece
import org.jetbrains.kotlin.analysis.api.rendering.KaPieceRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingContext
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOption
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOutput
import org.jetbrains.kotlin.analysis.api.rendering.KaTextAttribute
import org.jetbrains.kotlin.analysis.api.rendering.append
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.containingSymbol
import org.jetbrains.kotlin.analysis.api.symbols.importableFqName
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.allSupertypes
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.defaultType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.defaultValue
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.addAnnotation
import org.jetbrains.kotlin.idea.base.psi.getOrCreateClassBody
import org.jetbrains.kotlin.idea.base.psi.predictImplicitModality
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.util.withShortNames
import org.jetbrains.kotlin.idea.util.withoutAnnotations
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.core.TemplateKind
import org.jetbrains.kotlin.idea.core.getFunctionBodyTextFromTemplate
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddMemberToSupertypeFixFactory.MemberData
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.idea.util.withQualifiedDenotableSupertypes
import org.jetbrains.kotlin.idea.util.withQualifiedNames
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.addRemoveModifier.sortModifiers
import org.jetbrains.kotlin.psi.psiUtil.modalityModifier
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
  private fun createMemberData(classSymbol: KaClassSymbol, memberElement: KtCallableDeclaration): MemberData? {
    val project = memberElement.project
    val callableSymbol = memberElement.symbol
    val targetClass = classSymbol.psi as? KtClass ?: return null
    val signaturePreview = KaRenderer.withShortNames.forTargetClass(classSymbol).renderToString(callableSymbol)
    var sourceCode = KaRenderer.withQualifiedNames.forTargetClass(classSymbol).renderToString(callableSymbol)
    if (memberElement is KtNamedFunction) {
      if (classSymbol.classKind != KaClassKind.INTERFACE && classSymbol.modality != KaSymbolModality.ABSTRACT && classSymbol.modality != KaSymbolModality.SEALED) {
        val returnType = (callableSymbol as KaNamedFunctionSymbol).returnType
        sourceCode += if (returnType.classId != KaStandardTypeClassIds.UNIT) {
          val bodyText = getFunctionBodyTextFromTemplate(project, TemplateKind.FUNCTION, callableSymbol.name.asString(),
                                                         KaRenderer.withQualifiedDenotableSupertypes.renderToString(callableSymbol.returnType),
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

  private fun KaRenderer.forTargetClass(targetClassSymbol: KaClassSymbol): KaRenderer = copy {
    withoutAnnotations()
    push(SourceDefaultValueExpressionRenderer)

    set(KaRenderingOption.Modifiers) { symbol, modifiers ->
      buildList {
        addAll(modifiers)
        remove(KtTokens.OVERRIDE_KEYWORD)

        if (symbol.containingSymbol is KaClassSymbol) {
          removeAll { it in KtTokens.MODALITY_MODIFIERS }
          add(
            if (targetClassSymbol.modality == KaSymbolModality.SEALED || targetClassSymbol.modality == KaSymbolModality.ABSTRACT) {
              KtTokens.ABSTRACT_KEYWORD
            } else {
              KtTokens.OPEN_KEYWORD
            }
          )
        }
      }.distinct().let(::sortModifiers)
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
  ) : KotlinPsiUpdateModCommandAction.ElementContextless<KtCallableDeclaration>(element) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.add.member.supertype.family", kind)

    override fun getActionPresentation(
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
  val classBody = updater.getWritable(memberData.targetClass).getOrCreateClassBody()
  element.removeDefaultParameterValues()
  memberElement.copyAnnotationEntriesFrom(element)
  val insertedMemberElement = classBody.addBefore(memberElement, classBody.rBrace) as KtCallableDeclaration
  shortenReferences(insertedMemberElement)
  val modifierToken = insertedMemberElement.modalityModifier()?.node?.elementType as? KtModifierKeywordToken
                      ?: return
  if (insertedMemberElement.predictImplicitModality() == modifierToken) {
    RemoveModifierFixBase.removeModifier(insertedMemberElement, modifierToken)
  }
}

private fun KtCallableDeclaration.removeDefaultParameterValues() {
  valueParameters.forEach {
    it.defaultValue?.delete()
    it.equalsToken?.delete()
  }
}

private fun KtCallableDeclaration.copyAnnotationEntriesFrom(member: KtCallableDeclaration?) {
  member?.annotationEntries?.reversed()?.forEach { addAnnotation(it) }
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

/**
 * Renders a value parameter's default value as the expression written in the source code.
 *
 * The built-in renderer evaluates the default value and renders a placeholder for anything which is not a compile-time constant, which is
 * wrong when the member is being copied into a supertype: the expression has to survive verbatim. Declines when the parameter has no source
 * expression, so the built-in placeholder logic still applies.
 */
private object SourceDefaultValueExpressionRenderer :
  KaPieceRenderer<KaValueParameterSymbol>(KaPiece.ValueParameterDefaultValueExpression) {
  context(session: KaSession, context: KaRenderingContext, output: KaRenderingOutput)
  override fun render(value: KaValueParameterSymbol, next: () -> Unit): Boolean {
    val defaultValue = value.defaultValue ?: return false
    output.append(defaultValue.text, KaTextAttribute.Identifier)
    return true
  }
}
