// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.ui.table.VcsLogIconCellRenderer
import git4idea.GitIcons
import git4idea.i18n.GitBundle.message
import javax.swing.Icon

internal class GitCommitSignatureLogCellRenderer : VcsLogIconCellRenderer() {
  override fun customize(table: VcsLogGraphTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    val signature = value as? GitCommitSignature

    icon = getIcon(signature)
    toolTipText = getToolTip(signature).toString()
  }
}

private fun getIcon(signature: GitCommitSignature?): Icon? =
  when (signature) {
    is GitCommitSignature.Verified -> GitIcons.Verified
    GitCommitSignature.NotVerified -> GitIcons.Signed
    null, GitCommitSignature.NoSignature -> null
  }

private fun getToolTip(signature: GitCommitSignature?): HtmlChunk =
  when (signature) {
    is GitCommitSignature.Verified ->
      HtmlBuilder()
        .append(message("tooltip.commit.signature.verify.success"))
        .append(HtmlChunk.br())
        .append(signature.user)
        .append(HtmlChunk.br())
        .append(signature.fingerprint)
        .toFragment()

    GitCommitSignature.NotVerified -> HtmlChunk.text(message("tooltip.commit.signature.verify.failure"))

    null, GitCommitSignature.NoSignature -> HtmlChunk.text(message("tooltip.no.commit.signature"))
  }