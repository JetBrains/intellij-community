// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage


import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.jvm.actions.AnnotationRequest
import com.intellij.lang.jvm.actions.ChangeParametersRequest
import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JvmPsiConversionHelper
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaRendererTypeApproximator
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder.addAnnotationEntry
import org.jetbrains.kotlin.load.java.NOT_NULL_ANNOTATIONS
import org.jetbrains.kotlin.load.java.NULLABLE_ANNOTATIONS
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

internal class ChangeMethodParameters(
    target: KtNamedFunction,
    val request: ChangeParametersRequest
) : KotlinQuickFixAction<KtNamedFunction>(target) {

    @OptIn(KaExperimentalApi::class)
    override fun getText(): String {
        val target = element ?: return KotlinBundle.message("fix.change.signature.unavailable")

        val helper = JvmPsiConversionHelper.getInstance(target.project)

        val parametersString = request.expectedParameters.joinToString(", ", "(", ")") { ep ->
            val parameterName = ep.semanticNames.firstOrNull() ?: KotlinBundle.message("fix.change.signature.unnamed.parameter")
            val renderedType =
                ep.expectedTypes.firstOrNull()?.theType?.let {
                    val convertType = helper.convertType(it)

                    analyze(target) {
                        val kaType = convertType.asKaType(target)?.let {
                            KaRendererTypeApproximator.TO_DENOTABLE.approximateType(this, it, Variance.IN_VARIANCE)
                        } ?: error("Can't convert type $it")
                        val render = kaType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT)
                        render
                    }
                } ?: KotlinBundle.message("fix.change.signature.error")
            "$parameterName: $renderedType"
        }

        val shortenParameterString =
            StringUtil.shortenTextWithEllipsis(/* text = */ parametersString,
                                               /* maxLength = */ 30,
                                               /* suffixLength = */ 5,
                                               /* useEllipsisSymbol = */ true)
        return QuickFixBundle.message("change.method.parameters.text", shortenParameterString)
    }

    override fun getFamilyName(): String = QuickFixBundle.message("change.method.parameters.family")

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = element != null && request.isValid

    private sealed class ParameterModification {
        data class Keep(val ktParameter: KtParameter) : ParameterModification()
        data class Remove(val ktParameter: KtParameter) : ParameterModification()
        data class Add(
            val name: String,
            val jvmType: JvmType,
            val expectedAnnotations: Collection<AnnotationRequest>,
            val beforeAnchor: KtParameter?
        ) : ParameterModification()
    }

    private tailrec fun getParametersModifications(
        target: KtNamedFunction,
        currentParameters: List<KtParameter>,
        expectedParameters: List<ExpectedParameter>,
        index: Int = 0,
        collected: List<ParameterModification> = ArrayList(expectedParameters.size)
    ): List<ParameterModification> {
        val expectedHead = expectedParameters.firstOrNull() ?:
            return (collected + currentParameters.map { ParameterModification.Remove(it) })

        if (expectedHead is ChangeParametersRequest.ExistingParameterWrapper) {
            val expectedExistingParameter = expectedHead.existingKtParameter
            if (expectedExistingParameter == null) {
                LOG.error("can't find the kotlinOrigin for parameter ${expectedHead.existingParameter} at index $index")
                return collected
            }

            val existingInTail = currentParameters.indexOf(expectedExistingParameter)
            if (existingInTail == -1) {
                throw IllegalArgumentException("can't find existing for parameter ${expectedHead.existingParameter} at index $index")
            }

            return getParametersModifications(
                target,
                currentParameters.subList(existingInTail + 1, currentParameters.size),
                expectedParameters.subList(1, expectedParameters.size),
                index,
                collected
                        + currentParameters.subList(0, existingInTail).map { ParameterModification.Remove(it) }
                        + ParameterModification.Keep(expectedExistingParameter)
            )
        }

        val theType = expectedHead.expectedTypes.firstOrNull()?.theType ?: return emptyList()

        return getParametersModifications(
            target,
            currentParameters,
            expectedParameters.subList(1, expectedParameters.size),
            index + 1,
            collected + ParameterModification.Add(
                expectedHead.semanticNames.firstOrNull() ?: "param$index",
                theType,
                expectedHead.expectedAnnotations,
                currentParameters.firstOrNull { anchor ->
                    expectedParameters.any {
                        it is ChangeParametersRequest.ExistingParameterWrapper && it.existingKtParameter == anchor
                    }
                })
        )

    }

    private val ChangeParametersRequest.ExistingParameterWrapper.existingKtParameter
        get() = (existingParameter as? KtLightElement<*, *>)?.kotlinOrigin as? KtParameter


    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        doChangeParameter(project, PsiTreeUtil.findSameElementInCopy(element, file))
        return IntentionPreviewInfo.DIFF
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        if (!request.isValid) return
        doChangeParameter(project, element ?: return)
    }

    private fun doChangeParameter(project: Project, target: KtNamedFunction) {
        val parameterActions =
            getParametersModifications(target, target.valueParameters, request.expectedParameters)

        val psiFactory = KtPsiFactory(project)

        val parametersGenerated =
            parameterActions.filterIsInstance<ParameterModification.Add>().let {
                it zip generateParameterList(project, psiFactory, target, it).parameters
            }.toMap()

        val valueParameterList = target.valueParameterList!!
        for (action in parameterActions) {
            when (action) {
                is ParameterModification.Add -> {
                    val parameter = parametersGenerated.getValue(action)
                    for (expectedAnnotation in action.expectedAnnotations.reversed()) {
                        addAnnotationEntry(parameter, expectedAnnotation, null)
                    }
                    val anchor = action.beforeAnchor
                    if (anchor != null) {
                        valueParameterList.addParameterBefore(parameter, anchor)
                    } else {
                        valueParameterList.addParameter(parameter)
                    }
                }

                is ParameterModification.Keep -> {
                    // Do nothing
                }

                is ParameterModification.Remove -> {
                    valueParameterList.removeParameter(action.ktParameter)
                }
            }
        }

        shortenReferences(valueParameterList)
    }

    @OptIn(KaExperimentalApi::class, KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    private fun generateParameterList(
        project: Project,
        psiFactory: KtPsiFactory,
        namedFunction: KtNamedFunction,
        paramsToAdd: List<ParameterModification.Add>
    ): KtParameterList {
        val helper = JvmPsiConversionHelper.getInstance(project)

        val parametersTextList = paramsToAdd.mapIndexed { index, parameter ->
            buildString {
                val jvmType = parameter.jvmType
                val convertType: PsiType = helper.convertType(jvmType)
                append(parameter.name)
                append(": ")
                allowAnalysisOnEdt {
                    allowAnalysisFromWriteAction {
                        analyze(namedFunction) {
                            val kaType = convertType.asKaType(namedFunction)?.let {
                                KaRendererTypeApproximator.TO_DENOTABLE.approximateType(this, it, Variance.IN_VARIANCE)
                            } ?: error("Can't convert type $jvmType")
                            val render = kaType.render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT)
                            append(render)
                        }
                    }
                }
            }
        }
        val newParameterList = psiFactory.createParameterList("(${parametersTextList.joinToString(", ")})")
        newParameterList.parameters.forEach { param ->
            param.annotationEntries.forEach { a ->
                a.typeReference?.run {
                    val fqName = FqName(this.text)
                    if (fqName in (NULLABLE_ANNOTATIONS + NOT_NULL_ANNOTATIONS)) a.delete()
                }
            }
        }

        return newParameterList
    }

    companion object {
        fun create(ktNamedFunction: KtNamedFunction, request: ChangeParametersRequest): ChangeMethodParameters =
            ChangeMethodParameters(ktNamedFunction, request)

        private val LOG = Logger.getInstance(ChangeMethodParameters::class.java)
    }
}

