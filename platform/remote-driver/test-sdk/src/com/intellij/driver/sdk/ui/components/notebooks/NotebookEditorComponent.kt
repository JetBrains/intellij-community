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
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.ideFrame
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
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForCodeAnalysis
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
  private val interruptKernel
    get() = x("//div[@myicon='stop.svg']")
  private val deleteCell
    get() = x("//div[@myicon='delete.svg']")
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
  val imagePanel: List<UiComponent>
    get() = xx("//div[@class='FullEditorWidthRenderer']//div[@class='ImagePanel']").list()
  val lastNotebookOutput: String
    get() = notebookCellOutputs.last().getAllTexts().asString()

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

  fun addEmptyCodeCell(): Unit = addCellBelow.click()

  fun addCodeCell(text: String) {
    addEmptyCodeCell()
    driver.ui.pasteText(text)
  }

  fun addMarkdownCell(content: String) {
    driver.invokeActionWithRetries("NotebookInsertMarkdownCellAction")
    driver.ui.pasteText(content)
  }

  fun runAllCells(): Unit = runAllCells.click()

  fun runCell(): Unit = runAndSelectNext.click()

  fun clearAllOutputs(): Unit = clearOutputs.click()

  fun restartKernel(): Unit = restartKernel.click()

  fun interruptKernel(): Unit = interruptKernel.click()

  fun deleteFirstCell() {
    notebookCellEditors.first().click()
    deleteCell.click()
  }

  fun restartHighlighting() {
    driver.withContext {
      invokeActionWithRetries("RestartKotlinNotebookHighlighting")

      waitForHighlighting()
    }
  }

  fun runCellAndWaitExecuted(timeout: Duration = 30.seconds): Unit = step("Executing cell") {
    runCell()
    waitFor(timeout = timeout) {
      // TODO: what if the cell we ran doesn't have an execution label yet, and we are waiting for the previous one?
      notebookCellExecutionInfos.isNotEmpty() && notebookCellExecutionInfos.last().getParent().x {
        contains(byAttribute("defaulticon", "greenCheckmark.svg"))
      }.present()
    }
  }

  fun runAllCellsAndWaitExecuted(timeout: Duration = 1.minutes): Unit = step("Executing all cells") {
    runAllCells()
    waitFor(timeout = timeout) {
      // TODO: what if we have some cells that were executed before, and their checkmarks are still there,
      //  while new execution labels are not yet created?
      notebookCellExecutionInfos.isNotEmpty() && notebookCellExecutionInfos.all {
        it.getParent().x { contains(byAttribute("defaulticon", "greenCheckmark.svg")) }.present()
      }
    }
  }

  fun clickOnCell(cellSelector: CellSelector) {
    val cellEditors = notebookCellEditors
    val cell = cellSelector(cellEditors)
    cell.click()
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
        ) 
      ]
    """.trimIndent()
    ).list()

  fun JLabelUiComponent.getExecutionTimeInMs(): Long = step("Get cell execution time") {
    this.getText().run {
      val matchSeconds = Regex("\\d+s").find(this)?.value?.substringBefore("s")?.toLong() ?: 0
      val matchMs = Regex("\\d+ms").find(this)?.value?.substringBefore("ms")?.toLong() ?: 0

      matchSeconds * 1000 + matchMs
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
        getAllTexts().first().click()
      }
    }

    invokeAction(type.newNotebookActionId, false)

    popup().run {
      x("//div[@accessiblename='Name']", JTextFieldUI::class.java).click()

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
        getAllTexts().first().moveMouse()
      }
    }

    val newFileButton = x { byAccessibleName("New File or Directory…") }

    waitFor {
      newFileButton.present()
    }
    newFileButton.click()

    popup().run {
      waitOneText("${type.typeName} Notebook").click()

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
