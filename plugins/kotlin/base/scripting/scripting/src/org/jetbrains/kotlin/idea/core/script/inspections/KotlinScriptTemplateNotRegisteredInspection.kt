// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.core.script.definitions.KOTLIN_SCRIPT_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.idea.core.script.definitions.createAndReportScriptTemplateMarkerFile
import org.jetbrains.kotlin.idea.core.script.definitions.findScriptTemplateMarkerFile
import org.jetbrains.kotlin.idea.core.script.definitions.hasKotlinScriptAnnotation
import org.jetbrains.kotlin.idea.core.script.definitions.isScriptTemplateActive
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.core.script.settings.parseClasspathInput
import org.jetbrains.kotlin.idea.core.script.settings.parseExplicitTemplateInput
import org.jetbrains.kotlin.idea.core.script.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtVisitorVoid
import kotlin.io.path.absolutePathString

internal class KotlinScriptTemplateNotRegisteredInspection :
    KotlinApplicableInspectionBase<KtClass, KotlinScriptTemplateNotRegisteredInspection.Context>() {

    data class Context(val fqName: String, val needsRegister: Boolean, val needsMarker: Boolean)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            visitTargetElement(klass, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtClass): Boolean = hasKotlinScriptAnnotation(element)

    override fun getApplicableRanges(element: KtClass): List<TextRange> = ApplicabilityRange.single(element) { it.nameIdentifier }

    context(session: KaSession)
    override fun prepareContext(element: KtClass): Context? {
        if (element.symbol.annotations.none {
            it.classId?.asSingleFqName() == KOTLIN_SCRIPT_ANNOTATION_FQ_NAME
        }) return null

        val fqName = element.fqName?.asString() ?: return null

        val needsRegister = !isScriptTemplateActive(element.project, fqName)
        val module = ModuleUtilCore.findModuleForPsiElement(element)
        val needsMarker = module != null && findScriptTemplateMarkerFile(module, fqName) == null
        if (!needsRegister && !needsMarker) return null
        return Context(fqName, needsRegister, needsMarker)
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtClass,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor {
        val descriptionKey = when {
            context.needsRegister && context.needsMarker -> "inspection.script.definition.not.registered.description.both"
            context.needsRegister -> "inspection.script.definition.not.registered.description.register"
            else -> "inspection.script.definition.not.registered.description.marker"
        }
        val fixes = buildList {
            if (context.needsRegister) add(RegisterScriptDefinitionFix(context.fqName))
            if (context.needsMarker) add(CreateMarkerFileFix(context.fqName))
        }.toTypedArray()

        return createProblemDescriptor(
            element,
            rangeInElement,
            KotlinBaseScriptingBundle.message(descriptionKey),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            onTheFly,
            *fixes,
        )
    }

}

private class RegisterScriptDefinitionFix(private val fqn: String) : LocalQuickFix {
    override fun getName(): String = KotlinBaseScriptingBundle.message("inspection.script.definition.register.fix")
    override fun getFamilyName(): String = name

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.Html(
        KotlinBaseScriptingBundle.message(
            "inspection.script.definition.register.fix.preview", fqn.substringAfterLast(".")
        )
    )

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val module = ModuleUtilCore.findModuleForPsiElement(descriptor.psiElement) ?: return
        val moduleClasspath = OrderEnumerator.orderEntries(module).withoutSdk().classesRoots.mapNotNull {
            val local = VfsUtil.getLocalFile(it)
            local.fileSystem.getNioPath(local)?.absolutePathString()
        }
        KotlinScriptingSettings.getInstance(project).update { state ->
            val mergedFqns = (parseExplicitTemplateInput(state.explicitTemplateClassNames) + fqn).distinct().joinToString("\n")
            val mergedClasspath =
                (parseClasspathInput(state.explicitTemplateClasspath) + moduleClasspath).distinct().joinToString("\n")
            state.copy(
                explicitTemplateClassNames = mergedFqns, explicitTemplateClasspath = mergedClasspath
            )
        }
    }
}

private class CreateMarkerFileFix(private val fqn: String) : LocalQuickFix {
    override fun getName(): String = KotlinBaseScriptingBundle.message("inspection.script.definition.create.marker.fix")
    override fun getFamilyName(): String = name

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.Html(
        KotlinBaseScriptingBundle.message(
            "inspection.script.definition.create.marker.fix.preview", "${fqn.substringAfterLast(".")}.classname"
        )
    )

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val module = ModuleUtilCore.findModuleForPsiElement(descriptor.psiElement) ?: return
        createAndReportScriptTemplateMarkerFile(project, module, fqn)
    }
}
