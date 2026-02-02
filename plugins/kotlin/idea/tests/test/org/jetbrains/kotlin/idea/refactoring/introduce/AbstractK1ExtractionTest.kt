// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.ide.DataManager
import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import com.intellij.refactoring.util.DocCommentPolicy
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.refactoring.chooseMembers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ExtractSuperInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractSuperConflictSearcher
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractSuperRefactoring
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.AbstractExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.EXTRACT_FUNCTION
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionData
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionOptions
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.possibleReturnTypes
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.processDuplicates
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.propertyTargets
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.validate
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceConstant.INTRODUCE_CONSTANT
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceConstant.KotlinIntroduceConstantHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.IntroduceParameterDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.KotlinIntroduceLambdaParameterHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.KotlinIntroduceLambdaParameterHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.KotlinIntroduceParameterHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter.KotlinIntroduceParameterHelper
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceProperty.INTRODUCE_PROPERTY
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceProperty.KotlinIntroducePropertyHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceTypeAlias.IntroduceTypeAliasDescriptor
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceTypeAlias.KotlinIntroduceTypeAliasHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceTypeParameter.KotlinIntroduceTypeParameterHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable.K1IntroduceVariableHandler
import org.jetbrains.kotlin.idea.refactoring.markMembersInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.extractClassMembers
import org.jetbrains.kotlin.idea.refactoring.selectElement
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase
import org.jetbrains.kotlin.idea.test.util.findElementByCommentPrefix
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import java.util.Collections
import kotlin.test.assertEquals

abstract class AbstractK1ExtractionTest: AbstractExtractionTest() {

    override fun getIntroduceVariableHandler(): RefactoringActionHandler = K1IntroduceVariableHandler

    override fun getExtractFunctionHandler(
        explicitPreviousSibling: PsiElement?,
        expectedNames: List<String>,
        expectedReturnTypes: List<String>,
        expectedDescriptors: String,
        expectedTypes: String,
        acceptAllScopes: Boolean,
        extractionOptions: ExtractionOptions
    ): AbstractExtractKotlinFunctionHandler {
        return ExtractKotlinFunctionHandler(
            helper = object : ExtractionEngineHelper(EXTRACT_FUNCTION) {
                override fun adjustExtractionData(data: ExtractionData): ExtractionData {
                    return data.copy(options = extractionOptions)
                }

                override fun configureAndRun(
                    project: Project,
                    editor: Editor,
                    descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
                    onFinish: (ExtractionResult) -> Unit
                ) {
                    val renderer = DescriptorRenderer.FQ_NAMES_IN_TYPES
                    val descriptor = descriptorWithConflicts.descriptor
                    val actualNames = descriptor.suggestedNames
                    val actualReturnTypes = descriptor.controlFlow.possibleReturnTypes.map {
                        IdeDescriptorRenderers.SOURCE_CODE.renderType(it)
                    }
                    val allParameters = listOfNotNull(descriptor.receiverParameter) + descriptor.parameters
                    val actualDescriptors = allParameters.map { renderer.render(it.originalDescriptor) }.joinToString()
                    val actualTypes = allParameters.map { param ->
                        param.getParameterTypeCandidates().joinToString(", ", "[", "]") { type -> renderer.renderType(type) }
                    }.joinToString()

                    if (actualNames.size != 1 || expectedNames.isNotEmpty()) {
                        assertEquals(expectedNames, actualNames, "Expected names mismatch.")
                    }
                    if (actualReturnTypes.size != 1 || expectedReturnTypes.isNotEmpty()) {
                        assertEquals(expectedReturnTypes, actualReturnTypes, "Expected return types mismatch.")
                    }
                    KotlinLightCodeInsightFixtureTestCaseBase.assertEquals(
                        "Expected descriptors mismatch.",
                        expectedDescriptors,
                        actualDescriptors
                    )
                    KotlinLightCodeInsightFixtureTestCaseBase.assertEquals("Expected types mismatch.", expectedTypes, actualTypes)

                    val newDescriptor = if (descriptor.name == "") {
                        descriptor.copy(suggestedNames = Collections.singletonList(DUMMY_FUN_NAME))
                    } else {
                        descriptor
                    }

                    fun afterFinish(extraction: ExtractionResult) {
                        processDuplicates(extraction.duplicateReplacers, project, editor)
                        onFinish(extraction)
                    }
                    doRefactor(ExtractionGeneratorConfiguration(newDescriptor, ExtractionGeneratorOptions.DEFAULT), ::afterFinish)
                }
            },
            allContainersEnabled = acceptAllScopes
        )
    }


