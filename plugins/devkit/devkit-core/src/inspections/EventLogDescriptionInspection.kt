// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.properties.IProperty
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.references.EVENT_LOG_GROUP_FQN
import org.jetbrains.idea.devkit.references.EVENT_LOG_PROPERTIES_DIR
import org.jetbrains.idea.devkit.references.REGISTER_ACTIVITY_NAME
import org.jetbrains.idea.devkit.references.eventLogGroupCall
import org.jetbrains.idea.devkit.references.findEventLogPropertiesFile
import org.jetbrains.idea.devkit.references.findGroupIdAndRecorderName
import org.jetbrains.idea.devkit.references.findRecorderName
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

@VisibleForTesting
@ApiStatus.Internal
class EventLogDescriptionInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val hasFUS = JavaPsiFacade.getInstance(holder.project).findClass(EVENT_LOG_GROUP_FQN, holder.file.resolveScope) != null
    if (!hasFUS)
      return PsiElementVisitor.EMPTY_VISITOR

    val hasDescriptions = holder.project.guessProjectDir()
      ?.findFileByRelativePath(EVENT_LOG_PROPERTIES_DIR)
      ?.let { PsiManager.getInstance(holder.project).findDirectory(it) }
      ?.let { it.files.any { file -> file.name.endsWith(PropertiesFileType.DOT_DEFAULT_EXTENSION) } } == true
    if (!hasDescriptions)
      return PsiElementVisitor.EMPTY_VISITOR

    return UastHintedVisitorAdapter.create(
      holder.file.language,
      EventLogDescriptionInspectionVisitor(holder),
      arrayOf(UCallExpression::class.java),
      directOnly = true
    )
  }
}

private val EVENT_LOG_GROUP_CALL_PATTERN = eventLogGroupCall()

private class EventLogDescriptionInspectionVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
  override fun visitCallExpression(node: UCallExpression): Boolean {
    val argument = node.getArgumentForParameter(0)
    if (argument != null && EVENT_LOG_GROUP_CALL_PATTERN.accepts(node)) {
      if (node.kind == UastCallKind.CONSTRUCTOR_CALL) {
        val recorderArg = node.getArgumentForParameter(2)
        val recorder = findRecorderName(node)
          ?: return warn(recorderArg ?: node, "inspections.event.log.recorder.not.evaluable")
        val file = findEventLogPropertiesFile(holder.project, recorder)
          ?: return error(recorderArg ?: node, "inspections.event.log.recorder.unknown", "${EVENT_LOG_PROPERTIES_DIR}/${recorder}.properties")
        val groupId = argument.evaluateString()?.takeIf { it.isNotBlank() }
          ?: return warn(argument, "inspections.event.log.group.id.not.evaluable")
        val description = file.findPropertyByKey(groupId)?.value ?: return errorWithFix(
          argument,
          "inspections.event.log.group.description.missing", arrayOf(groupId, file.name),
          AddEventLogDescriptionQuickFix(file, groupId)
        )
        if (description.isBlank())
          error(argument, "inspections.event.log.group.description.empty", groupId)
      }
      else {
        val receiver = node.receiver
        val (groupId, recorder) = receiver?.let { findGroupIdAndRecorderName(it) }
          ?: return warn(receiver ?: node, "inspections.event.log.event.group.not.evaluable")
        val eventId = argument.evaluateString()?.takeIf { it.isNotBlank() }
          ?: return warn(argument, "inspections.event.log.event.id.not.evaluable")
        findEventLogPropertiesFile(holder.project, recorder)?.let { file ->
          val base = "${groupId}.${eventId}"
          val keys = if (node.methodName == REGISTER_ACTIVITY_NAME) sequenceOf("${base}.started", "${base}.finished") else sequenceOf(base)
          keys.forEach { key ->
            val description = file.findPropertyByKey(key)?.value
            when {
              description == null -> errorWithFix(
                argument,
                "inspections.event.log.event.description.missing", arrayOf(key, file.name),
                AddEventLogDescriptionQuickFix(file, key)
              )
              description.isBlank() -> error(argument, "inspections.event.log.event.description.empty", key)
            }
          }
        }
      }
    }

    return true
  }

  private fun warn(element: UElement, messageKey: @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) String): Boolean {
    element.sourcePsi?.let {
      holder.registerProblem(it, DevKitBundle.message(messageKey), ProblemHighlightType.WARNING)
    }
    return true
  }

  private fun error(element: UElement, messageKey: @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) String, vararg params: Any): Boolean {
    element.sourcePsi?.let {
      holder.registerProblem(it, DevKitBundle.message(messageKey, *params), ProblemHighlightType.GENERIC_ERROR)
    }
    return true
  }

  private fun errorWithFix(
    element: UElement,
    messageKey: @PropertyKey(resourceBundle = DevKitBundle.BUNDLE) String,
    params: Array<out Any>,
    fix: LocalQuickFix,
  ): Boolean {
    element.sourcePsi?.let {
      holder.registerProblem(it, DevKitBundle.message(messageKey, *params), ProblemHighlightType.GENERIC_ERROR, fix)
    }
    return true
  }
}

private class AddEventLogDescriptionQuickFix(propertiesFile: PropertiesFile, private val key: String) : LocalQuickFix {
  private val file = propertiesFile.virtualFile!!

  override fun getFamilyName(): String = DevKitBundle.message("inspections.event.log.description.fix.family.name")
  override fun getName(): String = DevKitBundle.message("inspections.event.log.description.fix.name", key, file.name)
  override fun startInWriteAction(): Boolean = false
  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val propertiesFile = PsiManager.getInstance(project).findFile(file) as? PropertiesFile ?: return
    if (!FileModificationService.getInstance().prepareFileForWrite(propertiesFile.containingFile)) return

    var anchor = null as IProperty?
    for (property in propertiesFile.properties) {
      val nextKey = property.key
      if (nextKey != null && nextKey > key) break
      anchor = property
    }

    val property = WriteCommandAction.writeCommandAction(project, propertiesFile.containingFile)
      .withName(DevKitBundle.message("inspections.event.log.description.fix.command.name"))
      .compute<IProperty, RuntimeException> {
        propertiesFile.findPropertyByKey(key) ?: propertiesFile.addPropertyAfter(key, "", anchor)
      }

    val valueOffset = property.psiElement.textRange.endOffset
    val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file, valueOffset), true) ?: return

    val template = TemplateManager.getInstance(project).createTemplate("", "")
    template.addVariable("VALUE", TextExpression(""), true)
    template.addEndVariable()
    template.isToReformat = false
    TemplateManager.getInstance(project).startTemplate(editor, template)
  }
}
