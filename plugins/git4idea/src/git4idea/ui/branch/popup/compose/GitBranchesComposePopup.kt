// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.platform.compose.JBComposePanel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.GitBranch
import git4idea.repo.GitRepository
import git4idea.ui.branch.popup.GitBranchesTreePopupStep.Companion.createDataContext
import git4idea.ui.branch.tree.localBranchesOrCurrent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.lazy.*
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import javax.swing.JComponent

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
    Box(modifier = Modifier
      .fillMaxSize()
      .background(JBUI.CurrentTheme.Popup.BACKGROUND.toComposeColor())
      .padding(start = 12.dp, end = 3.dp, top = 5.dp, bottom = 5.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DummySearchTextField()
        Divider(orientation = Orientation.Horizontal, color = UIUtil.getTooltipSeparatorColor().toComposeColor())
        val localBranches = repository.localBranchesOrCurrent
        val remoteBranches = repository.branches.remoteBranches

        val focusRequester = remember { FocusRequester() }
        Branches(
          defaultSelectedLocalBranch = localBranches.find { it.name.contains("master") } ?: localBranches.firstOrNull(),
          localBranches.toList(), remoteBranches.toList(),
          modifier = Modifier.focusRequester(focusRequester),
          dataContextProvider = { branch ->
            createDataContext(project, repository, listOf(repository), branch)
          },
          closePopup
        )

        // focus Branches tree by default
        LaunchedEffect(Unit) {
          focusRequester.requestFocus()
        }
      }
    }
  }

  return composePanel
}

@Composable
private fun DummySearchTextField() {
  Box(modifier = Modifier.offset(x = 6.dp).pointerHoverIcon(PointerIcon.Text).padding(vertical = 8.dp)) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon("expui/general/search.svg", "Search", ExpUiIcons::class.java)
      Text(
        text = "Search branch",
        style = TextStyle(
          fontSize = 13.sp,
          color = UIUtil.getContextHelpForeground().toComposeColor(),
        )
      )
    }
  }
}

@Composable
private fun Branches(
  defaultSelectedLocalBranch: GitBranch?,
  local: List<GitBranch>,
  remote: List<GitBranch>,
  modifier: Modifier = Modifier,
  dataContextProvider: (branch: GitBranch) -> DataContext,
  closePopup: () -> Unit
) {
  val columnState = rememberSelectableLazyListState()
  val adapter = rememberScrollbarAdapter(columnState.lazyListState)
  val scrollbarWidth = LocalScrollbarStyle.current.thickness
  Box(modifier = modifier) {
    SelectableLazyColumn(
      state = columnState,
      selectionMode = SelectionMode.Single,
      modifier = Modifier.padding(end = scrollbarWidth + 6.dp)
    ) {
      group(columnState, startingIndex = 0, "Local", local, dataContextProvider, closePopup)
      group(columnState, startingIndex = local.size + 1, "Remote", remote, dataContextProvider, closePopup)
    }

    // select default branch
    LaunchedEffect(Unit) {
      if (local.isNotEmpty()) {
        val selectedIndex = local.indexOfFirst { it == defaultSelectedLocalBranch }.takeIf { it != -1 } ?: 0
        columnState.selectedKeys = listOf(local[selectedIndex])
        columnState.scrollToItem(selectedIndex + 1)
      }
    }

    VerticalScrollbar(
      adapter,
      modifier = Modifier.align(CenterEnd)
    )
  }
}

private fun SelectableLazyListScope.group(
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
        .requiredHeight(24.dp)
        .background(JBUI.CurrentTheme.Popup.BACKGROUND.toComposeColor())
        .onGloballyPositioned {
          shouldDrawBorder = it.positionInParent().y == 0f
        },
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(groupName, color = UIUtil.getContextHelpForeground().toComposeColor(), modifier = Modifier.align(CenterVertically))
      }

      if (shouldDrawBorder && canDrawBorder) {
        Divider(orientation = Orientation.Horizontal, color = UIUtil.getTooltipSeparatorColor().toComposeColor())
      }
    }
  }

  branches(startingIndex + 1, columnState, branches, dataContextProvider, closePopup)
}

private fun SelectableLazyListScope.branches(
  startingIndex: Int,
  columnState: SelectableLazyListState,
  branches: List<GitBranch>,
  dataContextProvider: (branch: GitBranch) -> DataContext,
  closePopup: () -> Unit
) {
  for ((index, branch) in branches.withIndex()) {
    item(branch) {
      val coroutineScope = rememberCoroutineScope()
      Branch(
        branch.name, selected = isSelected,
        onHoverChanged = { hovered ->
          if (hovered) {
            coroutineScope.launch {
              columnState.selectedKeys = listOf(branch)
              // TODO: change last active index
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
  branch: String,
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
      Text(branch, maxLines = 1, overflow = TextOverflow.Ellipsis)
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