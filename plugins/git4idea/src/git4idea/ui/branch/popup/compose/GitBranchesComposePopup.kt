// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName")

package git4idea.ui.branch.popup.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.keymap.KeymapUtil.getKeyStroke
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.platform.compose.JBComposePanel
import com.intellij.platform.compose.PreviewKeyEventHost
import com.intellij.platform.compose.onHostPreviewKeyEvent
import com.intellij.ui.popup.AbstractPopup.isCloseRequest
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.GitBranch
import git4idea.repo.GitRepository
import git4idea.ui.branch.popup.GitBranchesTreePopupStep.Companion.createDataContext
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.lazy.*
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

internal fun createComposeBranchesPopup(project: Project, repository: GitRepository): JBPopup {
  val size = JBDimension(450, 420)

  lateinit var popup: JBPopup
  val popupComposeContent = createBranchesPopupComposeComponent(project, repository, closePopup = {
    popup.closeOk(null)
  }).apply {
    preferredSize = size
  }

  popup = JBPopupFactory
    .getInstance()
    .createComponentPopupBuilder(popupComposeContent, popupComposeContent)
    .setFocusable(true)
    .setRequestFocus(true)
    .setCancelKeyEnabled(false)
    .setResizable(true)
    .setMinSize(JBDimension(350, 300))
    .createPopup()
  return popup
}

private fun createBranchesPopupComposeComponent(
  project: Project,
  repository: GitRepository,
  closePopup: () -> Unit
): JComponent {
  val composePanel = JBComposePanel {
    val coroutineScope = rememberCoroutineScope()
    val branchesVm = remember(coroutineScope, project, repository) {
      GitBranchesComposeVm(project, coroutineScope, repository)
    }
    val text by branchesVm.text
    var textFieldFocused by remember { mutableStateOf(false) }
    var textFieldSelection by remember { mutableStateOf(androidx.compose.ui.text.TextRange.Zero) }
    val textFieldValue by derivedStateOf {
      TextFieldValue(text, textFieldSelection)
    }
    val textFieldFocusRequester = remember { FocusRequester() }
    val branchesFocusRequester = remember { FocusRequester() }
    val findKeyStroke = remember { getKeyStroke(ActionManager.getInstance().getAction("Find").shortcutSet) }

    Box(modifier = Modifier
      .fillMaxSize()
      .background(JBUI.CurrentTheme.Popup.BACKGROUND.toComposeColor())
      .padding(start = 12.dp, end = 3.dp, top = 5.dp, bottom = 5.dp)
      .onPreviewKeyEvent { keyEvent ->
        val e = keyEvent.nativeKeyEvent as KeyEvent
        when {
          isCloseRequest(e) && text.isNotEmpty() -> {
            branchesVm.updateSpeedSearchText("")
            true
          }
          isCloseRequest(e) -> {
            closePopup()
            true
          }
          keyEvent.key == Key.DirectionUp || keyEvent.key == Key.DirectionDown || keyEvent.key == Key.Enter -> {
            // TODO: rewrite key events handling, so list will handle this events also, since now it only gets focus
            branchesFocusRequester.requestFocus()
            false
          }
          Character.isWhitespace(keyEvent.key.nativeKeyCode) -> {
            true
          }
          findKeyStroke == KeyStroke.getKeyStroke(e.keyCode, e.modifiersEx, e.id == KeyEvent.KEY_RELEASED) -> {
            textFieldFocusRequester.requestFocus()
            textFieldSelection = TextRange(text.length)
            true
          }
          textFieldFocused -> {
            // TextField will handle it
            false
          }
          else -> branchesVm.handleKeyBySpeedSearch(e)
        }
      }
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SearchTextField(
          textFieldValue,
          modifier = Modifier.fillMaxWidth().focusRequester(textFieldFocusRequester).onFocusChanged {
            textFieldFocused = it.isFocused
          },
          onValueChange = {
            branchesVm.updateSpeedSearchText(it.text)
            textFieldSelection = it.selection
          })
        Divider(orientation = Orientation.Horizontal, color = UIUtil.getTooltipSeparatorColor().toComposeColor())

        BranchesWithActions(
          branchesVm,
          modifier = Modifier.focusRequester(branchesFocusRequester),
          dataContextProvider = { branch ->
            createDataContext(project, repository, listOf(repository), branch)
          },
          closePopup
        )

        // focus Branches tree by default
        LaunchedEffect(Unit) {
          branchesFocusRequester.requestFocus()
        }
      }
    }
  }

  return composePanel
}

