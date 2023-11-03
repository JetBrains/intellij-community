// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName")

package git4idea.ui.branch.popup.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredWidth
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
import com.intellij.openapi.util.TextRange
import com.intellij.util.ui.UIUtil
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun GitBranchCompose(
  branchVm: GitBranchComposeVm,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(modifier = Modifier.requiredWidth(16.dp))
    val rangesToHighlight by branchVm.matchingFragments.collectAsState()
    val highlightColor = UIUtil.getSearchMatchGradientStartColor().toComposeColor()
    val highlightedBranchName = remember(rangesToHighlight, highlightColor) {
      branchVm.name.highlightRanges(rangesToHighlight, highlightColor)
    }
    Text(highlightedBranchName, maxLines = 1, overflow = TextOverflow.Clip)
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