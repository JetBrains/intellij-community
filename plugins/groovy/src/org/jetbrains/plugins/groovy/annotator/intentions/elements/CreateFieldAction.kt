// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.codeInsight.CodeInsightUtil.positionCursor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.startTemplate
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.lang.jvm.JvmLong
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTypes
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.annotator.intentions.GroovyCreateFieldFromUsageHelper
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

internal class CreateFieldAction(
  target: GrTypeDefinition,
  request: CreateFieldRequest,
  private val constantField: Boolean
) : CreateFieldActionBase(target, request), JvmGroupIntentionAction {

  override fun getActionGroup(): JvmActionGroup = if (constantField) CreateConstantActionGroup else CreateFieldActionGroup

  override fun getText(): String {
    val what = request.fieldName
    val where = getNameForClass(target, false)
    val message = if (constantField) "intention.name.create.constant.field.in.class" else "intention.name.create.field.in.class"
    return GroovyBundle.message(message, what, where)
  }

  override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
    val field = GroovyFieldRenderer(project, constantField, target, request).renderField()
    val className = myTargetPointer.element?.name
    return IntentionPreviewInfo.CustomDiff(GroovyFileType.GROOVY_FILE_TYPE, className, "", field.text)
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    GroovyFieldRenderer(project, constantField, target, request).doRender()
  }
}

internal val constantModifiers = setOf(
  JvmModifier.STATIC,
  JvmModifier.FINAL
)

private class GroovyFieldRenderer(
  val project: Project,
  val constantField: Boolean,
  val targetClass: GrTypeDefinition,
  val request: CreateFieldRequest
) {

  val helper = GroovyCreateFieldFromUsageHelper()
  val typeConstraints = createConstraints(project, request.fieldType).toTypedArray()

  private val modifiersToRender: Collection<JvmModifier>
    get() {
      return if (constantField) {
        if (targetClass.isInterface) {
          // interface fields are public static final implicitly, so modifiers don't have to be rendered
          request.modifiers - constantModifiers - visibilityModifiers
        }
        else {
          // render static final explicitly
          request.modifiers + constantModifiers
        }
      }
      else {
        // render as is
        request.modifiers
      }
    }

  fun doRender() {
    var field = renderField()
    field = insertField(field)
    startTemplate(field)
  }

  fun renderField(): GrField {
    val elementFactory = GroovyPsiElementFactory.getInstance(project)
    val field = elementFactory.createField(request.fieldName, PsiTypes.intType())

    // clean template modifiers
    field.modifierList?.let { list ->
      list.firstChild?.let {
        list.deleteChildRange(it, list.lastChild)
      }
    }

    for (annotation in request.annotations) {
      field.modifierList?.addAnnotation(annotation.qualifiedName)
    }

    // setup actual modifiers
    for (modifier in modifiersToRender.map(JvmModifier::toPsiModifier)) {
      PsiUtil.setModifierProperty(field, modifier, true)
    }

    if (targetClass is GroovyScriptClass) field.modifierList?.addAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD)

    if (constantField) {
      field.initializerGroovy = elementFactory.createExpressionFromText("0", null)
    }

    val requestInitializer = request.initializer
    if (requestInitializer is JvmLong) {
      field.initializerGroovy = elementFactory.createExpressionFromText("${requestInitializer.longValue}L", null)
    }

    return field
  }

  private fun insertField(field: GrField): GrField {
    return helper.insertFieldImpl(targetClass, field, null)

  }

  private fun startTemplate(field: GrField) {
    val targetFile = targetClass.containingFile ?: return
    val newEditor = positionCursor(field.project, targetFile, field) ?: return
    val substitutor = request.targetSubstitutor.toPsiSubstitutor(project)
    val template = helper.setupTemplateImpl(field, typeConstraints, targetClass, newEditor, null, constantField, substitutor)
    val listener = MyTemplateListener(project, newEditor, targetFile)
    startTemplate(newEditor, template, project, listener, null)
  }
}

private class MyTemplateListener(val project: Project, val editor: Editor, val file: PsiFile) : TemplateEditingAdapter() {

  override fun templateFinished(template: Template, brokenOff: Boolean) {
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    val offset = editor.caretModel.offset
    val psiField = PsiTreeUtil.findElementOfClassAtOffset(file, offset, GrField::class.java, false) ?: return
    runWriteAction {
      CodeStyleManager.getInstance(project).reformat(psiField)
    }
    editor.caretModel.moveToOffset(psiField.textRange.endOffset - 1)
  }
}
