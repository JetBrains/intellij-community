// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.refactoring.rename.inplace.MyLookupExpression
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.collectDiagnostics
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.findClass
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object MapPlatformClassToKotlinFixFactories {
    val fixFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.PlatformClassMappedToKotlin> =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.PlatformClassMappedToKotlin ->
            listOfNotNull(createQuickFix(diagnostic))
        }

    context(_: KaSession)
    private fun createQuickFix(diagnostic: KaFirDiagnostic.PlatformClassMappedToKotlin): IntentionAction? {
        val referenceExpression = getImportOrUsageFromDiagnostic(diagnostic) ?: return null
        val platformClassId = mapKotlinClassToPlatformClassId(diagnostic.kotlinClass) ?: return null
        val possibleKotlinClasses = computePossibleKotlinClasses(platformClassId, diagnostic.kotlinClass)
        val replacementTargets = collectReplacementTargets(referenceExpression.containingKtFile, platformClassId)
        return MapPlatformClassToKotlinFix(
            referenceExpression,
            replacementTargets,
            possibleKotlinClasses,
            renderClassWithTypeParameters(platformClassId),
            possibleKotlinClasses.map { renderClassWithTypeParameters(it) },
        )
    }
}

internal class MapPlatformClassToKotlinFix(
    element: KtReferenceExpression,
    private val replacementTargets: ReplacementTargets,
    private val possibleKotlinClasses: List<ClassId>,
    private val platformClassQualifiedName: String,
    private val possibleClassQualifiedNames: List<String>,
) : KotlinQuickFixAction<KtReferenceExpression>(element) {

    override fun getText(): @IntentionName String {
        val singleClassQualifiedName = possibleClassQualifiedNames.singleOrNull()
        return if (singleClassQualifiedName != null) {
            KotlinBundle.message(
                "change.all.usages.of.0.in.this.file.to.1",
                platformClassQualifiedName,
                singleClassQualifiedName,
            )
        } else {
            KotlinBundle.message("change.all.usages.of.0.in.this.file.to.a.kotlin.class", platformClassQualifiedName)
        }
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("change.to.kotlin.class")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val primaryClassId = possibleKotlinClasses.firstOrNull() ?: return
        replacementTargets.imports.mapNotNull { it.element }.forEach { it.delete() }

        val usages = replacementTargets.usages.mapNotNull { it.element }
        if (usages.isEmpty()) return

        val replacedExpressions = replaceUsagesWithFirstClass(project, usages, primaryClassId)

        if (possibleKotlinClasses.size > 1 && editor != null && replacedExpressions.isNotEmpty()) {
            val options = LinkedHashSet<String>()
            possibleKotlinClasses.forEach { options.add(it.shortClassName.asString()) }
            buildAndShowTemplate(project, editor, file, replacedExpressions.mapNotNull { it.element }, options)
        }
    }
}

internal data class ReplacementTargets(
    val imports: List<SmartPsiElementPointer<KtImportDirective>>,
    val usages: List<SmartPsiElementPointer<KtUserType>>,
)

context(_: KaSession)
private fun collectReplacementTargets(file: KtFile, platformClassId: ClassId): ReplacementTargets {
    val imports = ArrayList<KtImportDirective>()
    val usages = ArrayList<KtUserType>()

    for (diagnostic in file.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)) {
        if (diagnostic !is KaFirDiagnostic.PlatformClassMappedToKotlin) continue

        val referenceExpression = getImportOrUsageFromDiagnostic(diagnostic) ?: continue
        if (mapKotlinClassToPlatformClassId(diagnostic.kotlinClass) != platformClassId) continue

        val importDirective = referenceExpression.getStrictParentOfType<KtImportDirective>()
        if (importDirective != null) {
            imports.add(importDirective)
        } else {
            usages.add(referenceExpression.getStrictParentOfType<KtUserType>() ?: continue)
        }
    }

    return ReplacementTargets(
        imports = imports.distinctBy { it.textOffset }.sortedBy { it.textOffset }.map { it.createSmartPointer() },
        usages = usages.distinctBy { it.textOffset }.sortedBy { it.textOffset }.map { it.createSmartPointer() },
    )
}

context(_: KaSession)
private fun computePossibleKotlinClasses(platformClassId: ClassId, preferredKotlinClassId: ClassId): List<ClassId> {
    val mutabilityMapping = JavaToKotlinClassMap.mutabilityMappings.firstOrNull { it.javaClass == platformClassId }
    return LinkedHashSet<ClassId>().apply {
        add(preferredKotlinClassId)
        if (mutabilityMapping != null) {
            add(mutabilityMapping.kotlinReadOnly)
            add(mutabilityMapping.kotlinMutable)
        }
    }.toList()
}

