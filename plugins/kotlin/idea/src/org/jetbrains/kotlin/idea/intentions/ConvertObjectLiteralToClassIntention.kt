// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.ChooseStringExpression
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionData
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionEngine
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.TypeParameter
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.collectRelevantConstraints
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.createPrimaryConstructorParameterListIfAbsent
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier

@K1Deprecation
class ConvertObjectLiteralToClassIntention : SelfTargetingRangeIntention<KtObjectLiteralExpression>(
    KtObjectLiteralExpression::class.java,
    KotlinBundle.messagePointer("convert.object.literal.to.class")
) {
    override fun applicabilityRange(element: KtObjectLiteralExpression): TextRange? = element.objectDeclaration.getObjectKeyword()?.textRange

    override fun startInWriteAction(): Boolean = false

    private fun doApply(editor: Editor, element: KtObjectLiteralExpression, targetParent: KtElement) {
        val project = element.project

        val scope = element.getResolutionScope()

        val validator: (String) -> Boolean = { scope.findClassifier(Name.identifier(it), NoLookupLocation.FROM_IDE) == null }
        val classNames = element.objectDeclaration.superTypeListEntries
            .mapNotNull {
                it.typeReference?.typeElement?.let { typeElement ->
                KotlinNameSuggester.suggestTypeAliasNameByPsi(typeElement, validator) }
            }
            .takeIf { it.isNotEmpty() } ?: listOf(KotlinNameSuggester.suggestNameByName("O", validator))

        val className = classNames.first()
        val psiFactory = KtPsiFactory(element.project)

        val targetSibling = element.parentsWithSelf.first { it.parent == targetParent }

        val objectDeclaration = element.objectDeclaration

        val containingClass = element.containingClass()
        val hasMemberReference = containingClass?.body?.allChildren?.any {
            (it is KtProperty || it is KtNamedFunction) &&
                    ReferencesSearch.search(it, element.useScope).findFirst() != null
        } ?: false

        // Collect type parameters only from accessible ancestor classes
        val ancestorTypeParameters = mutableListOf<KtTypeParameter>()
        var currentClass = containingClass
        while (currentClass != null) {
            currentClass.typeParameters.let { ancestorTypeParameters.addAll(it) }
            // Stop if this class is not inner - its outer classes' type parameters are not accessible
            if (!currentClass.isInner()) break
            currentClass = currentClass.containingClass()
        }

        val typeParamsSuffix = ancestorTypeParameters
            .mapNotNull { it.name }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "<", postfix = ">")
            ?: ""

        val newClass = psiFactory.createClass("class $className$typeParamsSuffix")
        objectDeclaration.getSuperTypeList()?.let {
            newClass.add(psiFactory.createColon())
            newClass.add(it)
        }

        objectDeclaration.body?.let {
            newClass.add(it)
        }

        ExtractionEngine(
            object : ExtractionEngineHelper(text) {
                override fun configureAndRun(
                    project: Project,
                    editor: Editor,
                    descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
                    onFinish: (ExtractionResult) -> Unit
                ) {
                    project.executeCommand(text) {
                        val descriptor = descriptorWithConflicts.descriptor
                        
                        // Add outer class type parameters to force them in the call site
                        val typeParameters = descriptor.typeParameters
                        val outerTypeParameters = ancestorTypeParameters
                            .filter { typeParam -> 
                                typeParameters.none { it.originalDeclaration.name == typeParam.name }
                            }
                            .map { TypeParameter(it, it.collectRelevantConstraints()) }
                        
                        val modifiedDescriptor = descriptor.copy(
                            suggestedNames = listOf(className),
                            typeParameters = typeParameters + outerTypeParameters
                        )
                        
                        doRefactor(
                            ExtractionGeneratorConfiguration(modifiedDescriptor, ExtractionGeneratorOptions.DEFAULT),
                            onFinish
                        )
                    }
                }
            }
        ).run(editor, ExtractionData(element.containingKtFile, element.toRange(), targetSibling)) { extractionResult ->
            val functionDeclaration = extractionResult.declaration as KtFunction
            if (functionDeclaration.valueParameters.isNotEmpty()) {
                val valKeyword = psiFactory.createValKeyword()
                newClass.createPrimaryConstructorParameterListIfAbsent()
                    .replaced(functionDeclaration.valueParameterList!!)
                    .parameters
                    .forEach {
                        it.addAfter(valKeyword, null)
                        it.addModifier(KtTokens.PRIVATE_KEYWORD)
                    }
            }

            val introducedClass = runWriteAction {
                val replaced = functionDeclaration.replaced(newClass)
                // Should be inner if: uses outer members OR uses outer type parameters
                val usesOuterTypeParams = ancestorTypeParameters.isNotEmpty()
                val shouldBeInner = hasMemberReference || usesOuterTypeParams
                if (shouldBeInner && containingClass == (replaced.parent.parent as? KtClass)) {
                    replaced.addModifier(KtTokens.INNER_KEYWORD)
                }
                replaced.primaryConstructor?.reformatted()
                CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(replaced)
            } ?: return@run

            val file = introducedClass.containingFile

            val template = TemplateBuilderImpl(file).let { builder ->
                builder.replaceElement(introducedClass.nameIdentifier!!, NEW_CLASS_NAME, ChooseStringExpression(classNames), true)
                for (psiReference in ReferencesSearch.search(introducedClass, LocalSearchScope(file), false).asIterable()) {
                    runWriteAction {
                        builder.replaceElement(psiReference.element, USAGE_VARIABLE_NAME, NEW_CLASS_NAME, false)
                    }
                }
                runWriteAction {
                    builder.buildInlineTemplate()
                }
            }

            editor.caretModel.moveToOffset(file.startOffset)
            runWriteAction {
                TemplateManager.getInstance(project).startTemplate(editor, template)
            }
        }
    }

    override fun applyTo(element: KtObjectLiteralExpression, editor: Editor?) {
        if (editor == null) return

        val containers = element.getExtractionContainers(strict = true, includeAll = true)

        if (isUnitTestMode()) {
            val targetComment = element.containingKtFile.findDescendantOfType<PsiComment>()?.takeIf {
                it.text == "// TARGET_BLOCK:"
            }

            val target = containers.firstOrNull { it == targetComment?.parent } ?: containers.last()
            return doApply(editor, element, target)
        }

        chooseContainerElementIfNecessary(
            containers,
            editor,
            if (containers.first() is KtFile) KotlinBundle.message("select.target.file") else KotlinBundle.message("select.target.code.block.file"),
            true
        ) {
            doApply(editor, element, it)
        }
    }
}

private const val NEW_CLASS_NAME = "NEW_CLASS_NAME"

private const val USAGE_VARIABLE_NAME = "OTHER_VAR"
