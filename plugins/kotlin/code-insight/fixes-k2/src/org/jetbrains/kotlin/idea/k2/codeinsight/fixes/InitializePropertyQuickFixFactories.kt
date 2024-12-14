// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.usageView.UsageInfo
import com.intellij.util.application
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.psi.getOrCreateBody
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.*
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.KotlinNameSuggester
import org.jetbrains.kotlin.idea.refactoring.addElement
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.changeSignature.toValVar
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry

object InitializePropertyQuickFixFactories {

    private data class PropertyContext(
        val defaultInitializer: String,
    )

    private data class PropertyContextForNewParameter(
        val defaultInitializer: String,
        val propertyName: String,
        val propertyType: String,
    )

    private class InitializePropertyModCommandAction(
        property: KtProperty,
        propertyContext: PropertyContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtProperty, PropertyContext>(property, propertyContext) {

        override fun getFamilyName(): String = KotlinBundle.message("add.initializer")

        override fun invoke(
            actionContext: ActionContext,
            element: KtProperty,
            elementContext: PropertyContext,
            updater: ModPsiUpdater,
        ) {
            val expression = KtPsiFactory(actionContext.project)
                .createExpression(elementContext.defaultInitializer)
            val initializer = element.setInitializer(expression)!!
            updater.select(TextRange(initializer.startOffset, initializer.endOffset))
            updater.moveCaretTo(initializer.endOffset)
        }
    }

    private class InitializeWithConstructorParameterFix(
        property: KtProperty,
        private val propertyContext: PropertyContextForNewParameter,
    ) : KotlinQuickFixAction<KtProperty>(property) {

        override fun getText(): @IntentionName String = KotlinBundle.message("initialize.with.constructor.parameter")
        override fun getFamilyName(): @IntentionFamilyName String = text

        override fun startInWriteAction(): Boolean = false

        override fun invoke(
            project: Project,
            editor: Editor?,
            file: KtFile
        ) {
            val property = element ?: return

            val containingClass = property.containingClassOrObject as? KtClass ?: return

            val constructors = buildList {
                addIfNotNull(containingClass.primaryConstructor)
                addAll(containingClass.secondaryConstructors)

                // add `null` to change signature of the implicit primary constructor
                if (isEmpty()) add(null)
            }

            // the constructor with a changed signature might conflict with an existing one, which is not updated at the moment;
            // to avoid such conflicts, we need to update constructors in order defined by their parameter count
            val sortedConstructors = constructors.sortedByDescending { it?.valueParameters?.size ?: 0 }
            val updatedConstructors = sortedConstructors.map { constructor ->
                changeConstructorSignature(project, property, constructor, containingClass)
            }

            application.runWriteAction {
                for (updatedConstructor in updatedConstructors) {
                    initializeProperty(project, property, updatedConstructor, containingClass)
                }
            }
        }

        private fun initializeProperty(
            project: Project,
            property: KtProperty,
            constructor: KtConstructor<*>,
            containingClass: KtClass,
        ) {
            val newParameterName = constructor.valueParameters.last().name
                ?: errorWithAttachment(property, constructor, containingClass)

            val psiFactory = KtPsiFactory(project)
            when (constructor) {
                is KtPrimaryConstructor -> property.setInitializer(psiFactory.createExpression(newParameterName))
                is KtSecondaryConstructor -> {
                    val callToThis = constructor.getDelegationCall().takeIf { it.isCallToThis }
                    if (callToThis != null) {
                        callToThis.valueArguments.last().getArgumentExpression()?.replace(psiFactory.createExpression(newParameterName))
                    } else {
                        val initialization = psiFactory.createExpression("this.${propertyContext.propertyName} = $newParameterName")
                        constructor.getOrCreateBody().addElement(initialization)
                    }
                }
            }
        }

        private fun changeConstructorSignature(
            project: Project,
            property: KtProperty,
            constructor: KtConstructor<*>?,
            containingClass: KtClass,
        ): KtConstructor<*> {
            val constructorPointer = constructor?.createSmartPointer()
            val containingClassPointer = containingClass.createSmartPointer()

            val validator = KotlinDeclarationNameValidator(
              visibleDeclarationsContext = containingClass.parent as KtElement,
              checkVisibleDeclarationsContext = false,
              target = KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER,
              excludedDeclarations = listOf(property),
            )

            val existingNames = constructor?.valueParameters?.mapNotNull { it.name }?.toSet() ?: emptySet()

            val parameterName = analyzeInModalWindow(
                containingClass,
                KotlinBundle.message("initialize.with.constructor.parameter.analyzing.existing.variables")
            ) {
                KotlinNameSuggester.suggestNameByName(propertyContext.propertyName) { it !in existingNames && validator.validate(it) }
            }

            val changeInfo = buildChangeInfoForAddingParameter(
                constructor,
                containingClass,
                parameterName,
                propertyContext.propertyType,
                propertyContext.defaultInitializer
            )

            KotlinChangeSignatureProcessor(project, changeInfo).run()

            val updatedConstructor = if (constructorPointer != null) {
                constructorPointer.element
            } else {
                containingClassPointer.element?.primaryConstructor
            } ?: errorWithAttachment(property, constructor, containingClass)

            return updatedConstructor
        }
    }

    private class MoveToConstructorParametersFix(
        property: KtProperty,
        private val propertyContext: PropertyContextForNewParameter,
    ) : KotlinQuickFixAction<KtProperty>(property) {

        override fun getText(): @IntentionName String = KotlinBundle.message("move.to.constructor.parameters")
        override fun getFamilyName(): @IntentionFamilyName String = text

        override fun startInWriteAction(): Boolean = false

        override fun invoke(
            project: Project,
            editor: Editor?,
            file: KtFile
        ) {
            val property = element ?: return
            val containingClass = property.containingClassOrObject as? KtClass ?: return
            val primaryConstructor = containingClass.primaryConstructor

            val changeInfo = buildChangeInfoForAddingParameter(
                primaryConstructor,
                containingClass,
                propertyContext.propertyName,
                propertyContext.propertyType,
                propertyContext.defaultInitializer,
                valOrVar = property.valOrVarKeyword.toValVar(),
            )

            val changeSignatureProcessor = object : KotlinChangeSignatureProcessor(project, changeInfo) {
                override fun performRefactoring(usages: Array<out UsageInfo>) {
                    super.performRefactoring(usages)

                    val newParameter = containingClass.primaryConstructor?.valueParameters?.lastOrNull<KtParameter>()
                        ?: errorWithAttachment(property, primaryConstructor, containingClass)

                    // replace a new parameter with the property text to preserve the property's modifiers and comments
                    val parameterToReplaceWith = KtPsiFactory(project).createParameter(property.text)

                    newParameter.replace(parameterToReplaceWith)
                    property.delete()
                }

                override fun showConflicts(conflicts: MultiMap<PsiElement, String>, usages: Array<out UsageInfo>?): Boolean {
                    // don't show a conflict with the property as it will be removed after the refactoring is performed
                    conflicts.remove(property)
                    return super.showConflicts(conflicts, usages)
                }
            }

            changeSignatureProcessor.setPrepareSuccessfulSwingThreadCallback {}
            changeSignatureProcessor.run()
        }
    }

    // todo refactor
    val mustBeInitialized = KotlinQuickFixFactory { diagnostic: KaFirDiagnostic.MustBeInitialized ->
        createFixes(diagnostic.psi)
    }

    val mustBeInitializedWarning = KotlinQuickFixFactory { diagnostic: KaFirDiagnostic.MustBeInitializedWarning ->
        createFixes(diagnostic.psi)
    }

    val mustBeInitializedOrBeFinal = KotlinQuickFixFactory { diagnostic: KaFirDiagnostic.MustBeInitializedOrBeFinal ->
        createFixes(diagnostic.psi)
    }

    val mustBeInitializedOrBeFinalWarning =
        KotlinQuickFixFactory { diagnostic: KaFirDiagnostic.MustBeInitializedOrBeFinalWarning ->
            createFixes(diagnostic.psi)
        }

    val mustBeInitializedOrBeAbstract = KotlinQuickFixFactory { diagnostic: KaFirDiagnostic.MustBeInitializedOrBeAbstract ->
        createFixes(diagnostic.psi)
    }

    val mustBeInitializedOrBeAbstractWarning =
        KotlinQuickFixFactory { diagnostic: KaFirDiagnostic.MustBeInitializedOrBeAbstractWarning ->
            createFixes(diagnostic.psi)
        }

    val mustBeInitializedOrFinalOrAbstract =
        KotlinQuickFixFactory { diagnostic: KaFirDiagnostic.MustBeInitializedOrFinalOrAbstract ->
            createFixes(diagnostic.psi)
        }

    val mustBeInitializedOrFinalOrAbstractWarning =
        KotlinQuickFixFactory { diagnostic: KaFirDiagnostic.MustBeInitializedOrFinalOrAbstractWarning ->
            createFixes(diagnostic.psi)
        }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.createFixes(
        property: KtProperty,
    ): List<CommonIntentionAction> {
        // An extension property cannot be initialized because it has no backing field
        if (property.receiverTypeReference != null) return emptyList()

        return buildList {
            val propertyType = property.returnType
            val initializerText = propertyType.defaultInitializer ?: "TODO()"
            val propertyContext = PropertyContext(initializerText)

            add(InitializePropertyModCommandAction(property, propertyContext))

            val propertyName = property.name ?: return@buildList

            (property.containingClassOrObject as? KtClass)?.let { ktClass ->
                if (ktClass.isAnnotation() || ktClass.isInterface()) return@let
                if (ktClass.primaryConstructor?.hasActualModifier() == true) return@let

                val propertyContextForNewParameter = PropertyContextForNewParameter(
                    initializerText,
                    propertyName,
                    propertyType.render(position = Variance.INVARIANT)
                )

                val secondaryConstructors = ktClass.secondaryConstructors.filterNot(::hasExplicitDelegationCallToThis)
                if (property.accessors.isEmpty() && secondaryConstructors.isEmpty()) {
                    add(MoveToConstructorParametersFix(property, propertyContextForNewParameter))
                }
                if (secondaryConstructors.none { it.hasActualModifier() }) {
                    add(InitializeWithConstructorParameterFix(property, propertyContextForNewParameter))
                }
            }
        }
    }

    private fun buildChangeInfoForAddingParameter(
        constructor: KtConstructor<*>?,
        containingClass: KtClass,
        name: String,
        type: String,
        defaultValueForCall: String,
        valOrVar: KotlinValVar = KotlinValVar.None,
    ): KotlinChangeInfo {
        val constructorOrClass = constructor ?: containingClass

        val methodDescriptor = KotlinMethodDescriptor(constructorOrClass)
        val changeInfo = KotlinChangeInfo(methodDescriptor)

        val parameterInfo = KotlinParameterInfo(
            originalType = KotlinTypeInfo(type, constructorOrClass),
            name = name,
            valOrVar = valOrVar,
            defaultValueForCall = KtPsiFactory.contextual(constructorOrClass).createExpression(defaultValueForCall),
            defaultValueAsDefaultParameter = false,
            defaultValue = null,
            context = constructorOrClass,
        )

        changeInfo.addParameter(parameterInfo)

        return changeInfo
    }

    private fun hasExplicitDelegationCallToThis(constructor: KtSecondaryConstructor): Boolean = constructor.getDelegationCall().isCallToThis

    private fun errorWithAttachment(property: KtProperty, constructor: KtConstructor<*>?, containingClass: KtClass): Nothing {
        errorWithAttachment("Failed to perform fix") {
            withPsiEntry("property", property)
            withPsiEntry("constructor", constructor)
            withPsiEntry("containingClass", containingClass)
        }
    }
}
