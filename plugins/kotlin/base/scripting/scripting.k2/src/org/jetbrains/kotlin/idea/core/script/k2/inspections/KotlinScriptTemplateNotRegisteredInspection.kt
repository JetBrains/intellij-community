// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptTemplatesFromDependenciesCache
import org.jetbrains.kotlin.idea.core.script.k2.settings.ScriptDefinitionSettingsStateComponent
import org.jetbrains.kotlin.idea.core.script.k2.settings.parseExplicitTemplateInput
import org.jetbrains.kotlin.idea.core.script.k2.settings.parsedClassNames
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtVisitorVoid
import kotlin.io.path.absolutePathString

private val KOTLIN_SCRIPT_ANNOTATION_FQN = FqName("kotlin.script.experimental.annotations.KotlinScript")

private const val MARKER_RELATIVE_DIR = "META-INF/kotlin/script/templates"

internal class KotlinScriptTemplateNotRegisteredInspection :
    KotlinApplicableInspectionBase<KtClass, KotlinScriptTemplateNotRegisteredInspection.Context>() {

    data class Context(val fqName: String, val needsRegister: Boolean, val needsMarker: Boolean)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            visitTargetElement(klass, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtClass): Boolean = element.annotationEntries.any {
        it.shortName == Name.identifier("KotlinScript")
    }

    override fun getApplicableRanges(element: KtClass): List<TextRange> = ApplicabilityRange.single(element) { it.nameIdentifier }

    override fun KaSession.prepareContext(element: KtClass): Context? {
        if (element.symbol.annotations.none {
            it.classId?.asSingleFqName() == KOTLIN_SCRIPT_ANNOTATION_FQN
        }) return null

        val fqName = element.fqName?.asString() ?: return null

        val needsRegister = !isActive(element.project, fqName)
        val module = ModuleUtilCore.findModuleForPsiElement(element)
        val needsMarker = module != null && !markerFileExists(module, fqName)
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

    private fun isActive(project: Project, fqName: String): Boolean {
        val state = ScriptDefinitionSettingsStateComponent.getInstance(project).state
        if (fqName in state.parsedClassNames) return true
        if (fqName in ScriptTemplatesFromDependenciesCache.getOrDiscover(project).fqns) return true
        return false
    }

    private fun markerFileExists(module: Module, fqn: String): Boolean =
        ModuleRootManager.getInstance(module).getSourceRoots(JavaResourceRootType.RESOURCE).any { resourceRoot ->
            resourceRoot.findFileByRelativePath("$MARKER_RELATIVE_DIR/$fqn.classname") != null
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
        ScriptDefinitionSettingsStateComponent.getInstance(project).update { state ->
            val mergedFqns = (parseExplicitTemplateInput(state.explicitTemplateClassNames) + fqn).distinct().joinToString("\n")
            val mergedClasspath =
                (parseExplicitTemplateInput(state.explicitTemplateClasspath) + moduleClasspath).distinct().joinToString("\n")
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
        val resourceRoot = ModuleRootManager.getInstance(module).getSourceRoots(JavaResourceRootType.RESOURCE).firstOrNull() ?: return
        WriteAction.run<RuntimeException> {
            val templatesDir = VfsUtil.createDirectoryIfMissing(resourceRoot, MARKER_RELATIVE_DIR) ?: return@run
            val markerName = "$fqn.classname"
            if (templatesDir.findChild(markerName) == null) {
                templatesDir.createChildData(this, markerName)
            }
        }
    }
}
