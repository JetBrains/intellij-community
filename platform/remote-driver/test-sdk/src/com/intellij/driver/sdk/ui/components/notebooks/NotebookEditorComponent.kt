package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.sdk.PsiFile
import com.intellij.driver.sdk.PsiManager
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.invokeActionWithRetries
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.EditorComponentImpl
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowLeftToolbarUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowRightToolbarUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.elements.JLabelUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.JcefOffScreenViewComponent
import com.intellij.driver.sdk.ui.components.elements.LetsPlotComponent
import com.intellij.driver.sdk.ui.components.elements.NotebookTableOutputUi
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.wait
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForCodeAnalysis
import com.intellij.driver.sdk.waitForIndicators
import org.intellij.lang.annotations.Language
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * We need to choose an editor with an actual file, skipping the artificial one created for wrappers.
 */
private const val topLevelEditorSearchPattern = "//div[@class='EditorComponentImpl' and not(@accessiblename='Editor')]"

fun Finder.notebookEditor(@Language("xpath") xpath: String? = null): NotebookEditorUiComponent =
  x(xpath ?: "//div[@class='EditorCompositePanel']",
    NotebookEditorUiComponent::class.java)

fun Finder.notebookEditor(action: NotebookEditorUiComponent.() -> Unit) {
  return notebookEditor().action()
}

fun NotebookEditorUiComponent.waitForHighlighting() {
  driver.waitForCodeAnalysis(file = editor.getVirtualFile())
}

typealias CellSelector = (List<UiComponent>) -> UiComponent

val FirstCell: CellSelector = { it.first() }
val SecondCell: CellSelector = { it.drop(1).first() }
val LastCell: CellSelector = { it.last() }


class NotebookEditorUiComponent(private val data: ComponentData) : JEditorUiComponent(data) {
  private val addCellBelow
    get() = x("//div[@myicon='add.svg']")
  private val runAndSelectNext
    get() = x("//div[@myicon='runAndSelect.svg']")
  private val runAllCells
    get() = x("//div[@myicon='runAll.svg']")
  private val clearOutputs
    get() = x("//div[@myicon='clearOutputs.svg']")
  private val restartKernel
    get() = x("//div[@myicon='restartKernel.svg']")
  private val deleteCell
    get() = x("//div[@myicon='delete.svg']")
  val interruptKernel: UiComponent
    get() = x("//div[@myicon='stop.svg']")
  val debugButton: UiComponent
    get() = x("//div[@myicon='debug.svg']")
  val notebookCellOutputs: List<UiComponent>
    get() = xx("//div[@class='FullEditorWidthRenderer']//div[@class='EditorComponentImpl']").list()
  val jcefOffScreens: List<JcefOffScreenViewComponent>
    get() = xx("//div[@class='JcefOffScreenViewComponent']", JcefOffScreenViewComponent::class.java).list()
  val notebookCellExecutionInfos: List<JLabelUiComponent>
    get() = xx("//div[@accessiblename='ExecutionLabel']", JLabelUiComponent::class.java).list()
  val notebookTables: List<NotebookTableOutputUi>
    get() = xx("//div[@class='LoadingDecoratorLayeredPaneImpl']/div[@class='JPanel'][descendant::div[@class='TableResultView']][descendant::div[@class='TwoSideComponent']]", NotebookTableOutputUi::class.java).list()
  val notebookPlots: List<LetsPlotComponent>
    get() = xx("//div[@class='LetsPlotComponent']", LetsPlotComponent::class.java).list()
  val toolbar: UiComponent
    get() = x("//div[@class='JupyterFileEditorToolbar']")
  val kotlinNotebookToolbar: KotlinNotebookActionToolBarComponent
    get() = x(
      "//div[@class='ActionToolbarImpl' and contains(@myvisibleactions, 'Kotlin Notebook')]",
      KotlinNotebookActionToolBarComponent::class.java
    )
  val imagePanel: List<UiComponent>
    get() = xx("//div[@class='FullEditorWidthRenderer']//div[@class='ImagePanel']").list()
  val lastNotebookOutput: String
    get() = notebookCellOutputs.last().getAllTexts().asString()
  val statusBar: UiComponent
    get() = x("//div[@class='JupyterFileEditorToolbar']")
  val cellIndexPanel: UiComponent
    get() = x("//div[@class='MyScrollPane']//div[@class='JBViewport']//div[@class='EditorGutterComponentImpl']")
  val cellActions: List<UiComponent>
    get() = xx("//div[@class='JupyterCellActionsToolbar']").list()
  val foldingBars: List<UiComponent>
    get() = xx("//div[@class='EditorCellFoldingBarComponent']").list()

  override val editorComponent: EditorComponentImpl
    get() = when {
      data.xpath.contains("EditorCompositePanel") -> driver.cast(
        editor(topLevelEditorSearchPattern).component,
        EditorComponentImpl::class
      )
      else -> super.editorComponent
    }

