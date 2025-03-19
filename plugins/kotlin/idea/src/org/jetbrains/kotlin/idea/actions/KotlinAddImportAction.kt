// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.statistics.StatisticsManager
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.idea.KotlinDescriptorIconProvider
import org.jetbrains.kotlin.idea.actions.KotlinAddImportActionInfo.executeListener
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.completion.KotlinStatisticsInfo
import org.jetbrains.kotlin.idea.completion.isDeprecatedAtCallSite
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.imports.getConstructors
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.quickfix.ImportComparablePriority
import org.jetbrains.kotlin.idea.quickfix.ImportFixHelper
import org.jetbrains.kotlin.idea.quickfix.ImportPrioritizer
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference.ShorteningMode
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.application.underModalProgressOrUnderWriteActionWithNonCancellableProgressInDispatchThread
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isOneSegmentFQN
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import javax.swing.Icon

internal fun createSingleImportAction(
    project: Project,
    editor: Editor,
    element: KtElement,
    fqNames: Collection<FqName>
): KotlinAddImportAction {
    val file = element.containingKtFile
    val prioritizer = createPrioritizerForFile(file)
    val expressionWeigher = ExpressionWeigher.createWeigher(element)

    val variants = fqNames.asSequence().mapNotNull { fqName ->
        val sameFqNameDescriptors = file.resolveImportReference(fqName)
        createVariantWithPriority(fqName, sameFqNameDescriptors, prioritizer, expressionWeigher, project)
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
    val prioritizer = createPrioritizerForFile(file)
    val expressionWeigher = ExpressionWeigher.createWeigher(element)

    val variants = fqNames.asSequence().mapNotNull { fqName ->
        val sameFqNameDescriptors = file.resolveImportReference(fqName.parent())
            .filterIsInstance<ClassifierDescriptor>()
            .flatMap { it.getConstructors() }
        createVariantWithPriority(fqName, sameFqNameDescriptors, prioritizer, expressionWeigher, project)
    }

    return KotlinAddImportAction(project, editor, element, variants)
}

private fun createVariantWithPriority(
    fqName: FqName,
    sameFqNameDescriptors: Collection<DeclarationDescriptor>,
    prioritizer: ImportPrioritizer,
    expressionWeigher: ExpressionWeigher,
    project: Project
): VariantWithPriority? {
    val descriptorsWithPriority =
        sameFqNameDescriptors.map { it to createDescriptorPriority(prioritizer, expressionWeigher, it) }.sortedBy { it.second }
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
    val prioritizer = createPrioritizerForFile(file)
    val expressionWeigher = ExpressionWeigher.createWeigher(element)

    val variants = fqNames.groupBy { it.parentOrNull() ?: FqName.ROOT }.asSequence().map {
        val samePackageFqNames = it.value
        val descriptors = samePackageFqNames.flatMap { fqName -> file.resolveImportReference(fqName) }
        val variant = if (samePackageFqNames.size > 1) {
            GroupedImportVariant(autoImportDescription, descriptors, project)
        } else {
            SingleImportVariant(samePackageFqNames.first(), descriptors, project)
        }

        val priority = createDescriptorGroupPriority(prioritizer, expressionWeigher, descriptors)
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
    element: KtElement,
    private val variants: Sequence<VariantWithPriority>
) : QuestionAction {
    private var singleImportVariant: DescriptorBasedAutoImportVariant? = null

    private val elementPointer = element.createSmartPointer()

    private fun variantsList(): List<DescriptorBasedAutoImportVariant> {
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
                runReadAction {
                    variantsList()
                }
            }.orEmpty()
        }
    }

    fun showHint(): Boolean {
        val element = elementPointer.element ?: return false
        val iterator = variants.iterator()
        if (!iterator.hasNext()) return false

        val first = iterator.next().variant
        val multiple = if (iterator.hasNext()) {
            true
        } else {
            singleImportVariant = first
            false
        }

        val hintText = ShowAutoImportPass.getMessage(multiple, getKind(first.declarationToImport), first.hint)
        HintManager.getInstance().showQuestionHint(editor, hintText, element.startOffset, element.endOffset, this)

        return true
    }

    private fun getKind(element: PsiElement?): String? {
        if (element is PsiClass) {
            return KotlinBundle.message("text.class")
        }
        if (element is PsiField) {
            return KotlinBundle.message("text.property")
        }
        if (element is PsiMethod) {
            return KotlinBundle.message("text.function")
        }
        return null
    }

    override fun execute(): Boolean {
        val element = elementPointer.element ?: return false
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        if (!element.isValid) return false

        val variantsList = variantsList()
        element.containingKtFile.executeListener?.onExecute(variantsList)

        if (variantsList.isEmpty()) return false

        if (variantsList.size == 1 || isUnitTestMode()) {
            addImport(variantsList.first())
            return true
        }

        ImportFixHelper.createListPopupWithImportVariants(project, variantsList, ::addImport).showInBestPositionFor(editor)

        return true
    }

    private fun addImport(variant: AutoImportVariant) {
        require(variant is DescriptorBasedAutoImportVariant)
        val element = elementPointer.element ?: return

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

internal data class VariantWithPriority(val variant: DescriptorBasedAutoImportVariant, val priority: ImportComparablePriority)

internal fun createPrioritizerForFile(file: KtFile, compareNames: Boolean = true): ImportPrioritizer {
    val isImportedByDefault = { fqName: FqName ->
        ImportInsertHelper.getInstance(file.project).isImportedWithDefault(ImportPath(fqName, isAllUnder = false), file)
    }
    return ImportPrioritizer(file, isImportedByDefault, compareNames)
}

internal fun createDescriptorPriority(
    prioritizer: ImportPrioritizer,
    expressionWeigher: ExpressionWeigher,
    descriptor: DeclarationDescriptor
): ImportPrioritizer.Priority {
    val languageVersionSettings = prioritizer.file.languageVersionSettings

    return prioritizer.Priority(
        declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(prioritizer.file.project, descriptor),
        statisticsInfo = KotlinStatisticsInfo.forDescriptor(descriptor),
        isDeprecated = isDeprecatedAtCallSite(descriptor) { languageVersionSettings },
        fqName = descriptor.importableFqName ?: error("Unexpected null for fully-qualified name of importable declaration"),
        expressionWeight = expressionWeigher.weigh(descriptor),
    )
}

internal fun createDescriptorGroupPriority(
    prioritizer: ImportPrioritizer,
    expressionWeigher: ExpressionWeigher,
    descriptors: List<DeclarationDescriptor>
): ImportPrioritizer.GroupPriority =
    prioritizer.GroupPriority(descriptors.map { createDescriptorPriority(prioritizer, expressionWeigher, it) })

internal abstract class DescriptorBasedAutoImportVariant(
    val descriptorsToImport: Collection<DeclarationDescriptor>,
    override val fqName: FqName,
    project: Project,
) : AutoImportVariant {
    override val declarationToImport: PsiElement? = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptorsToImport.first())
    override val icon: Icon? = KotlinDescriptorIconProvider.getIcon(descriptorsToImport.first(), declarationToImport, 0)

    override val debugRepresentation: String
        get() = descriptorsToImport.first().variantNameForDebug()

    companion object {
        private val debugRenderer = DescriptorRenderer.DEBUG_TEXT.withOptions {
            annotationFilter = { false }
        }

        private fun DeclarationDescriptor.variantNameForDebug() = when (this) {
            is ClassDescriptor ->
                fqNameOrNull()?.toString()?.let { "class $it" } ?: debugRenderer.render(this)

            else ->
                debugRenderer.render(this)
        }
    }
}

private class GroupedImportVariant(
    val autoImportDescription: String,
    descriptors: Collection<DeclarationDescriptor>,
    project: Project
) : DescriptorBasedAutoImportVariant(
    descriptorsToImport = descriptors,
    fqName = descriptors.first().importableFqName!!.parent(),
    project = project,
) {
    override val hint: String get() = KotlinBundle.message("0.from.1", autoImportDescription, fqName)
}

private class SingleImportVariant(
    fqName: FqName,
    descriptors: Collection<DeclarationDescriptor>,
    project: Project
) : DescriptorBasedAutoImportVariant(
    descriptorsToImport = listOf(
        descriptors.singleOrNull()
            ?: descriptors.minByOrNull { if (it is ClassDescriptor) 0 else 1 }
            ?: error("we create the class with not-empty descriptors always")
    ),
    fqName = fqName,
    project = project,
) {
    override val hint: String get() = fqName.asString()
}