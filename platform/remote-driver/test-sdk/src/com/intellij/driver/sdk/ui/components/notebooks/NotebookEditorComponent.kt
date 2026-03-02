package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.sdk.PsiFile
import com.intellij.driver.sdk.PsiManager
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.invokeActionWithRetries
import com.intellij.driver.sdk.singleProject
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
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.JButtonUiComponent
import com.intellij.driver.sdk.ui.components.elements.JLabelUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.JcefOffScreenViewComponent
import com.intellij.driver.sdk.ui.components.elements.LetsPlotComponent
import com.intellij.driver.sdk.ui.components.elements.NotebookTableOutputUi
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.hasFocus
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForCodeAnalysis
import com.intellij.driver.sdk.waitNotNull
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
  x(xpath ?: "//div[@class='EditorCompositePanel' and .//div[@class='JupyterFileEditorToolbar']]",
    NotebookEditorUiComponent::class.java)

fun Finder.notebookEditor(action: NotebookEditorUiComponent.() -> Unit) {
  return notebookEditor().action()
}

fun NotebookEditorUiComponent.waitForHighlighting() {
  driver.waitForCodeAnalysis(file = editor.getVirtualFile())
}

typealias CellSelector = (List<UiComponent>) -> UiComponent?

val FirstCell: CellSelector = { it.firstOrNull() }
val SecondCell: CellSelector = { it.getOrNull(1) }
val LastCell: CellSelector = { it.lastOrNull() }


class NotebookEditorUiComponent(private val data: ComponentData) : JEditorUiComponent(data) {
  private val addCellBelow
    get() = x("//div[@myicon='add.svg']")
  private val runAndSelectNext
    get() = x("//div[@myicon='runAndSelect.svg']")
  private val runAllCells
    get() = x("//div[@myicon='runAll.svg']")
  private val clearOutputs
    get() = x("//div[@myicon='clearOutputs.svg']")

  class RestartButton(data: ComponentData) : UiComponent(data) {
    private val actionButtonUi = ActionButtonUi(data)
    val hasBadge: Boolean get() = actionButtonUi.icon.contains("BadgeIcon")
  }

  val restartKernelButton: RestartButton
    get() = x(RestartButton::class.java) { byAttribute("myaction", "Restart Kernel (Restart kernel)") }

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
  val firstNotebookOutput: String
    get() = notebookCellOutputs.first().getAllTexts().asString()
  val lastNotebookOutput: String
    get() = notebookCellOutputs.last().getAllTexts().asString()
  val statusBar: UiComponent
    get() = x("//div[@class='JupyterFileEditorToolbar']")
  val cellIndexPanel: UiComponent
    get() = x("//div[@class='MyScrollPane']//div[@class='JBViewport']//div[@class='EditorGutterComponentImpl']")
  val cellActions: List<UiComponent>
    get() = xx("//div[@class='JupyterCellActionsToolbar']").list()
  val newCellActions: List<UiComponent>
    get() = x("//div[@class='JupyterAddNewCellToolbar']").xx("//div[@class='ActionButtonWithText']", JButtonUiComponent::class.java).list()
  val foldingBars: List<UiComponent>
    get() = xx("//div[@class='EditorCellFoldingBarComponent']").list()

  override val editorComponent: EditorComponentImpl
    get() = when {
      data.xpath.contains("EditorCompositePanel") -> {
        // Increase timeout from the default 15 seconds to 30 seconds to handle slower CI environments.
        // The default timeout is resulting in too many flaky tests.
        driver.cast(
          editor(topLevelEditorSearchPattern).waitFound(30.seconds).component,
          EditorComponentImpl::class
        )
      }
      else -> super.editorComponent
    }

  val psiFile: PsiFile?
    get() = with(driver) {
      service<PsiManager>(singleProject()).findFile(editor.getVirtualFile())
    }

  fun addEmptyCodeCell(): Unit {
    driver.invokeActionWithRetries("NotebookInsertCodeCellAction")
  }

  fun addEmptyMarkdownCell(): Unit {
    driver.invokeActionWithRetries("NotebookInsertMarkdownCellAction")
  }

  fun pasteToCurrentCell(text: String) {
    driver.ui.pasteText(text)
  }

  fun addCodeCell(text: String) {
    addEmptyCodeCell()
    pasteToCurrentCell(text)
  }

  fun addCodeCellWithRetry(text: String) {
    addEmptyCodeCell()
    pasteToCellWithRetry(LastCell, text)
  }

  fun addMarkdownCell(content: String) {
    addEmptyMarkdownCell()
    pasteToCurrentCell(content)
  }

  /**
   * Adds a new SQL cell to the notebook with the provided content.
   *
   * @param content The SQL code to be inserted into the new SQL cell.
   * @throws IllegalStateException if the notebook does not support SQL cells.
   */
  fun addSqlCell(@Language("SQL") content: String) {
    driver.invokeActionWithRetries("JupyterAddSQLCellAction")
    driver.ui.pasteText(content)
  }

  fun runAllCells(): Unit = runAllCells.strictClick()

  fun runCell(): Unit = runAndSelectNext.strictClick()

  fun clearAllOutputs(): Unit = clearOutputs.strictClick()