@Composable
private fun SearchTextField(
  value: TextFieldValue,
  modifier: Modifier = Modifier,
  onValueChange: (value: TextFieldValue) -> Unit
) {
  TextField(
    value,
    modifier = modifier.padding(top = 3.dp, bottom = 3.dp),
    onValueChange = {
      onValueChange(it)
    },
    placeholder = {
      Text("Search", color = UIUtil.getContextHelpForeground().toComposeColor())
    },
    undecorated = true,
    leadingIcon = {
      Row {
        Icon("expui/general/search.svg", "Search", ExpUiIcons::class.java)
        Spacer(modifier = Modifier.width(5.dp))
      }
    }
  )
}

@Composable
private fun BranchesWithActions(
  branchesVm: GitBranchesComposeVm,
  modifier: Modifier = Modifier,
  dataContextProvider: (branch: GitBranch) -> DataContext,
  closePopup: () -> Unit
) {
  val local by branchesVm.localBranches.collectAsState()
  val remote by branchesVm.remoteBranches.collectAsState()
  val actions = branchesVm.actions

  val columnState = rememberSelectableLazyListState()
  val adapter = rememberScrollbarAdapter(columnState.lazyListState)
  val scrollbarWidth = LocalScrollbarStyle.current.thickness
  Box(modifier = modifier) {
    PreviewKeyEventHost {
      SelectableLazyColumn(
        state = columnState,
        selectionMode = SelectionMode.Single,
        modifier = Modifier.padding(end = scrollbarWidth + 6.dp)
      ) {
        branchesActions(actions, columnState, startingIndex = 0, closePopup)
        separator("Actions Separator")
        if (local.isNotEmpty()) {
          branchesGroup(branchesVm, columnState, startingIndex = actions.size + 1, "Local", local, dataContextProvider, closePopup)
        }
        if (remote.isNotEmpty()) {
          branchesGroup(branchesVm, columnState, startingIndex = actions.size + local.size + 2, "Remote", remote, dataContextProvider,
                        closePopup)
        }
      }
    }

    val preferredBranch by branchesVm.preferredBranch.collectAsState()
    // select default branch
    LaunchedEffect(columnState, local, remote, preferredBranch) {
      // TODO: write it cleaner, since now calculation requires a lot of attention on indexes
      if (local.isNotEmpty()) {
        val selectedIndex = local.indexOfFirst { it == preferredBranch }.takeIf { it != -1 } ?: 0
        columnState.selectedKeys = setOf(local[selectedIndex])
        // select local branch taking header into account
        columnState.scrollToItem(actions.size + 1 + selectedIndex + 1)
        return@LaunchedEffect
      }
      if (remote.isNotEmpty()) {
        val selectedIndex = remote.indexOfFirst { it == preferredBranch }.takeIf { it != -1 } ?: return@LaunchedEffect
        columnState.selectedKeys = setOf(remote[selectedIndex])
        // select remote branch taking local branches and headers into account
        val indexToSelect = if (local.isEmpty()) {
          actions.size + 1 + selectedIndex + 1
        }
        else {
          actions.size + 1 + local.size + selectedIndex + 2
        }
        columnState.scrollToItem(indexToSelect)
        return@LaunchedEffect
      }
    }

    VerticalScrollbar(
      adapter,
      modifier = Modifier.align(CenterEnd)
    )
  }
}

private fun SelectableLazyListScope.separator(id: String) {
  item("#Separator $id", contentType = BranchesPopupItemType.Separator, selectable = false) {
    Divider(Orientation.Horizontal, modifier = Modifier.padding(5.dp))
  }
}

