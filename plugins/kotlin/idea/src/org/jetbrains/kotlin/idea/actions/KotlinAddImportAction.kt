// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.WeighingService
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.util.ProximityLocation
import com.intellij.psi.util.proximity.PsiProximityComparator
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.backend.jvm.ir.psiElement
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinDescriptorIconProvider
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.completion.ImportableFqNameClassifier
import org.jetbrains.kotlin.idea.completion.KotlinStatisticsInfo
import org.jetbrains.kotlin.idea.completion.isDeprecatedAtCallSite
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.dfa.getArrayElementType
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference.ShorteningMode
import org.jetbrains.kotlin.idea.util.application.underModalProgressOrUnderWriteActionWithNonCancellableProgressInDispatchThread
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isOneSegmentFQN
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.ListCellRenderer

internal fun createSingleImportAction(
    project: Project,
    editor: Editor,
    element: KtElement,
    fqNames: Collection<FqName>
): KotlinAddImportAction {
    val file = element.containingKtFile
    val prioritizer = Prioritizer(file, element.parent as? KtCallExpression)
    val variants = fqNames.asSequence().mapNotNull {fqName ->
        val sameFqNameDescriptors = file.resolveImportReference(fqName)
        createVariantWithPriority(fqName, sameFqNameDescriptors, file, prioritizer, project)
    }.sortedWith(compareBy({ it.priority }, { it.variant.hint }))

    return KotlinAddImportAction(project, editor, element, variants)
}

internal fun createSingleImportActionForConstructor(
    project: Project,
    editor: Editor,
    element: KtElement,
    fqNames: Collection<FqName>
): KotlinAddImportAction {
    val file = element.containingKtFile
    val prioritizer = Prioritizer(file)
    val variants = fqNames.asSequence().mapNotNull { fqName ->
        val sameFqNameDescriptors = file.resolveImportReference(fqName.parent())
            .filterIsInstance<ClassDescriptor>()
            .flatMap { it.constructors }
        createVariantWithPriority(fqName, sameFqNameDescriptors, file, prioritizer, project)
    }

    return KotlinAddImportAction(project, editor, element, variants)
}

private fun createVariantWithPriority(
    fqName: FqName,
    sameFqNameDescriptors: Collection<DeclarationDescriptor>,
    file: KtFile,
    prioritizer: Prioritizer,
    project: Project
):VariantWithPriority? {
    val descriptorsWithPriority =
        sameFqNameDescriptors.map { it to prioritizer.priority(it, file.languageVersionSettings) }.sortedBy { it.second }
    val priority = descriptorsWithPriority.firstOrNull()?.second ?: return null

    return VariantWithPriority(SingleImportVariant(fqName, descriptorsWithPriority.map { it.first }, project), priority)
}

internal fun createGroupedImportsAction(
    project: Project,
    editor: Editor,
    element: KtElement,
    autoImportDescription: String,
    fqNames: Collection<FqName>
): KotlinAddImportAction {
    val file = element.containingKtFile
    val prioritizer = DescriptorGroupPrioritizer(file)
    val variants = fqNames.groupBy { it.parentOrNull() ?: FqName.ROOT }.asSequence().map {
        val samePackageFqNames = it.value
        val descriptors = samePackageFqNames.flatMap { fqName -> file.resolveImportReference(fqName) }
        val variant = if (samePackageFqNames.size > 1) {
            GroupedImportVariant(autoImportDescription, descriptors, project)
        } else {
            SingleImportVariant(samePackageFqNames.first(), descriptors, project)
        }

        val priority = prioritizer.priority(descriptors, file.languageVersionSettings)
        VariantWithPriority(variant, priority)
    }

    return KotlinAddImportAction(project, editor, element, variants)
}

/**
 * Automatically adds import directive to the file for resolving reference.
 * Based on {@link AddImportAction}
 */
