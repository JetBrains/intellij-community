// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.IdeCoreBundle
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.CommitStepException
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.language.EmptyProjectGeneratorNewProjectWizard
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.platform.ProjectGeneratorManager
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.TimeoutUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.CardLayout
import java.awt.Dimension
import java.io.IOException
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

private val LOG = logger<GeneratorNewProjectWizardDialog>()

/**
 * The new project wizard of the platform. It shows the generators of the New Project Wizard API, and it needs no Java plugin.
 *
 * The tray on the left lists the language generators of [com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard]
 * in alphabetical order. The other generators of [GeneratorNewProjectWizard.EP_NAME] follow them in the order of
 * [GeneratorNewProjectWizard.ordinal]. The Empty Project generator is always present.
 * The module builders, the frameworks and the project templates are a part of the IntelliJ IDEA wizard only.
 *
 * @param defaultLocation the directory that the Location field shows at start, or `null`. The user cannot change it.
 */
@ApiStatus.Internal
class GeneratorNewProjectWizardDialog(defaultLocation: Path?) : DialogWrapper(null, true) {

  val context: WizardContext = WizardContext(null, disposable)

  private val projectTypeList = ProjectTypeList(context, GENERATOR_ITEM_COMPARATOR)
  private val optionsPanel = JPanel(CardLayout())
  private val steps = HashMap<String, ModuleWizardStep>()
  private var currentStep: ModuleWizardStep? = null

  /**
   * The project directory, or the `.ipr` file for [StorageScheme.DEFAULT]. It is valid after the user closes the dialog with OK.
   */
  val projectFile: Path
    get() = when (context.projectStorageFormat) {
      StorageScheme.DEFAULT -> Path.of(context.projectFileDirectory, context.projectName + ProjectFileType.DOT_DEFAULT_EXTENSION)
      StorageScheme.DIRECTORY_BASED -> Path.of(context.projectFileDirectory)
    }

  init {
    title = IdeCoreBundle.message("title.new.project")
    if (defaultLocation != null && !context.isProjectFileDirectorySet) {
      context.setProjectFileDirectory(defaultLocation, false)
      context.isProjectLocationLocked = true
    }

    val emptyPanel = JBPanelWithEmptyText().withEmptyText(UIBundle.message("newProjectWizard.ProjectTypeStep.empty.text"))
    optionsPanel.add(emptyPanel, EMPTY_CARD)

    projectTypeList.setItems(ProjectTypeListSection.NEW_PROJECT, collectGeneratorItems())
    projectTypeList.installFilteringListModel(::getSearchText) { showEmptyCard() }
    installLanguageGeneratorListener(context, object : LanguageGeneratorListener {
      override fun generatorAdded(generator: GeneratorNewProjectWizard) {
        if (generator.isEnabled()) {
          projectTypeList.addItem(GeneratorItem(generator, isLanguage = true))
        }
      }

      override fun generatorRemoved(name: String) {
        projectTypeList.removeItem(ProjectTypeListSection.NEW_PROJECT, name)
      }
    })
    projectTypeList.whenItemSelected(::showItem)
    projectTypeList.restoreSelection()

    init()
    rootPane?.putClientProperty(UIUtil.NO_BORDER_UNDER_WINDOW_TITLE_KEY, true)
  }

  private fun collectGeneratorItems(): List<GeneratorItem> {
    val languages = collectLanguageGeneratorNewProjectWizards(context)
    val generators = collectGeneratorNewProjectWizards(context).filter { it.isEnabled() }.toMutableList()
    if (generators.none { it.id == NewProjectWizardConstants.Generators.EMPTY_PROJECT }) {
      generators.add(EmptyProjectGeneratorNewProjectWizard())
    }
    return languages.map { GeneratorItem(it, isLanguage = true) } + generators.map { GeneratorItem(it, isLanguage = false) }
  }

  private fun getSearchText(item: GeneratorItem): String {
    val keywords = LinkedHashSet<String>()
    keywords.add(item.name)
    val step = steps[item.id]
    if (step is NewProjectWizardStep) {
      keywords.addAll(step.keywords.toSet())
    }
    return keywords.joinToString(" ")
  }

  private fun showItem(item: GeneratorItem) {
    context.projectBuilder = item.builder
    val step = steps.getOrPut(item.id) { createStep(item) }
    try {
      step._init()
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
    context.setScreen(1)
    currentStep = step
    (optionsPanel.layout as CardLayout).show(optionsPanel, item.id)
    isOKActionEnabled = true
    NewProjectWizardCollector.logGeneratorSelected(context)
  }

  private fun createStep(item: GeneratorItem): ModuleWizardStep {
    val step = item.builder.getCustomOptionsStep(context, disposable)
    step.updateStep()
    val component = step.component
    if (component is DialogPanel) {
      component.withPreferredWidth(maxOf(400, component.minimumSize.width))
    }
    val scrollPane = JBScrollPane(component)
    scrollPane.border = JBUI.Borders.empty()
    optionsPanel.add(scrollPane, item.id)
    return step
  }

  private fun showEmptyCard() {
    currentStep = null
    (optionsPanel.layout as CardLayout).show(optionsPanel, EMPTY_CARD)
    isOKActionEnabled = false
  }

  override fun createCenterPanel(): JComponent {
    val projectTypePanel = projectTypeList.component
    projectTypePanel.minimumSize = Dimension(JBUI.scale(240), JBUI.scale(100))
    optionsPanel.border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0)

    val splitter = OnePixelSplitter(false, 0.25f)
    splitter.setHonorComponentsMinimumSize(true)
    splitter.firstComponent = projectTypePanel
    splitter.secondComponent = optionsPanel
    return splitter
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return currentStep?.preferredFocusedComponent ?: projectTypeList.component
  }