private fun SelectableLazyListScope.branchesGroup(
  branchesVm: GitBranchesComposeVm,
  columnState: SelectableLazyListState,
  startingIndex: Int,
  groupName: String,
  branches: List<GitBranch>,
  dataContextProvider: (branch: GitBranch) -> DataContext,
  closePopup: () -> Unit
) {
  stickyHeader(groupName, contentType = { BranchesPopupItemType.Group }) {
    var shouldDrawBorder by remember { mutableStateOf(false) }
    val canDrawBorder by derivedStateOf { !(columnState.firstVisibleItemIndex == 0 && columnState.firstVisibleItemScrollOffset == 0) }
    Column(
      modifier = Modifier
        .pointerHoverIcon(PointerIcon.Default)
        .fillMaxWidth()
        .background(JBUI.CurrentTheme.Popup.BACKGROUND.toComposeColor())
        .onGloballyPositioned {
          shouldDrawBorder = it.positionInParent().y == 0f
        },
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
          modifier = Modifier.requiredHeight(24.dp).padding(start = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(groupName, color = UIUtil.getContextHelpForeground().toComposeColor(), modifier = Modifier.align(CenterVertically))
        }

      if (shouldDrawBorder && canDrawBorder) {
        Divider(orientation = Orientation.Horizontal, color = UIUtil.getTooltipSeparatorColor().toComposeColor())
      }
    }
  }

  branches(branchesVm, startingIndex + 1, columnState, branches, dataContextProvider, closePopup)
}

private fun SelectableLazyListScope.branches(
  branchesVm: GitBranchesComposeVm,
  startingIndex: Int,
  columnState: SelectableLazyListState,
  branches: List<GitBranch>,
  dataContextProvider: (branch: GitBranch) -> DataContext,
  closePopup: () -> Unit
) {
  items(
    branches.size,
    contentType = { BranchesPopupItemType.Branch },
    key = { branches[it] }
  ) { index ->
    val branch = branches[index]
    val branchVm = remember(branch) { branchesVm.createBranchVm(branch) }
    BranchPopupItemBox(branch, columnState, listIndex = startingIndex + index, isSelected) {
      BranchListItem(
        branchVm,
        selected = isSelected,
        dataContextProvider = {
          dataContextProvider(branch)
        },
        closePopup = closePopup
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BranchListItem(
  branchVm: GitBranchComposeVm,
  selected: Boolean,
  modifier: Modifier = Modifier,
  dataContextProvider: () -> DataContext,
  closePopup: () -> Unit
) {
  var showActions by remember { mutableStateOf(false) }

  Box(modifier = modifier
    .fillMaxSize()
    .onHostPreviewKeyEvent(enabled = selected) {
      if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.DirectionRight)) {
        showActions = true
        return@onHostPreviewKeyEvent true
      }
      false
    }
    .onClick {
      showActions = true
    }
  ) {
    GitBranchCompose(branchVm, selected, modifier = Modifier.align(CenterStart))

    if (showActions) {
      GitComposeBranchActionsPopup(
        dataContext = dataContextProvider(),
        onClose = { isOk ->
          showActions = false
          if (isOk) {
            closePopup()
          }
        }
      )
    }
  }
}

@Composable
internal fun BranchPopupItemBox(
  key: Any,
  columnState: SelectableLazyListState,
  listIndex: Int,
  selected: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val coroutineScope = rememberCoroutineScope()
  Box(modifier = modifier
    .fillMaxWidth()
    .requiredHeight(24.dp)
    .onHover { hovered ->
      if (hovered) {
        coroutineScope.launch {
          columnState.selectedKeys = setOf(key)
          columnState.lastActiveItemIndex = listIndex
        }
      }
    }
    .pointerHoverIcon(PointerIcon.Hand)
    .background(
      if (selected) {
        UIUtil.getListSelectionBackground(true).toComposeColor()
      }
      else {
        Color.Transparent
      }, shape = RoundedCornerShape(5.dp))
    .padding(start = 12.dp)
  ) {
    content()
  }
}

internal sealed interface BranchesPopupItemType {
  data object Action : BranchesPopupItemType
  data object Separator : BranchesPopupItemType
  data object Branch : BranchesPopupItemType
  data object Group : BranchesPopupItemType
}