// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName")

package git4idea.ui.branch.popup.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.keymap.KeymapUtil.getKeyStroke
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.platform.compose.JBComposePanel
import com.intellij.platform.compose.PreviewKeyEventHost
import com.intellij.platform.compose.onHostPreviewKeyEvent
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.GitBranch
import git4idea.repo.GitRepository
import git4idea.ui.branch.popup.GitBranchesTreePopupStep.Companion.createDataContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.lazy.*
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
    val branchesVm = remember(coroutineScope, project, repository) { GitBranchesComposeVm(coroutineScope, repository) }
    val text by branchesVm.text.collectAsState()
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
          keyEvent.key == Key.DirectionUp || keyEvent.key == Key.DirectionDown || keyEvent.key == Key.Enter -> {
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

        Branches(
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
private fun Branches(
  branchesVm: GitBranchesComposeVm,
  modifier: Modifier = Modifier,
  dataContextProvider: (branch: GitBranch) -> DataContext,
  closePopup: () -> Unit
) {
  val local by branchesVm.localBranches.collectAsState()
  val remote by branchesVm.remoteBranches.collectAsState()

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
        if (local.isNotEmpty()) {
          group(branchesVm, columnState, startingIndex = 0, "Local", local, dataContextProvider, closePopup)
        }
        if (remote.isNotEmpty()) {
          group(branchesVm, columnState, startingIndex = local.size + 1, "Remote", remote, dataContextProvider, closePopup)
        }
      }
    }

    val preferredBranch by branchesVm.preferredBranch.collectAsState()
    // select default branch
    LaunchedEffect(preferredBranch) {
      if (local.isNotEmpty()) {
        val selectedIndex = local.indexOfFirst { it == preferredBranch }.takeIf { it != -1 } ?: 0
        columnState.selectedKeys = listOf(local[selectedIndex])
        columnState.scrollToItem(selectedIndex + 1)
        return@LaunchedEffect
      }
      // TODO: write it cleaner
      if (remote.isNotEmpty()) {
        val selectedIndex = remote.indexOfFirst { it == preferredBranch }.takeIf { it != -1 } ?: return@LaunchedEffect
        columnState.selectedKeys = listOf(remote[selectedIndex])
        columnState.scrollToItem(selectedIndex + 1)
        return@LaunchedEffect
      }
    }

    VerticalScrollbar(
      adapter,
      modifier = Modifier.align(CenterEnd)
    )
  }
}

private fun SelectableLazyListScope.group(
  branchesVm: GitBranchesComposeVm,
  columnState: SelectableLazyListState,
  startingIndex: Int,
  groupName: String,
  branches: List<GitBranch>,
  dataContextProvider: (branch: GitBranch) -> DataContext,
  closePopup: () -> Unit
) {
  stickyHeader(groupName) {
    var shouldDrawBorder by remember { mutableStateOf(false) }
    val canDrawBorder = !(columnState.firstVisibleItemIndex == 0 && columnState.firstVisibleItemScrollOffset == 0)
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
  for ((index, branch) in branches.withIndex()) {
    item(branch) {
      val coroutineScope = rememberCoroutineScope()
      val branchVm = remember(coroutineScope, branch) { branchesVm.createBranchVm(coroutineScope, branch) }
      Branch(
        branchVm,
        selected = isSelected,
        onHoverChanged = { hovered ->
          if (hovered) {
            coroutineScope.launch {
              columnState.selectedKeys = listOf(branch)
              columnState.lastActiveItemIndex = startingIndex + index
            }
          }
        },
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
private fun Branch(
  branchVm: GitBranchComposeVm,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onHoverChanged: (hovered: Boolean) -> Unit,
  dataContextProvider: () -> DataContext,
  closePopup: () -> Unit
) {
  val hoverSource = remember { MutableInteractionSource() }
  val pointerInside = hoverSource.collectIsHoveredAsState()
  var hovered by remember { mutableStateOf(false) }
  var showActions by remember { mutableStateOf(false) }

  LaunchedEffect(pointerInside) {
    snapshotFlow { pointerInside.value }.collectLatest { isPointerInside ->
      if (!isPointerInside) {
        hovered = false
        onHoverChanged(false)
      }
    }
  }

  Box(modifier = modifier
    .fillMaxWidth()
    .requiredHeight(24.dp)
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
    .pointerHoverIcon(PointerIcon.Hand)
    .pointerInput(pointerInside) {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent()
          when (event.type) {
            PointerEventType.Move -> {
              val isSyntheticEvent = event.nativeEvent == null
              if (!isSyntheticEvent && pointerInside.value && !hovered) {
                hovered = true
                onHoverChanged(true)
              }
            }
          }
        }
      }
    }
    .hoverable(hoverSource)
    .background(
      if (selected) {
        UIUtil.getListSelectionBackground(true).toComposeColor()
      }
      else {
        Color.Transparent
      }, shape = RoundedCornerShape(5.dp))
  ) {
    Row(
      modifier = Modifier.align(CenterStart).padding(start = 12.dp),
      verticalAlignment = CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(modifier = Modifier.requiredWidth(16.dp))
      val rangesToHighlight by branchVm.matchingFragments.collectAsState()
      val highlightColor = UIUtil.getSearchMatchGradientStartColor().toComposeColor()
      val highlightedBranchName = remember(rangesToHighlight, highlightColor) {
        branchVm.name.highlightRanges(rangesToHighlight, highlightColor)
      }
      Text(highlightedBranchName, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

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

private fun String.highlightRanges(
  rangesToHighlight: List<TextRange>,
  highlightColor: Color
): AnnotatedString {
  var lastEndIndex = 0
  val text = this
  return buildAnnotatedString {
    rangesToHighlight.sortedBy { it.startOffset }.forEach { range ->
      append(text.substring(lastEndIndex, range.startOffset))

      withStyle(SpanStyle(background = highlightColor)) {
        append(text.substring(range.startOffset, range.endOffset))
      }

      lastEndIndex = range.endOffset
    }

    append(text.substring(lastEndIndex, text.length))
  }
}