    override fun doIntroduceVariableTest(unused: String) {
        doTestIfNotDisabledByFileDirective(isIntroduceVariableTest = true) { file ->
            TemplateManagerImpl.setTemplateTesting(getTestRootDisposable())

            file as KtFile

            val introduceVariableHandler =
                LanguageRefactoringSupport.getInstance()
                    .forLanguage(KotlinLanguage.INSTANCE).introduceVariableHandler as KotlinIntroduceVariableHandler
            introduceVariableHandler.occurenceReplaceChoice =
                if (InTextDirectivesUtils.isDirectiveDefined(file.text, "// REPLACE_SINGLE_OCCURRENCE")) {
                    OccurrencesChooser.ReplaceChoice.NO
                } else {
                    OccurrencesChooser.ReplaceChoice.ALL
                }

            KotlinCommonRefactoringSettings.getInstance().INTRODUCE_SPECIFY_TYPE_EXPLICITLY =
                InTextDirectivesUtils.isDirectiveDefined(file.text, "// SPECIFY_TYPE_EXPLICITLY")

            getIntroduceVariableHandler().invoke(
                fixture.project,
                fixture.editor,
                file,
                DataManager.getInstance().getDataContext(fixture.editor.component)
            )
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

            val inplaceVariableNames = InTextDirectivesUtils.findListWithPrefixes(file.text, "// INPLACE_VARIABLE_NAME:")
                .takeUnless { it.isEmpty() }
            val templateState = TemplateManagerImpl.getTemplateState(editor)

            if (inplaceVariableNames != null) {
                templateState as TemplateState

                WriteCommandAction.runWriteCommandAction(project) {
                    for (inplaceVariableName in inplaceVariableNames) {
                        val range = templateState.currentVariableRange ?: error("No variable range was found")
                        templateState.editor.document.replaceString(range.startOffset, range.endOffset, inplaceVariableName)
                        templateState.nextTab()
                    }
                }
            }

            if (templateState?.isFinished == false) {
                project.executeCommand("") { templateState.gotoEnd(false) }
            }
        }
    }

    protected fun doIntroduceSimpleParameterTest(path: String) {
        doIntroduceParameterTest(path, false)
    }

    protected fun doIntroduceLambdaParameterTest(path: String) {
        doIntroduceParameterTest(path, true)
    }

    private fun doIntroduceParameterTest(unused: String, asLambda: Boolean) {
        doTestIfNotDisabledByFileDirective { file ->
            val fileText = file.text

            open class HelperImpl : KotlinIntroduceParameterHelper<FunctionDescriptor> {
                override fun configure(descriptor: IntroduceParameterDescriptor<FunctionDescriptor>): IntroduceParameterDescriptor<FunctionDescriptor> = with(descriptor) {
                    val singleReplace = InTextDirectivesUtils.isDirectiveDefined(fileText, "// SINGLE_REPLACE")
                    val withDefaultValue = InTextDirectivesUtils.getPrefixedBoolean(fileText, "// WITH_DEFAULT_VALUE:") ?: true

                    copy(
                        occurrencesToReplace = if (singleReplace) Collections.singletonList(originalOccurrence) else occurrencesToReplace,
                        withDefaultValue = withDefaultValue
                    )
                }
            }

            class LambdaHelperImpl : HelperImpl(), KotlinIntroduceLambdaParameterHelper<FunctionDescriptor> {
                override fun configureExtractLambda(descriptor: ExtractableCodeDescriptor): ExtractableCodeDescriptor = with(descriptor) {
                    if (name.isEmpty()) copy(suggestedNames = listOf(DUMMY_FUN_NAME)) else this
                }
            }

            val handler = if (asLambda) {
                KotlinIntroduceLambdaParameterHandler(LambdaHelperImpl())
            } else {
                KotlinIntroduceParameterHandler(HelperImpl())
            }
            with(handler) {
                val target = (file as KtFile).findElementByCommentPrefix("// TARGET:") as? KtNamedDeclaration
                if (target != null) {
                    selectElement(fixture.editor, file, false, listOf(ElementKind.EXPRESSION)) { element ->
                        invoke(fixture.project, fixture.editor, element as KtExpression, target)
                    }
                } else {
                    invoke(fixture.project, fixture.editor, file, null)
                }
            }
        }
    }

