// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModChooseAction
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.scopes.memberScope
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.addMemberDeclaration
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.ValueClassMemberGenerationUtils.Parameter
import org.jetbrains.kotlin.idea.codeinsight.utils.ValueClassMemberGenerationUtils.componentFunctionText
import org.jetbrains.kotlin.idea.codeinsight.utils.ValueClassMemberGenerationUtils.copyFunctionText
import org.jetbrains.kotlin.idea.codeinsight.utils.ValueClassMemberGenerationUtils.generationParameters
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.classVisitor
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

// new feature support
// requires -XXLanguage:+FullValueClasses
@ApiStatus.Internal
class ConvertDataClassToValueClassInspection :
    KotlinApplicableInspectionBase<KtClass, ConvertDataClassToValueClassInspection.Context>() {

    class Context(val properties: List<Parameter>)

    override fun isAvailableForFile(file: PsiFile): Boolean =
        super.isAvailableForFile(file) &&
                file.languageVersionSettings.supportsFeature(LanguageFeature.FullValueClasses)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = classVisitor { klass ->
        visitTargetElement(klass, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtClass): Boolean {
        if (!element.isData() || element.nameIdentifier == null) return false
        if (element.isInner()) return false

        val parameters = element.primaryConstructorParameters
        if (parameters.isEmpty()) return false
        if (parameters.any { !it.hasValOrVar() || it.isMutable }) return false
        if (parameters.any { it.nameIdentifier == null || it.typeReference == null }) return false

        return element.body?.properties?.none { it.hasBackingField() } != false
    }

    override fun getApplicableRanges(element: KtClass): List<TextRange> =
        ApplicabilityRange.union(element) { listOfNotNull(it.nameIdentifier, it.dataKeyword()) }

    context(session: KaSession)
    override fun prepareContext(element: KtClass): Context? {

        val inheritedNames = element.superTypeListEntries.map { entry ->
            val typeReference = (entry as? KtSuperTypeEntry)?.typeReference ?: return null
            val classSymbol = typeReference.type.symbol as? KaClassSymbol ?: return null
            classSymbol
        }
            .flatMap { it.memberScope.callables }
            .filterIsInstance<KaNamedFunctionSymbol>()
            .filter { it.valueParameters.isEmpty() }
            .map { it.name.asString() }

        val properties = element.generationParameters { index -> "component${index + 1}" in inheritedNames } ?: return null
        return Context(properties)
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtClass,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor {
        val fix = LocalQuickFix.from(ChooseGeneratedMembersFix(element, context))
        return createProblemDescriptor(
            element,
            rangeInElement,
            KotlinBundle.message("inspection.convert.to.value.class.display.name"),
            ProblemHighlightType.INFORMATION,
            onTheFly,
            *listOfNotNull(fix).toTypedArray(),
        )
    }

    private class ChooseGeneratedMembersFix(
        element: KtClass,
        private val elementContext: Context,
    ) : PsiBasedModCommandAction<KtClass>(element, KtClass::class.java) {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("inspection.convert.to.value.class.fix.text")

        override fun perform(context: ActionContext, element: KtClass): ModCommand = ModChooseAction(
            KotlinBundle.message("inspection.convert.to.value.class.chooser.title"),
            GeneratedMembers.entries.map {
                ConvertToValueClassFix(element, elementContext.properties, it)
            },
        )
    }

    private class ConvertToValueClassFix(
        element: KtClass,
        private val properties: List<Parameter>,
        private val members: GeneratedMembers,
    ) : PsiUpdateModCommandAction<KtClass>(element) {

        override fun getFamilyName(): @IntentionFamilyName String = members.text

        override fun invoke(context: ActionContext, element: KtClass, updater: ModPsiUpdater) {
            val dataKeyword = element.dataKeyword() as? LeafPsiElement ?: return
            dataKeyword.replaceWithText(KtTokens.VALUE_KEYWORD.value)

            val psiFactory = KtPsiFactory(context.project, markGenerated = true)
            val hasInheritedMethod = hasInheritedComponentNFunction(properties)
            when (members) {
                GeneratedMembers.NONE -> {
                    if (!hasInheritedMethod) return
                    else generateOverrideComponentNFunctions(element, properties, psiFactory)
                }

                GeneratedMembers.COMPONENTS -> properties.forEachIndexed { index, property ->
                    element.addMemberDeclaration(psiFactory.createFunction(componentFunctionText(index + 1, property)))
                }

                GeneratedMembers.COPY -> {
                    element.addMemberDeclaration(psiFactory.createFunction(element.copyFunctionText(properties) ?: return))
                    if (hasInheritedMethod) generateOverrideComponentNFunctions(element, properties, psiFactory)
                }

                GeneratedMembers.COPY_AND_COMPONENTS -> {
                    properties.forEachIndexed { index, property ->
                        element.addMemberDeclaration(psiFactory.createFunction(componentFunctionText(index + 1, property)))
                    }
                    element.addMemberDeclaration(psiFactory.createFunction(element.copyFunctionText(properties) ?: return))
                }
            }
        }

        private fun generateOverrideComponentNFunctions(element: KtClass, properties: List<Parameter>, psiFactory: KtPsiFactory) {
            properties.forEachIndexed { index, parameter ->
                if (parameter.overrideComponentN) {
                    element.addMemberDeclaration(psiFactory.createFunction(componentFunctionText(index + 1, parameter)))
                }
            }
        }

        private fun hasInheritedComponentNFunction(properties: List<Parameter>): Boolean {
            return properties.any { it.overrideComponentN }
        }
    }

    private enum class GeneratedMembers(private val messageKey: String) {
        NONE("inspection.convert.to.value.class.option.none"),
        COPY("inspection.convert.to.value.class.option.copy"),
        COMPONENTS("inspection.convert.to.value.class.option.components"),
        COPY_AND_COMPONENTS("inspection.convert.to.value.class.option.copy.and.components");

        val text: @IntentionFamilyName String
            get() = KotlinBundle.message(messageKey)
    }

    private fun KtProperty.hasBackingField(): Boolean {
        if (hasInitializer() || hasDelegate()) return true
        if (getter?.hasBody() != true) return true
        if (isVar && setter?.hasBody() != true) return true
        return accessors.any { accessor ->
            accessor.anyDescendantOfType<KtSimpleNameExpression> { it.getReferencedName() == KtTokens.FIELD_KEYWORD.value }
        }
    }

}

private fun KtClass.dataKeyword() = modifierList?.getModifier(KtTokens.DATA_KEYWORD)