private fun mapKotlinClassToPlatformClassId(kotlinClassId: ClassId): ClassId? {
    return JavaToKotlinClassMap.mapKotlinToJava(kotlinClassId.asSingleFqName().toUnsafe())
}

context(_: KaSession)
private fun renderClassWithTypeParameters(classId: ClassId): String {
    val fqName = classId.asSingleFqName().asString()
    val typeParameters = (findClass(classId) as? KaNamedClassSymbol)?.typeParameters.orEmpty()
    if (typeParameters.isEmpty()) return fqName

    return buildString {
        append(fqName)
        append(typeParameters.joinToString(prefix = "<", postfix = ">", separator = ", ") { it.name.asString() })
    }
}

private fun replaceUsagesWithFirstClass(
    project: Project,
    usages: List<KtUserType>,
    replacementClassId: ClassId,
): List<SmartPsiElementPointer<KtSimpleNameExpression>> {
    val replacementClassName = replacementClassId.shortClassName.asString()
    val psiFactory = KtPsiFactory(project)
    val replacedElements = ArrayList<SmartPsiElementPointer<KtSimpleNameExpression>>()

    for (usage in usages) {
        val typeArgumentsText = usage.typeArgumentList?.text.orEmpty()
        val replacementType = psiFactory.createType(replacementClassName + typeArgumentsText)
        val replacementTypeElement = replacementType.typeElement ?: continue
        val replacedElement = usage.replace(replacementTypeElement)
        val replacedExpression = replacedElement.firstChild as? KtSimpleNameExpression ?: continue
        replacedElements.add(replacedExpression.createSmartPointer())
    }

    return replacedElements
}

private const val PRIMARY_USAGE = "PrimaryUsage"
private const val OTHER_USAGE = "OtherUsage"

private fun buildAndShowTemplate(
    project: Project,
    editor: Editor,
    file: KtFile,
    replacedElements: Collection<PsiElement>,
    options: LinkedHashSet<String>,
) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)

    val primaryReplacedExpression = replacedElements.firstOrNull() ?: return

    val caretModel = editor.caretModel
    val oldOffset = caretModel.offset
    caretModel.moveToOffset(file.node.startOffset)

    val builder = TemplateBuilderImpl(file)
    val expression = MyLookupExpression(
        /* name = */ primaryReplacedExpression.text,
        /* names = */ options,
        /* elementToRename = */ null,
        /* nameSuggestionContext = */ null,
        /* shouldSelectAll = */ false,
        /* advertisement = */ KotlinBundle.message("choose.an.appropriate.kotlin.class"),
    )

    builder.replaceElement(primaryReplacedExpression, PRIMARY_USAGE, expression, true)
    for (replacedExpression in replacedElements) {
        if (replacedExpression === primaryReplacedExpression) continue
        builder.replaceElement(replacedExpression, OTHER_USAGE, PRIMARY_USAGE, false)
    }

    TemplateManager.getInstance(project).startTemplate(
        editor, builder.buildInlineTemplate(), object : TemplateEditingAdapter() {
            override fun templateFinished(template: Template, brokenOff: Boolean) {
                caretModel.moveToOffset(oldOffset)
            }
        })
}

private fun getImportOrUsageFromDiagnostic(diagnostic: KaFirDiagnostic.PlatformClassMappedToKotlin): KtReferenceExpression? {
    val importDirective = diagnostic.psi.getNonStrictParentOfType<KtImportDirective>()
    return if (importDirective != null) {
        importDirective.importedReference?.getQualifiedElementSelector() as? KtReferenceExpression
    } else {
        when (val psi = diagnostic.psi) {
            is KtReferenceExpression -> psi
            is KtUserType -> psi.referenceExpression
            is KtTypeReference -> psi.typeElement.toUserType()?.referenceExpression
            else -> {
                psi.getNonStrictParentOfType<KtUserType>()?.referenceExpression
                    ?: psi.getNonStrictParentOfType<KtTypeReference>()?.typeElement.toUserType()?.referenceExpression
                    ?: psi.findDescendantOfType<KtUserType>()?.referenceExpression
            }
        }
    }
}

private fun KtTypeElement?.toUserType(): KtUserType? {
    return generateSequence(this) { (it as? KtNullableType)?.innerType }.lastOrNull() as? KtUserType
}