  val psiFile: PsiFile?
    get() = with(driver) {
      service<PsiManager>(singleProject()).findFile(editor.getVirtualFile())
    }

  fun addEmptyCodeCell(): Unit = addCellBelow.strictClick()

  fun addCodeCell(text: String) {
    addEmptyCodeCell()
    driver.ui.pasteText(text)
  }

  fun addMarkdownCell(content: String) {
    driver.invokeActionWithRetries("NotebookInsertMarkdownCellAction")
    driver.ui.pasteText(content)
  }

  fun runAllCells(): Unit = runAllCells.strictClick()

  fun runCell(): Unit = runAndSelectNext.strictClick()

  fun clearAllOutputs(): Unit = clearOutputs.strictClick()

  fun restartKernel(): Unit = restartKernel.strictClick()

  fun interruptKernel(): Unit = interruptKernel.strictClick()

  fun deleteFirstCell() {
    notebookCellEditors.first().strictClick()
    deleteCell.click()
  }

  fun runCellAndWaitExecuted(
    timeout: Duration = 30.seconds,
    expectedFinalExecutionCount: Int = 1,
  ): Unit = step("Executing cell") {
    runCell()
    waitFor(timeout = timeout) {
      areAllExecutionsFinishedSuccessfully(expectedFinalExecutionCount)
    }
  }

  /*
    This function should be removed when fixed:
    PY-84369
    PY-84374
   */
  fun softRunCellAndWaitExecuted(timeout: Duration = 2.minutes): Unit = step("Executing cell") {
    runCell()
    waitFor(timeout = timeout) {
      val last = notebookCellExecutionInfos.lastOrNull()
      if (last == null) {
        false
      }
      else {
        val timeBefore = last.getExecutionTimeInMsSafe()
        wait(250.milliseconds)
        val timeAfter = last.getExecutionTimeInMsSafe()
        timeAfter == timeBefore && timeAfter != null
      }
    }
  }

  fun runAllCellsAndWaitExecuted(timeout: Duration = 1.minutes): Unit = step("Executing all cells") {
    runAllCells()
    waitFor(timeout = timeout) {
      // TODO: what if we have some cells that were executed before, and their checkmarks are still there,
      //  while new execution labels are not yet created?
      areAllExecutionsFinishedSuccessfully(notebookCellEditors.size)
    }
  }

  /**
   * Checks if there are exactly [expectedFinalExecutionCount] finished cells with green checkmark
   * in the current notebook editor.
   */
  private fun areAllExecutionsFinishedSuccessfully(
    expectedFinalExecutionCount: Int,
  ): Boolean {
    val infos = notebookCellExecutionInfos
    return infos.isNotEmpty() &&
           infos.size == expectedFinalExecutionCount &&
           infos.all {
             it.getParent().x { contains(byAttribute("defaulticon", "greenCheckmark.svg")) }.present()
           }
  }

  /**
   * Combined action that runs all cells, wait for their execution, and waits for the indexes to update.
   */
  fun runAllCellsAndWaitIndexesUpdated(timeout: Duration = 1.minutes, indicatorsTimeout: Duration = timeout): Unit = step("Executing cells and wait for indexes") {
    runAllCellsAndWaitExecuted(timeout)
    step("Waiting for indicators after execution") {
      driver.waitForIndicators(indicatorsTimeout)
    }
  }

  /*
    This functions should be removed when fixed:
    PY-84369
    PY-84374
   */
  fun softRunAllCellsAndWaitExecuted(timeout: Duration = 2.minutes): Unit = step("Executing all cells") {
    runAllCells()
    waitFor(timeout = timeout) {
      val infos = notebookCellExecutionInfos
      val timesBefore = infos.map { it.getExecutionTimeInMsSafe() }

      wait(250.milliseconds)

      val timesAfter = infos.map { it.getExecutionTimeInMsSafe() }

      infos.isNotEmpty()
      && timesAfter.all { it != null }
      && timesBefore == timesAfter
    }
  }

  fun clickOnCell(cellSelector: CellSelector) {
    val cellEditors = notebookCellEditors
    val cell = cellSelector(cellEditors)
    cell.strictClick()
  }

  fun moveMouseOnCell(cellSelector: CellSelector) {
    val cellEditors = notebookCellEditors
    val cell = cellSelector(cellEditors)
    cell.moveMouse()
  }

  fun typeInCell(
    cellSelector: CellSelector,
    text: String,
    delayBetweenChars: Duration = 50.milliseconds,
  ) {
    clickOnCell(cellSelector)
    keyboard {
      typeText(text, delayBetweenChars.inWholeMilliseconds)
    }
  }

  fun pasteToCell(cellSelector: CellSelector, text: String) {
    clickOnCell(cellSelector)
    driver.ui.pasteText(text)
  }