class KotlinAddImportAction internal constructor(
    private val project: Project,
    private val editor: Editor,
    private val element: KtElement,
    private val variants: Sequence<VariantWithPriority>
) : QuestionAction {
    private var singleImportVariant: AutoImportVariant? = null

    private fun variantsList(): List<AutoImportVariant> {
        if (singleImportVariant != null && !isUnitTestMode()) return listOf(singleImportVariant!!)

        val variantsList = {
            runReadAction {
                variants.sortedBy { it.priority }.map { it.variant }.toList()
            }
        }
        return if (isUnitTestMode()) {
            variantsList()
        } else {
            project.runSynchronouslyWithProgress(KotlinBundle.message("import.progress.text.resolve.imports"), true) {
                variantsList()
            }.orEmpty()
        }
    }

    fun showHint(): Boolean {
        val iterator = variants.iterator()
        if (!iterator.hasNext()) return false

        val first = iterator.next().variant
        val multiple = if (iterator.hasNext()) {
            true
        } else {
            singleImportVariant = first
            false
        }

        val hintText = ShowAutoImportPass.getMessage(multiple, first.hint)
        HintManager.getInstance().showQuestionHint(editor, hintText, element.startOffset, element.endOffset, this)

        return true
    }

    override fun execute(): Boolean {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        if (!element.isValid) return false

        val variantsList = variantsList()
        KotlinAddImportActionInfo.executeListener?.onExecute(variantsList.map { it.descriptorsToImport.toList() })

        if (variantsList.isEmpty()) return false

        if (variantsList.size == 1 || isUnitTestMode()) {
            addImport(variantsList.first())
            return true
        }

        JBPopupFactory.getInstance().createListPopup(project, getVariantSelectionPopup(variantsList)) {
            val psiRenderer = DefaultPsiElementCellRenderer()

            ListCellRenderer<AutoImportVariant> { list, value, index, isSelected, cellHasFocus ->
                JPanel(BorderLayout()).apply {
                    add(
                        psiRenderer.getListCellRendererComponent(
                            list,
                            value.declarationToImport,
                            index,
                            isSelected,
                            cellHasFocus
                        )
                    )
                }
            }
        }.showInBestPositionFor(editor)

        return true
    }

    private fun getVariantSelectionPopup(variants: List<AutoImportVariant>): BaseListPopupStep<AutoImportVariant> {
        return object : BaseListPopupStep<AutoImportVariant>(KotlinBundle.message("action.add.import.chooser.title"), variants) {
            override fun isAutoSelectionEnabled() = false

            override fun isSpeedSearchEnabled() = true

            override fun onChosen(selectedValue: AutoImportVariant?, finalChoice: Boolean): PopupStep<String>? {
                if (selectedValue == null || project.isDisposed) return null

                if (finalChoice) {
                    addImport(selectedValue)
                    return null
                }

                val toExclude = AddImportAction.getAllExcludableStrings(selectedValue.excludeFqNameCheck.asString())

                return object : BaseListPopupStep<String>(null, toExclude) {
                    override fun getTextFor(value: String): String {
                        return KotlinBundle.message("fix.import.exclude", value)
                    }

                    override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<Any>? {
                        if (finalChoice && !project.isDisposed) {
                            AddImportAction.excludeFromImport(project, selectedValue)
                        }
                        return null
                    }
                }
            }

            override fun hasSubstep(selectedValue: AutoImportVariant?) = true
            override fun getTextFor(value: AutoImportVariant) = value.hint
            override fun getIconFor(value: AutoImportVariant) = value.icon
        }
    }

    private fun addImport(variant: AutoImportVariant) {
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        psiDocumentManager.commitAllDocuments()

        project.executeWriteCommand(QuickFixBundle.message("add.import")) {
            if (!element.isValid) return@executeWriteCommand

            val file = element.containingKtFile

            val statisticsManager = StatisticsManager.getInstance()

            variant.descriptorsToImport.forEach { descriptor ->
                val statisticsInfo = KotlinStatisticsInfo.forDescriptor(descriptor)
                statisticsManager.incUseCount(statisticsInfo)

                // for class or package we use ShortenReferences because we not necessary insert an import but may want to
                // insert partly qualified name

                val importableFqName = descriptor.importableFqName
                val importAlias = importableFqName?.let { file.findAliasByFqName(it) }
                if (importableFqName?.isOneSegmentFQN() != true &&
                    (importAlias != null || descriptor is ClassDescriptor || descriptor is PackageViewDescriptor)
                ) {
                    if (element is KtSimpleNameExpression) {
                        if (importAlias != null) {
                            importAlias.nameIdentifier?.copy()?.let { element.getIdentifier()?.replace(it) }
                            val resultDescriptor = element.resolveMainReferenceToDescriptors().firstOrNull()
                            if (importableFqName == resultDescriptor?.importableFqName) {
                                return@forEach
                            }
                        }

                        if (importableFqName != null) {
                            underModalProgressOrUnderWriteActionWithNonCancellableProgressInDispatchThread(
                                project,
                                progressTitle = KotlinBundle.message("add.import.for.0", importableFqName.asString()),
                                computable = { element.mainReference.bindToFqName(importableFqName, ShorteningMode.FORCED_SHORTENING) }
                            )
                        }
                    }
                } else {
                    ImportInsertHelper.getInstance(project).importDescriptor(file, descriptor)
                }
            }
        }
    }
}

