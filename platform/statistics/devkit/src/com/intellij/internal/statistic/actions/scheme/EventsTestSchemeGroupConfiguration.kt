// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions.scheme

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.actions.TestParseEventsSchemeDialog
import com.intellij.internal.statistic.eventLog.whitelist.LocalWhitelistGroup
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService
import com.intellij.json.JsonLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import com.intellij.util.IncorrectOperationException
import com.intellij.util.TextFieldCompletionProviderDumbAware
import com.intellij.util.ThrowableRunnable
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class EventsTestSchemeGroupConfiguration(private val project: Project,
                                         private val productionGroups: FUStatisticsWhiteListGroupsService.WLGroups,
                                         initialGroup: LocalWhitelistGroup,
                                         groupIdChangeListener: ((LocalWhitelistGroup) -> Unit)? = null) : Disposable {

  val panel: JPanel
  val groupIdTextField: TextFieldWithCompletion
  private val log = logger<EventsTestSchemeGroupConfiguration>()
  private var currentGroup: LocalWhitelistGroup = initialGroup
  private val addCustomRuleCheckBox: JBCheckBox = JBCheckBox(StatisticsBundle.message("stats.use.custom.validation.rules"),
                                                             initialGroup.useCustomRules)
  private val validationRulesEditorComponent: JComponent
  private val validationRulesDescription: JLabel
  private val tempFile: PsiFile
  private val validationRulesEditor: EditorEx

  init {
    val completionProvider = object : TextFieldCompletionProviderDumbAware() {
      override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
        result.addAllElements(productionGroups.groups.mapNotNull { it.id }.map(LookupElementBuilder::create))
      }
    }
    groupIdTextField = TextFieldWithCompletion(project, completionProvider, initialGroup.groupId, true, true, false)

    tempFile = TestParseEventsSchemeDialog.createTempFile(project, "event-log-validation-rules", currentGroup.customRules)!!
    tempFile.virtualFile.putUserData(EventsSchemeJsonSchemaProviderFactory.EVENTS_TEST_SCHEME_VALIDATION_RULES_KEY, true)
    tempFile.putUserData(FUS_TEST_SCHEME_COMMON_RULES_KEY, ProductionRules(productionGroups.rules))
    validationRulesEditor = createEditor(project, tempFile)
    validationRulesEditor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
        currentGroup.customRules = validationRulesEditor.document.text
      }
    })
    validationRulesEditorComponent = validationRulesEditor.component
    validationRulesEditorComponent.minimumSize = JBUI.size(200, 100)

    groupIdTextField.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
        currentGroup.groupId = groupIdTextField.text
        if (groupIdChangeListener != null) {
          groupIdChangeListener(currentGroup)
        }
      }
    })
    addCustomRuleCheckBox.addChangeListener { updateRulesOption() }
    validationRulesDescription = ComponentPanelBuilder.createCommentComponent(
      StatisticsBundle.message("stats.validation.rules.format"), true)

    panel = panel {
      row {
        cell {
          label(StatisticsBundle.message("stats.group.id"))
          groupIdTextField(growX)
        }
      }
      row {
        cell {
          addCustomRuleCheckBox()
          ContextHelpLabel.create(StatisticsBundle.message("stats.test.scheme.custom.rules.help"))()
        }
      }
      row {
        validationRulesEditorComponent(growX)
      }
      row {
        validationRulesDescription()
      }
    }
      .withBorder(JBUI.Borders.empty(2))
    updateRulesOption()
  }

  private fun createEditor(project: Project, file: PsiFile): EditorEx {
    var document = PsiDocumentManager.getInstance(project).getDocument(file)
    if (document == null) {
      document = EditorFactory.getInstance().createDocument(currentGroup.customRules)
    }
    val editor = EditorFactory.getInstance().createEditor(document, project, file.virtualFile, false) as EditorEx
    editor.setFile(file.virtualFile)
    editor.settings.isLineMarkerAreaShown = false
    editor.settings.isFoldingOutlineShown = false

    val fileType = FileTypeManager.getInstance().findFileTypeByName("JSON")
    val lightFile = LightVirtualFile("Dummy.json", fileType, "")
    val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, lightFile)
    try {
      editor.highlighter = highlighter
    }
    catch (e: Throwable) {
      log.warn(e)
    }
    return editor
  }

  fun updatePanel(newGroup: LocalWhitelistGroup?) {
    if (newGroup == null) return
    currentGroup = newGroup
    groupIdTextField.text = newGroup.groupId
    groupIdTextField.requestFocusInWindow()
    addCustomRuleCheckBox.isSelected = newGroup.useCustomRules
    WriteAction.run<Throwable> { validationRulesEditor.document.setText(newGroup.customRules) }
  }

  private fun updateRulesOption() {
    val useCustomRules = addCustomRuleCheckBox.isSelected
    validationRulesEditorComponent.isVisible = useCustomRules
    validationRulesDescription.isVisible = useCustomRules

    currentGroup.useCustomRules = useCustomRules
  }

  fun getFocusedComponent(): JComponent = groupIdTextField

  override fun dispose() {
    WriteCommandAction.writeCommandAction(project).run(
      ThrowableRunnable<RuntimeException> {
        try {
          tempFile.delete()
        }
        catch (e: IncorrectOperationException) {
          log.warn(e)
        }
      })

    if (!validationRulesEditor.isDisposed) {
      EditorFactory.getInstance().releaseEditor(validationRulesEditor)
    }
  }

  fun validate(): List<ValidationInfo> {
    return validateTestSchemeGroup(project, currentGroup, groupIdTextField, tempFile)
  }

  companion object {
    internal val FUS_TEST_SCHEME_COMMON_RULES_KEY = Key.create<ProductionRules>("statistics.test.scheme.validation.rules.file")

    fun validateTestSchemeGroup(project: Project,
                                testSchemeGroup: LocalWhitelistGroup,
                                groupIdTextField: JComponent): List<ValidationInfo> {
      return validateTestSchemeGroup(project, testSchemeGroup, groupIdTextField, null)
    }

    private fun validateTestSchemeGroup(project: Project,
                                        testSchemeGroup: LocalWhitelistGroup,
                                        groupIdTextField: JComponent,
                                        customRulesFile: PsiFile?): List<ValidationInfo> {
      val groupId: String = testSchemeGroup.groupId
      val validationInfo = mutableListOf<ValidationInfo>()
      if (groupId.isEmpty()) {
        validationInfo.add(ValidationInfo(StatisticsBundle.message("stats.specify.group.id"), groupIdTextField))
      }

      if (testSchemeGroup.useCustomRules) {
        validationInfo.addAll(validateCustomValidationRules(project, testSchemeGroup.customRules, customRulesFile))
      }
      return validationInfo
    }

    internal fun validateCustomValidationRules(project: Project,
                                               customRules: String,
                                               customRulesFile: PsiFile?): List<ValidationInfo> {
      val file = if (customRulesFile != null) {
        customRulesFile
      } else {
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(JsonLanguage.INSTANCE, customRules)
        psiFile.virtualFile.putUserData(EventsSchemeJsonSchemaProviderFactory.EVENTS_TEST_SCHEME_VALIDATION_RULES_KEY, true)
        psiFile
      }
      if (!isValidJson(customRules)) return listOf(ValidationInfo(StatisticsBundle.message("stats.unable.to.parse.validation.rules")))
      val problemHolder = ProblemsHolder(InspectionManager.getInstance(project), file, true)
      val inspectionSession = LocalInspectionToolSession(file, file.textRange.startOffset, file.textRange.endOffset)
      val inspectionVisitor = JsonSchemaComplianceInspection()
        .buildVisitor(problemHolder, problemHolder.isOnTheFly, inspectionSession)
      val traverser = SyntaxTraverser.psiTraverser(file)
      for (element in traverser) {
        element.accept(inspectionVisitor)
      }
      return problemHolder.results.map { ValidationInfo("Line ${it.lineNumber + 1}: ${it.descriptionTemplate}") }
    }

    private fun isValidJson(customRules: String): Boolean {
      try {
        Gson().fromJson(customRules, JsonObject::class.java)
        return true
      }
      catch (e: JsonSyntaxException) {
        return false
      }
    }
  }

  internal class ProductionRules(val regexps: Set<String>, val enums: Set<String>) {
    constructor(rules: FUStatisticsWhiteListGroupsService.WLRule?) : this(rules?.regexps?.keys ?: emptySet(),
                                                                          rules?.enums?.keys ?: emptySet())
  }

}