  /**
   * Use to access text editing area
   */
  val notebookCellEditors: List<UiComponent>
    get() = xx("""
      //div[@class='FullEditorWidthRenderer']
      /div[@class='JPanel' and not(
          .//div[contains(@class, 'NotebookAboveCellDelimiterPanel')] 
            or 
          .//div[contains(@class, 'NotebookBelowLastCellPanel')]
            or
          .//div[contains(@class, 'OutputComponent')]
            or
          .//div[contains(@class, 'LetsPlotComponent')]
        ) 
      ]
    """.trimIndent()
    ).list()

  fun JLabelUiComponent.getExecutionTimeInMsSafe(): Long? = step("Get cell execution time") {
    if (this.notPresent()) return@step null
    val text = this.getText()
    if (text.isEmpty()) return@step null

    val seconds = Regex("""(\d+)s""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val millis = Regex("""(\d+)ms""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    seconds * 1_000 + millis
  }

  fun JLabelUiComponent.getExecutionTime(): Duration = step("Get cell execution time") {
    this.getText().run {
      val matchSeconds = Regex("\\d+s").find(this)?.value?.substringBefore("s")?.toLong() ?: 0
      val matchMs = Regex("\\d+ms").find(this)?.value?.substringBefore("ms")?.toLong() ?: 0

      matchSeconds.seconds + matchMs.milliseconds
    }
  }
}

enum class NotebookType(val typeName: String, val newNotebookActionId: String) {
  KOTLIN("Kotlin", "NewKotlinNotebookAction"),
  JUPYTER("Jupyter", "NewJupyterNotebookAction");
}

/**
 * Creates a new Kotlin or Jupyter notebook in the IDE.
 *
 * @param name The name for the new notebook. Defaults to "New Notebook".
 * @param type The type of notebook to create.
 */
fun Driver.createNewNotebook(name: String = "New Notebook", type: NotebookType) {
  ideFrame {
    projectView {
      projectViewTree.run {
        waitFor("wait for project tree to load", 30.seconds) {
          getAllTexts().isNotEmpty()
        }
        leftToolWindowToolbar.projectButton.open() // making sure the project view is in focus
        invokeAction("ScrollPane-scrollHome") // making sure the first line is within the visible bounds
        getAllTexts().first().strictClick()
      }
    }

    invokeAction(type.newNotebookActionId, false)

    popup().run {
      x("//div[@accessiblename='Name']", JTextFieldUI::class.java).strictClick()

      keyboard {
        waitFor("expect $name in the popup") {
          driver.ui.pasteText(name)
          getAllTexts().any { it.text == name }
        }
        enter() // submit the popup
      }
    }

    waitFor("the editor is present") {
      notebookEditor().present()
    }
  }
}

fun Driver.createNewNotebookWithMouse(name: String = "New Notebook", type: NotebookType) {
  ideFrame {
    projectView {
      projectViewTree.run {
        waitFor("wait for project tree to load", 30.seconds) {
          getAllTexts().isNotEmpty()
        }
        getAllTexts().first().strictClick()
      }
    }

    val newFileButton = x { byAccessibleName("New File or Directory…") }

    waitFor(timeout = 30.seconds) {
      newFileButton.present()
    }
    newFileButton.strictClick()

    popup().run {
      waitOneText("${type.typeName} Notebook").strictClick()

      keyboard {
        waitFor("expect $name in the popup") {
          driver.ui.pasteText(name)
          getAllTexts().any { it.text == name }
        }
        enter() // submit the popup
      }
    }

    waitFor("the editor is present", timeout = 1.minutes) {
      notebookEditor().present()
    }
    projectView {
      projectViewTree.run {
        waitOneText(message = "File name should present in project tree", timeout = 15.seconds) {
          it.text == "$name.ipynb"
        }
      }
    }
  }
}

fun Driver.closeRightToolWindow(stripeButtonName: String) {
  ideFrame {
    val rightToolbar = xx(ToolWindowRightToolbarUi::class.java) { byClass("ToolWindowRightToolbar") }.list().firstOrNull()
                       ?: return@ideFrame
    val varsButton = rightToolbar.stripeButton(stripeButtonName)
    if (varsButton.present()) {
      varsButton.close()
    }
  }
}

fun Driver.openLeftToolWindow(stripeButtonName: String) {
  ideFrame {
    val leftToolbar = xx(ToolWindowLeftToolbarUi::class.java) { byClass("ToolWindowLeftToolbar") }.list().firstOrNull()
                       ?: return@ideFrame
    val varsButton = leftToolbar.stripeButton(stripeButtonName)
    if (varsButton.notPresent()) {
      varsButton.open()
    }
  }
}

/**
 * Executes a test block within the context of the notebook editor UI component.
 * Note: only the NotebookEditorUiComponent and its successors are directly available in the context of this block.
 * If you need to access other UI components in testBody(), use the `driver.ideFrame {}`.
 *
 * @param testBody A lambda containing the test actions to be executed with the `NotebookEditorUiComponent`.
 */
fun Driver.withNotebookEditor(testBody: NotebookEditorUiComponent.() -> Unit): IdeaFrameUI = ideFrame {
  notebookEditor {
    testBody()
  }
}