internal interface ComparablePriority : Comparable<ComparablePriority>

internal data class VariantWithPriority(val variant: AutoImportVariant, val priority: ComparablePriority)

internal class CallExpressionWeigher(callExpression: KtCallExpression?) {

    private val kotlinTypes: List<KotlinType>?
    private val valueArgumentsSize: Int

    init {
        val valueArgumentList = callExpression?.valueArgumentList
        val valueArguments = callExpression?.valueArguments
        valueArgumentsSize = valueArguments?.size ?: 0

        kotlinTypes = if (valueArgumentList != null && valueArguments != null) {
            val bindingContext = valueArgumentList.analyze(BodyResolveMode.PARTIAL)

            val types = ArrayList<KotlinType>(valueArgumentsSize)
            for (valueArgument in valueArguments) {
                val argumentExpression = valueArgument.getArgumentExpression() ?: break
                types += argumentExpression.getType(bindingContext) ?: break
            }
            types
        } else {
            null
        }
    }

    fun weigh(descriptor: DeclarationDescriptor): Int {
        val base = descriptor.importableFqName?.asString()?.let { fqName ->
            when {
                // kotlin packages are higher than java, but java is higher than any 3rd party library
                fqName.startsWith("kotlin.") || fqName.startsWith("kotlinx.") -> 5
                fqName.startsWith("java.") -> 2
                descriptor is DeclarationDescriptorWithSource -> run {
                    val psiElement = descriptor.psiElement
                    val virtualFile = psiElement?.containingFile?.virtualFile ?: return@run 0
                    val fileIndex = ProjectRootManager.getInstance(psiElement.project).fileIndex
                    // project source higher than libs
                    if (fileIndex.isInSourceContent(virtualFile)) 7 else 0
                }
                else -> 0
            }
        } ?: 0

        if (kotlinTypes == null) return base

        val callableMemberDescriptor = descriptor as? CallableMemberDescriptor ?: return base

        val descriptorParameters = callableMemberDescriptor.valueParameters.size
        val descriptorHasVarargParameter = callableMemberDescriptor.valueParameters.any(ValueParameterDescriptor::isVararg)

        var weight = if (descriptorParameters >= valueArgumentsSize || descriptorHasVarargParameter) {
            // same number of arguments is better than when more arguments
            if (descriptorParameters == valueArgumentsSize || descriptorHasVarargParameter) 1 else 0
        } else {
            // apply only base weigh if target has fewer parameters than expected
            return base
        }
        val typeChecker = KotlinTypeChecker.DEFAULT

        val valueParametersIterator = callableMemberDescriptor.valueParameters.iterator()
        var valueParameterDescriptor: ValueParameterDescriptor? = null

        // TODO: it does not cover following cases:
        //  - named parameters
        //  - default values

        for (kotlinType in kotlinTypes) {
            if (!valueParametersIterator.hasNext()) {
                break
            }
            if (valueParameterDescriptor == null || !valueParameterDescriptor.isVararg) {
                // vararg could be only the last parameter, there is no parameters left
                valueParameterDescriptor = valueParametersIterator.next()
            }

            // replace `<T>` but `<*>` if needed, otherwise `<T>` has no subtypes
            val returnType = valueParameterDescriptor?.returnType?.replaceArgumentsWithStarProjections()
            val vararg = valueParameterDescriptor?.isVararg == true
            val valueParameterType = if (vararg) {
                // `vararg a: Int` has type `IntArray`
                returnType?.getArrayElementType()
            } else {
                returnType
            }

            if (valueParameterType == null ||
                !(typeChecker.isSubtypeOf(kotlinType, valueParameterType) ||
                        vararg && returnType != null && typeChecker.isSubtypeOf(kotlinType, returnType))
            ) {
                break
            }
            weight = 100 * weight + 10
        }

        return base + weight
    }
}

