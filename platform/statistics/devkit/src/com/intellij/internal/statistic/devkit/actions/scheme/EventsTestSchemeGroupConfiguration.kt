// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions.scheme

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.eventLog.events.scheme.GroupDescriptor
import com.intellij.internal.statistic.eventLog.validator.storage.GroupValidationTestRule
import com.intellij.internal.statistic.eventLog.validator.storage.GroupValidationTestRule.Companion.EMPTY_RULES
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
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.layout.*
import com.intellij.util.IncorrectOperationException
import com.intellij.util.PairProcessor
import com.intellij.util.TextFieldCompletionProviderDumbAware
import com.intellij.util.ThrowableRunnable
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class EventsTestSchemeGroupConfiguration(private val project: Project,
                                         productionGroups: EventGroupRemoteDescriptors,
                                         initialGroup: GroupValidationTestRule,
                                         generatedScheme: List<GroupDescriptor>,
                                         groupIdChangeListener: ((GroupValidationTestRule) -> Unit)? = null) : Disposable {

  val panel: JPanel
  val groupIdTextField: TextFieldWithCompletion
  private var currentGroup: GroupValidationTestRule = initialGroup
  private lateinit var allowAllEventsRadioButton: JBRadioButton
  private lateinit var customRulesRadioButton: JBRadioButton
  private lateinit var generateSchemeButton: JComponent
  private val validationRulesEditorComponent: JComponent
  private val validationRulesDescription: JLabel
  private val tempFile: PsiFile
  private val validationRulesEditor: EditorEx
  private val eventsScheme: Map<String, String> = createEventsScheme(generatedScheme)

  init {
    groupIdTextField = TextFieldWithCompletion(project, createCompletionProvider(productionGroups), initialGroup.groupId, true, true, false)
    groupIdTextField.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
        currentGroup.groupId = groupIdTextField.text
        if (groupIdChangeListener != null) {
          groupIdChangeListener(currentGroup)
        }
        updateGenerateSchemeButton()
      }
    })

    tempFile = com.intellij.internal.statistic.devkit.actions.TestParseEventsSchemeDialog.createTempFile(project, "event-log-validation-rules", currentGroup.customRules)!!
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

    validationRulesDescription = ComponentPanelBuilder.createCommentComponent(
      StatisticsBundle.message("stats.validation.rules.format"), true)

    panel = panel {
      row {
        cell {
          label(StatisticsBundle.message("stats.group.id"))
          groupIdTextField(growX)
        }
      }
      buttonGroup {
        row {
          allowAllEventsRadioButton = radioButton(StatisticsBundle.message("stats.allow.all.events")).component
          allowAllEventsRadioButton.isSelected = !initialGroup.useCustomRules
          allowAllEventsRadioButton.addChangeListener { updateRulesOption() }
        }
        row {
          cell {
            customRulesRadioButton = radioButton(StatisticsBundle.message("stats.use.custom.validation.rules")).component
            customRulesRadioButton.isSelected = initialGroup.useCustomRules
            customRulesRadioButton.addChangeListener { updateRulesOption() }
            ContextHelpLabel.create(StatisticsBundle.message("stats.test.scheme.custom.rules.help"))()
          }
        }
      }
      row {
        validationRulesEditorComponent(growX)
      }
      row {
        generateSchemeButton = button("Generate Scheme") {
          val scheme = eventsScheme[groupIdTextField.text]
          if (scheme != null) {
            WriteAction.run<Throwable> { validationRulesEditor.document.setText(scheme) }
          }
        }.component
      }
      row {
        validationRulesDescription()
      }
    }
      .withBorder(JBUI.Borders.empty(2))
    updateRulesOption()
  }

  private fun updateGenerateSchemeButton() {
    val useCustomRules = customRulesRadioButton.isSelected
    generateSchemeButton.isVisible = useCustomRules
    generateSchemeButton.isEnabled = useCustomRules && eventsScheme[groupIdTextField.text] != null
    if (!generateSchemeButton.isEnabled) {
      generateSchemeButton.toolTipText = StatisticsBundle.message("stats.scheme.generation.available.only.for.new.api")
    }
    else {
      generateSchemeButton.toolTipText = null
    }
  }

  private fun createCompletionProvider(productionGroups: EventGroupRemoteDescriptors): TextFieldCompletionProviderDumbAware {
    return object : TextFieldCompletionProviderDumbAware() {
      override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
        val generatedSchemeVariants = eventsScheme.keys.map {
          LookupElementBuilder.create(it).withInsertHandler(InsertHandler { _, item ->
            val scheme = eventsScheme[item.lookupString]
            if (scheme != null) {
              customRulesRadioButton.isSelected = true
              WriteAction.run<Throwable> { validationRulesEditor.document.setText(scheme) }
            }
          })
        }
        result.addAllElements(generatedSchemeVariants)

        val productionGroupsVariants = productionGroups.groups.asSequence()
          .mapNotNull { it.id }
          .filterNot { eventsScheme.keys.contains(it) }
          .map {
            LookupElementBuilder.create(it).withInsertHandler(InsertHandler { _, _ ->
              allowAllEventsRadioButton.isSelected = true
              WriteAction.run<Throwable> { validationRulesEditor.document.setText(EMPTY_RULES) }
            })
          }.toList()
        result.addAllElements(productionGroupsVariants)
      }
    }
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
      LOG.warn(e)
    }
    return editor
  }

  fun updatePanel(newGroup: GroupValidationTestRule?) {
    if (newGroup == null) return
    currentGroup = newGroup
    groupIdTextField.text = newGroup.groupId
    groupIdTextField.requestFocusInWindow()
    if (newGroup.useCustomRules) {
      customRulesRadioButton.isSelected = true
    }
    else {
      allowAllEventsRadioButton.isSelected = true
    }
    WriteAction.run<Throwable> { validationRulesEditor.document.setText(newGroup.customRules) }
  }

  private fun updateRulesOption() {
    val useCustomRules = customRulesRadioButton.isSelected
    validationRulesEditorComponent.isVisible = useCustomRules
    validationRulesDescription.isVisible = useCustomRules
    updateGenerateSchemeButton()

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
          LOG.warn(e)
        }
      })

    if (!validationRulesEditor.isDisposed) {
      EditorFactory.getInstance().releaseEditor(validationRulesEditor)
    }
  }

  fun validate(): List<ValidationInfo> {
    return validateTestSchemeGroup(project, currentGroup, groupIdTextField, tempFile)
  }

  private fun createEventsScheme(generatedScheme: List<GroupDescriptor>): HashMap<String, String> {
    val eventsScheme = HashMap<String, String>()
    val gson = GsonBuilder().setPrettyPrinting().create()
    for (group in generatedScheme) {
      val validationRules = createValidationRules(group)
      if (validationRules != null) {
        eventsScheme[group.id] = gson.toJson(validationRules)
      }
    }
    return eventsScheme
  }

  private fun createValidationRules(group: GroupDescriptor): EventGroupRemoteDescriptors.GroupRemoteRule? {
    val eventIds = hashSetOf<String>()
    val eventData = hashMapOf<String, MutableSet<String>>()
    val events = group.schema
    for (event in events) {
      eventIds.add(event.event)
      for (dataField in event.fields) {
        val validationRule = dataField.value
        val validationRules = eventData[dataField.path]
        if (validationRules == null) {
          eventData[dataField.path] = validationRule.toHashSet()
        }
        else {
          validationRules.addAll(validationRule)
        }
      }
    }

    if (eventIds.isEmpty() && eventData.isEmpty()) return null

    val rules = EventGroupRemoteDescriptors.GroupRemoteRule()
    rules.event_id = eventIds
    rules.event_data = eventData
    return rules
  }

  companion object {
    private val LOG = logger<EventsTestSchemeGroupConfiguration>()

    internal val FUS_TEST_SCHEME_COMMON_RULES_KEY = Key.create<ProductionRules>("statistics.test.scheme.validation.rules.file")

    fun validateTestSchemeGroup(project: Project,
                                testSchemeGroup: GroupValidationTestRule,
                                groupIdTextField: JComponent): List<ValidationInfo> {
      return validateTestSchemeGroup(project, testSchemeGroup, groupIdTextField, null)
    }

    private fun validateTestSchemeGroup(project: Project,
                                        testSchemeGroup: GroupValidationTestRule,
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
      if (customRules.isBlank()) return listOf(ValidationInfo(StatisticsBundle.message("stats.unable.to.parse.validation.rules")))
      if (!isValidJson(customRules)) return listOf(ValidationInfo(StatisticsBundle.message("stats.unable.to.parse.validation.rules")))
      if (project === ProjectManager.getInstance().defaultProject) return emptyList()
      val file = if (customRulesFile != null) {
        customRulesFile
      }
      else {
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(JsonLanguage.INSTANCE, customRules)
        psiFile.virtualFile.putUserData(EventsSchemeJsonSchemaProviderFactory.EVENTS_TEST_SCHEME_VALIDATION_RULES_KEY, true)
        psiFile
      }
      val map: Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> = InspectionEngine.inspectEx(
        Collections.singletonList(LocalInspectionToolWrapper(JsonSchemaComplianceInspection())),
        file, file.textRange, file.textRange, true, false, true, DaemonProgressIndicator(),
        PairProcessor.alwaysTrue())

      return map.values.flatten().map { descriptor ->
        ValidationInfo("Line ${descriptor.lineNumber + 1}: ${descriptor.descriptionTemplate}")
      }
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
    constructor(rules: EventGroupRemoteDescriptors.GroupRemoteRule?) : this(rules?.regexps?.keys ?: emptySet(),
                                                                            rules?.enums?.keys ?: emptySet())
  }

}