  fun restartKernel(waitForFinish: Duration? = null) {
    restartKernelButton.run {
      strictClick()
      restartKernelButton.waitNotFound()
      waitForFinish?.let { waitFound(waitForFinish) }
    }
  }

  fun interruptKernel() {
    waitFor("cell the first cell starting execution", timeout = 30.seconds) {
      areTheCellStartExecuting(0)
    }
    // update swing
    clickOnCell(FirstCell)

    waitFor(timeout = 15.seconds, message = "Interrupt kernel button should present") {
      interruptKernel.present()
    }
    interruptKernel.strictClick()
  }

  fun deleteFirstCell() {
    notebookCellEditors.first().strictClick()
    deleteCell.click()
  }

  /**
   * Checks if there are exactly [expectedFinalExecutionCount] finished cells with green checkmark
   * in the current notebook editor.
   */
  fun areAllExecutionsFinishedSuccessfully(
    expectedFinalExecutionCount: Int,
  ): Boolean {
    val infos = notebookCellExecutionInfos
    return infos.isNotEmpty() &&
           infos.size == expectedFinalExecutionCount &&
           infos.all {
             it.getParent().x { contains(byAttribute("defaulticon", "greenCheckmark.svg")) }.present()
           }
  }

  fun areTheCellStartExecuting(cellNumber: Int): Boolean {
    val infos = notebookCellExecutionInfos
    return infos.isNotEmpty() &&
           infos[cellNumber].getParent().x {
             contains(byAttribute("defaulticon", "history.svg"))
           }.notPresent()
  }

  fun clickOnCell(cellSelector: CellSelector) {
    val cell = waitNotNull { cellSelector(notebookCellEditors) }
    cell.strictClick()
  }

  fun moveMouseOnCell(cellSelector: CellSelector) {
    val cell = waitNotNull { cellSelector(notebookCellEditors) }
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

  fun pasteToCellWithRetry(cellSelector: CellSelector, text: String) {
    waitFor(timeout = 15.seconds) {
      clickOnCell(cellSelector)
      driver.ui.pasteText(text)
      val searchText = text.replace("\n", "").replace(" ", "")
      val lastCell = waitNotNull { LastCell(notebookCellEditors) }
      lastCell.getParent().getParent().getAllTexts().asString().replace(" ", "").contains(searchText)
    }
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
    leftToolWindowToolbar.projectButton.open() // making sure the project view is open and in focus for correct scrolling
    projectView {
      projectViewTree.run {
        waitFor("wait for project tree to load", 30.seconds) {
          getAllTexts().isNotEmpty()
        }
        invokeActionWithRetries("ScrollPane-scrollHome") // making sure the first line is within the visible bounds
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
    waitFor("Project view should present", timeout = 1.minutes) {
      leftToolWindowToolbar.projectButton.open()
      projectView().present()
    }
    projectView {
      projectViewTree.run {
        waitFor("wait for project tree to load", 30.seconds) {
          getAllTexts().isNotEmpty()
        }
        moveMouse()
      }
    }

    val newFileButton = x { byAccessibleName("New File or Directory…") }

    should("New notebook button should be pressed", timeout = 1.minutes) {
      should(message = "new file popup should present and focused", timeout = 30.seconds) {
        newFileButton.strictClick()
        hasFocus(popup())
      }

      popup().run {
        waitOneText("${type.typeName} Notebook").strictClick()
        hasSubtext("New ${type.typeName} Notebook")
      }
    }

    popup().run {
      keyboard {
        waitFor("expect $name in the popup") {
          driver.ui.pasteText(name)
          getAllTexts().any { it.text == name }
        }
        enter() // submit the popup
      }
    }
    projectView {
      projectViewTree.run {
        waitOneText(message = "File name should present in project tree", timeout = 15.seconds) {
          it.text == "$name.ipynb"
        }
      }
    }
  }
  withNotebookEditor {
    waitFor("the editor is present", timeout = 1.minutes) {
      notebookCellEditors.isNotEmpty()
    }
  }
}

//TODO: @Stankevych should be refactored to a single fun that interacts with the right toolbar
fun Driver.openRightToolWindow(stripeButtonName: String) {
  ideFrame {
    val rightToolbar = xx(ToolWindowRightToolbarUi::class.java) { byClass("ToolWindowRightToolbar") }.list().firstOrNull()
                       ?: return@ideFrame
    val varsButton = rightToolbar.stripeButton(stripeButtonName)
    if (varsButton.present()) {
      varsButton.open()
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

//TODO: @Stankevych should be refactored to a single fun that interacts with the left toolbar
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

fun Driver.closeLeftToolWindow(stripeButtonName: String) {
  ideFrame {
    val leftToolbar = xx(ToolWindowLeftToolbarUi::class.java) { byClass("ToolWindowLeftToolbar") }.list().firstOrNull()
                      ?: return@ideFrame
    val varsButton = leftToolbar.stripeButton(stripeButtonName)
    if (varsButton.present()) {
      varsButton.close()
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

fun Driver.openNotebookWithProjectPanel(fileName: String): IdeaFrameUI = ideFrame {
  openLeftToolWindow("Project")
  projectView {
    projectViewTree.run {
      waitOneText(fileName).doubleClick()
    }
  }
  waitFor("the editor is present", timeout = 30.seconds) {
    notebookEditor().present()
  }
}
