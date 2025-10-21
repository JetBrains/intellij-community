package com.intellij.devkit.compose.demo.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBDimension
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import kotlin.coroutines.CoroutineContext

private val logger = logger<WizardDialogWrapper>()

internal class WizardDialogWrapper(
  project: Project,
  @Nls title: String,
  private val pages: List<WizardPage>,
  private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DialogWrapper(project), CoroutineScope {
  override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.EDT + CoroutineName("ComposeWizard")

  private val currentPageIndex = mutableIntStateOf(0)

  private val cancelAction = CancelAction()
  private val backAction = WizardAction(DevkitComposeBundle.message("jewel.wizard.back")) { onBackClick() }
  private val nextAction = WizardAction(DevkitComposeBundle.message("jewel.wizard.next")) { onNextClick() }
  private val finishAction = WizardAction(DevkitComposeBundle.message("jewel.wizard.finish")) { onFinishClick() }

  private var pageScope: CoroutineScope? = null

  init {
    require(pages.isNotEmpty()) { "Wizard must have at least one page" }

    init()

    this.title = title

    updateActions()
  }

  private fun updateActions() {
    pageScope?.cancel("Page changing")
    val newScope = CoroutineScope(coroutineContext)
    pageScope = newScope

    val pageIndex = currentPageIndex.intValue
    val page = pages[pageIndex]

    backAction.isEnabled = pageIndex > 0 && page.canGoBackwards.value
    nextAction.isEnabled = pageIndex < pages.lastIndex && page.canGoForward.value
    finishAction.isEnabled = pageIndex == pages.lastIndex && page.canGoForward.value

    newScope.launch(defaultDispatcher) {
      page.canGoBackwards.collect { canGoBackwards ->
        logger.info("CanGoBackwards: $canGoBackwards")
        backAction.isEnabled = pageIndex > 0 && canGoBackwards
      }
    }
    newScope.launch(defaultDispatcher) {
      page.canGoForward.collect { canGoForward ->
        logger.info("CanGoForward: $canGoForward")
        nextAction.isEnabled = pageIndex < pages.lastIndex && canGoForward
        finishAction.isEnabled = pageIndex == pages.lastIndex && canGoForward
      }
    }
  }

  @OptIn(ExperimentalJewelApi::class)
  override fun createCenterPanel(): JComponent {
    enableNewSwingCompositing()

    return JewelComposePanel {
      val index by currentPageIndex
      pages[index].PageContent()
    }
      .apply { minimumSize = JBDimension(400, 400) }
  }

  override fun createActions(): Array<Action> = arrayOf(cancelAction, backAction, nextAction, finishAction)

  private fun onBackClick() {
    if (currentPageIndex.intValue <= 0) {
      logger.warn("Trying to go back beyond the first page")
      return
    }
    currentPageIndex.intValue -= 1
    updateActions()
  }

  private fun onNextClick() {
    if (currentPageIndex.intValue >= pages.lastIndex) {
      logger.warn("Trying to go next on or beyond the last page")
      return
    }
    currentPageIndex.intValue += 1
    updateActions()
  }

  private fun onFinishClick() {
    logger.info("Finish clicked")
    close(OK_EXIT_CODE)
  }

  private inner class CancelAction : DialogWrapperAction(DevkitComposeBundle.message("jewel.wizard.button.cancel")) {
    override fun doAction(e: ActionEvent?) {
      logger.debug("Cancel clicked")
      doCancelAction()
    }
  }

  // todo report false-positive unused in Kotlin
  private inner class WizardAction(@Nls name: String, private val onAction: () -> Unit) : DialogWrapperAction(name) {
    override fun doAction(e: ActionEvent?) {
      onAction()
    }
  }
}

internal interface WizardPage {
  @Composable
  fun PageContent()

  val canGoForward: StateFlow<Boolean>
  val canGoBackwards: StateFlow<Boolean>
}