    override fun doIntroducePropertyTest(unused: String) {
        doTestIfNotDisabledByFileDirective { file ->
            file as KtFile

            val extractionTarget = propertyTargets.single {
                it.targetName == InTextDirectivesUtils.findStringWithPrefixes(file.getText(), "// EXTRACTION_TARGET: ")
            }

            val explicitPreviousSibling = file.findElementByCommentPrefix("// SIBLING:")
            val helper = object : ExtractionEngineHelper(INTRODUCE_PROPERTY) {
                override fun validate(descriptor: ExtractableCodeDescriptor) = descriptor.validate(extractionTarget)

                override fun configureAndRun(
                    project: Project,
                    editor: Editor,
                    descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
                    onFinish: (ExtractionResult) -> Unit
                ) {
                    doRefactor(
                        ExtractionGeneratorConfiguration(
                            descriptorWithConflicts.descriptor,
                            ExtractionGeneratorOptions.DEFAULT.copy(target = extractionTarget, delayInitialOccurrenceReplacement = true)
                        ),
                        onFinish
                    )
                }
            }
            val handler = KotlinIntroducePropertyHandler(helper)
            val editor = fixture.editor
            handler.selectElements(editor, file) { elements, previousSibling ->
                handler.doInvoke(project, editor, file, elements, explicitPreviousSibling ?: previousSibling)
            }
        }
    }

    protected fun doIntroduceTypeParameterTest(unused: String) {
        doTest { file ->
            file as KtFile

            val explicitPreviousSibling = file.findElementByCommentPrefix("// SIBLING:")
            val editor = fixture.editor
            KotlinIntroduceTypeParameterHandler.selectElements(editor, file) { elements, previousSibling ->
                KotlinIntroduceTypeParameterHandler.doInvoke(project, editor, elements, explicitPreviousSibling ?: previousSibling)
            }
        }
    }


    override fun getIntroduceTypeAliasHandler(
        explicitPreviousSibling: PsiElement?,
        aliasName: String?,
        aliasVisibility: KtModifierKeywordToken?
    ): RefactoringActionHandler = object : KotlinIntroduceTypeAliasHandler() {
        override fun doInvoke(
            project: Project,
            editor: Editor,
            elements: List<PsiElement>,
            targetSibling: PsiElement,
            descriptorSubstitutor: ((IntroduceTypeAliasDescriptor) -> IntroduceTypeAliasDescriptor)?
        ) {
            super.doInvoke(project, editor, elements, explicitPreviousSibling ?: targetSibling) {
                it.copy(name = aliasName ?: it.name, visibility = aliasVisibility ?: it.visibility)
            }
        }
    }

    override fun doIntroduceConstantTest(unused: String) {
        doTestIfNotDisabledByFileDirective { file ->
            file as KtFile

            val extractionTarget = propertyTargets.single {
                it.targetName == InTextDirectivesUtils.findStringWithPrefixes(file.getText(), "// EXTRACTION_TARGET: ")
            }

            val helper = object : ExtractionEngineHelper(INTRODUCE_CONSTANT) {
                override fun configureAndRun(
                    project: Project,
                    editor: Editor,
                    descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
                    onFinish: (ExtractionResult) -> Unit
                ) {
                    doRefactor(
                        ExtractionGeneratorConfiguration(
                            descriptorWithConflicts.descriptor,
                            ExtractionGeneratorOptions(target = extractionTarget, delayInitialOccurrenceReplacement = true, isConst = true)
                        ),
                        onFinish
                    )
                }
            }

            val handler = KotlinIntroduceConstantHandler(helper)
            val editor = fixture.editor
            handler.selectElements(editor, file) { elements, target ->
                handler.doInvoke(project, editor, file, elements, target)
            }
        }
    }

    override fun doExtractSuperTest(unused: String, isInterface: Boolean) {
        doTest(checkAdditionalAfterdata = true) { file ->
            file as KtFile

            markMembersInfo(file)

            val targetParent = file.findElementByCommentPrefix("// SIBLING:")?.parent ?: file.parent!!
            val fileText = file.text
            val className = InTextDirectivesUtils.stringWithDirective(fileText, "NAME")
            val targetFileName = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// TARGET_FILE_NAME:")
                ?: "$className.${KotlinFileType.EXTENSION}"
            val editor = fixture.editor
            val originalClass = file.findElementAt(editor.caretModel.offset)?.getStrictParentOfType<KtClassOrObject>()!!
            val memberInfos = chooseMembers(extractClassMembers(originalClass))
            val conflicts = KotlinExtractSuperConflictSearcher.getInstance().collectConflicts(
                originalClass,
                memberInfos,
                targetParent,
                className,
                isInterface,
            )
            project.checkConflictsInteractively(conflicts) {
                val extractInfo = ExtractSuperInfo(
                    originalClass,
                    memberInfos,
                    targetParent,
                    targetFileName,
                    className,
                    isInterface,
                    DocCommentPolicy(DocCommentPolicy.ASIS)
                )
                KotlinExtractSuperRefactoring.getInstance().performRefactoring(extractInfo)
            }
        }
    }

    override fun updateScriptDependenciesSynchronously(psiFile: PsiFile) {
        ScriptConfigurationManager.updateScriptDependenciesSynchronously(psiFile)
    }
}
