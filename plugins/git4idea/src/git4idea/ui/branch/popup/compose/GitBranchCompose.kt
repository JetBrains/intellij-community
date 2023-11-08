// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName")

package git4idea.ui.branch.popup.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.util.TextRange
import com.intellij.util.ui.UIUtil
import git4idea.GitBranch
import icons.DvcsImplIcons
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun GitBranchCompose(
  branchVm: GitBranchComposeVm,
  selected: Boolean,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      val isFavorite by branchVm.isFavorite.collectAsState()
      FavoriteBranchIndication(branchVm, isFavorite, selected)

      val rangesToHighlight by branchVm.matchingFragments.collectAsState()
      BranchName(branchVm.name, rangesToHighlight)
      IncomingOutgoingIndication(branchVm.hasIncomings, branchVm.hasOutgoings)

      Spacer(modifier = Modifier.weight(1f))

      TrackedBranchName(branchVm.trackedBranch)
    }

    if (selected) {
      Icon("expui/general/chevronRight.svg", null, ExpUiIcons::class.java, modifier = Modifier.requiredWidth(16.dp))
    }
    else {
      Box(modifier = Modifier.requiredWidth(16.dp))
    }
  }
}

@Composable
private fun TrackedBranchName(trackedBranch: GitBranch?) {
  if (trackedBranch != null) {
    Text(
      trackedBranch.name,
      maxLines = 1,
      color = UIUtil.getContextHelpForeground().toComposeColor(),
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun BranchName(
  branchName: String,
  rangesToHighlight: List<TextRange>
) {
  val highlightColor = UIUtil.getSearchMatchGradientStartColor().toComposeColor()
  val highlightedBranchName = remember(rangesToHighlight, highlightColor) {
    branchName.highlightRanges(rangesToHighlight, highlightColor)
  }
  Text(highlightedBranchName, maxLines = 1, overflow = TextOverflow.Clip)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteBranchIndication(branchVm: GitBranchComposeVm, isFavorite: Boolean, selected: Boolean) {
  Box(
    modifier = Modifier.onClick {
      branchVm.toggleIsFavourite()
    }
  ) {
    when {
      isFavorite && branchVm.isCurrent && !selected -> Icon("expui/vcs/currentBranchFavoriteLabel.svg", null, ExpUiIcons::class.java)
      isFavorite -> Icon("nodes/favorite.svg", null, AllIcons::class.java)
      selected -> Icon("nodes/notFavoriteOnHover.svg", null, AllIcons::class.java)
      branchVm.isCurrent -> Icon("expui/vcs/currentBranchLabel.svg", null, ExpUiIcons::class.java)
      else -> Box(modifier = Modifier.requiredWidth(16.dp))
    }
  }
}

@Composable
private fun IncomingOutgoingIndication(
  hasIncomings: Boolean,
  hasOutgoings: Boolean,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier) {
    when {
      hasIncomings && hasOutgoings -> Row {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
          Icon("icons/incoming.svg", null, DvcsImplIcons::class.java)
          Icon("icons/outgoing.svg", null, DvcsImplIcons::class.java)
        }
      }
      hasIncomings -> Icon("icons/incoming.svg", null, DvcsImplIcons::class.java)
      hasOutgoings -> Icon("icons/outgoing.svg", null, DvcsImplIcons::class.java)
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