internal class Prioritizer(
    private val file: KtFile,
    callExpression: KtCallExpression? = null,
    private val compareNames: Boolean = true
) {
    private val classifier = ImportableFqNameClassifier(file){
        ImportInsertHelper.getInstance(file.project).isImportedWithDefault(ImportPath(it, false), file)
    }
    private val statsManager = StatisticsManager.getInstance()
    private val proximityLocation = ProximityLocation(file, file.module)
    private val callExpressionWeigher = CallExpressionWeigher(callExpression)

    inner class Priority(descriptor: DeclarationDescriptor, languageVersionSettings: LanguageVersionSettings) : ComparablePriority {
        private val isDeprecated = isDeprecatedAtCallSite(descriptor) { languageVersionSettings }
        private val fqName = descriptor.importableFqName!!
        private val classification = classifier.classify(fqName, false)
        private val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(file.project, descriptor)
        private val lastUseRecency = statsManager.getLastUseRecency(KotlinStatisticsInfo.forDescriptor(descriptor))
        private val proximityWeight = WeighingService.weigh(PsiProximityComparator.WEIGHER_KEY, declaration, proximityLocation)
        private val callExpressionWeigh = callExpressionWeigher.weigh(descriptor)

        override fun compareTo(other: ComparablePriority): Int {
            other as Priority

            if (isDeprecated != other.isDeprecated) {
                return if (isDeprecated) +1 else -1
            }

            val c1 = classification.compareTo(other.classification)
            if (c1 != 0) return c1

            val c2 = other.callExpressionWeigh.compareTo(callExpressionWeigh)
            if (c2 != 0) return c2

            val c3 = lastUseRecency.compareTo(other.lastUseRecency)
            if (c3 != 0) return c3

            val c4 = proximityWeight.compareTo(other.proximityWeight)
            if (c4 != 0) return -c4 // n.b. reversed

            return if (compareNames) {
                fqName.asString().compareTo(other.fqName.asString())
            } else {
                0
            }
        }
    }

    fun priority(
        descriptor: DeclarationDescriptor,
        languageVersionSettings: LanguageVersionSettings,
    ) = Priority(descriptor, languageVersionSettings)
}

private class DescriptorGroupPrioritizer(file: KtFile) {
    private val prioritizer = Prioritizer(file, compareNames = false)

    inner class Priority(
        val descriptors: List<DeclarationDescriptor>,
        languageVersionSettings: LanguageVersionSettings
    ) : ComparablePriority {
        val ownDescriptorsPriority = descriptors.maxOf { prioritizer.priority(it, languageVersionSettings) }

        override fun compareTo(other: ComparablePriority): Int {
            other as Priority

            val c1 = ownDescriptorsPriority.compareTo(other.ownDescriptorsPriority)
            if (c1 != 0) return c1

            return other.descriptors.size - descriptors.size
        }
    }

    fun priority(
        descriptors: List<DeclarationDescriptor>,
        languageVersionSettings: LanguageVersionSettings
    ) = Priority(descriptors, languageVersionSettings)
}

internal abstract class AutoImportVariant(
    val descriptorsToImport: Collection<DeclarationDescriptor>,
    val excludeFqNameCheck: FqName,
    project: Project,
) {
    abstract val hint: String
    val declarationToImport: PsiElement? = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptorsToImport.first())
    val icon: Icon? = KotlinDescriptorIconProvider.getIcon(descriptorsToImport.first(), declarationToImport, 0)
}

private class GroupedImportVariant(
    val autoImportDescription: String,
    descriptors: Collection<DeclarationDescriptor>,
    project: Project
) : AutoImportVariant(
    descriptorsToImport = descriptors,
    excludeFqNameCheck = descriptors.first().importableFqName!!.parent(),
    project = project,
) {
    override val hint: String get() = KotlinBundle.message("0.from.1", autoImportDescription, excludeFqNameCheck)
}

private class SingleImportVariant(
    excludeFqNameCheck: FqName,
    descriptors: Collection<DeclarationDescriptor>,
    project: Project
) : AutoImportVariant(
    descriptorsToImport = listOf(
        descriptors.singleOrNull()
            ?: descriptors.minByOrNull { if (it is ClassDescriptor) 0 else 1 }
            ?: error("we create the class with not-empty descriptors always")
    ),
    excludeFqNameCheck = excludeFqNameCheck,
    project = project,
) {
    override val hint: String get() = excludeFqNameCheck.asString()
}

/** Test hooks allowing inspection of data used for KotlinAddImportAction. **/
object KotlinAddImportActionInfo {
    interface ExecuteListener {
        fun onExecute(variants: List<List<DeclarationDescriptor>>)
    }

    @Volatile
    internal var executeListener: ExecuteListener? = null

    @TestOnly
    fun setExecuteListener(disposable: Disposable, listener: ExecuteListener) {
        assert(executeListener == null)
        executeListener = listener
        Disposer.register(disposable) { executeListener = null }
    }
}