  override fun getInitialSize(): Dimension = Dimension(800, 600)

  override fun getStyle(): DialogStyle = DialogStyle.COMPACT

  override fun getDimensionServiceKey(): String = "new project wizard"

  override fun doOKAction() {
    if (!finishStep()) return
    NewProjectWizardCollector.logGeneratorFinished(context)
    super.doOKAction()
  }

  private fun finishStep(): Boolean {
    val step = currentStep ?: return false
    try {
      if (!step.validate()) {
        return false
      }
    }
    catch (e: ConfigurationException) {
      Messages.showErrorDialog(contentPanel, e.message, e.title)
      return false
    }
    step.updateDataModel()
    try {
      step.onStepLeaving()
      step._commit(true)
      step.onWizardFinished()
    }
    catch (e: CommitStepException) {
      Messages.showErrorDialog(contentPanel, e.message)
      return false
    }
    return true
  }

  override fun doCancelAction() {
    currentStep?.onStepLeaving()
    super.doCancelAction()
  }

  override fun dispose() {
    steps.values.forEach { it.disposeUIResources() }
    steps.clear()
    super.dispose()
  }

  @TestOnly
  fun availableGeneratorsToString(): String = projectTypeList.getAvailableItems()

  @TestOnly
  fun setSelectedGenerator(name: String): Boolean {
    projectTypeList.setSelectedItem(name)
    return projectTypeList.getSelectedItem()?.name == name
  }

  @TestOnly
  fun selectedGeneratorToString(): String? = projectTypeList.getSelectedItem()?.name

  private class GeneratorItem(val wizard: GeneratorNewProjectWizard, val isLanguage: Boolean) : ProjectTypeListItem {
    val builder: GeneratorNewProjectWizardBuilderAdapter = GeneratorBuilder(wizard)

    override val id: String = builder.builderId
    override val name: @NlsSafe String = wizard.name
    override val icon: Icon = wizard.icon
    override val section: ProjectTypeListSection = ProjectTypeListSection.NEW_PROJECT

    override fun toString(): String = name
  }

  private class GeneratorBuilder(wizard: GeneratorNewProjectWizard) : GeneratorNewProjectWizardBuilderAdapter(wizard)

  private companion object {
    const val EMPTY_CARD = "empty card"

    val GENERATOR_ITEM_COMPARATOR: Comparator<GeneratorItem> = compareBy<GeneratorItem> { !it.isLanguage }
      .thenBy { if (it.isLanguage) 0 else it.wizard.ordinal }
      .thenBy(NaturalComparator.INSTANCE) { it.name }
  }
}

/**
 * Shows [GeneratorNewProjectWizardDialog] and creates its project.
 *
 * @param defaultLocation see [GeneratorNewProjectWizardDialog].
 * @param attachTo see [createProjectFromBuilder].
 */
@ApiStatus.Internal
suspend fun showGeneratorNewProjectWizard(project: Project?, defaultLocation: Path?, attachTo: Project?) {
  try {
    serviceAsync<ProjectGeneratorManager>().initProjectGenerator(project)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("Failed to execute initProjectGenerator", e)
  }
  // warm-up components
  serviceAsync<ProjectManager>().defaultProject

  val dialog = withContext(Dispatchers.EDT) { GeneratorNewProjectWizardDialog(defaultLocation) }
  val context = dialog.context
  val time = System.nanoTime()
  NewProjectWizardCollector.logOpen(context)
  if (!withContext(Dispatchers.EDT) { dialog.showAndGet() }) {
    NewProjectWizardCollector.logFinish(context, false, TimeoutUtil.getDurationMillis(time))
    return
  }

  try {
    val newProject = createProjectFromBuilder(
      projectBuilder = context.projectBuilder,
      projectName = context.projectName,
      projectFile = dialog.projectFile,
      storageScheme = context.projectStorageFormat,
      projectToClose = null,
      attachTo = attachTo,
    )
    NewProjectWizardCollector.logProjectCreated(newProject, context)
  }
  catch (e: IOException) {
    withContext(Dispatchers.EDT) {
      Messages.showErrorDialog(e.message, UIBundle.message("error.project.wizard.new.project.initialization.title"))
    }
    return
  }
  NewProjectWizardCollector.logFinish(context, true, TimeoutUtil.getDurationMillis(time))
}
