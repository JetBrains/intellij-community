package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.VirtualFile
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language

fun Finder.vcsLogGraphTable(@Language("xpath") xpath: String? = null, action: VcsLogGraphTableUi.() -> Unit = {}): VcsLogGraphTableUi {
  return x(xpath ?: xQuery { byClass("VcsLogMainGraphTable") }, VcsLogGraphTableUi::class.java).apply { action() }
}

class VcsLogGraphTableUi(data: ComponentData) : JTableUiComponent(data) {
  val logTable: VcsLogGraphTable by lazy { driver.cast(component, VcsLogGraphTable::class) }
}

@Remote("com.intellij.vcs.log.ui.table.VcsLogGraphTable", plugin = "com.intellij/intellij.platform.vcs.log.impl")
interface VcsLogGraphTable {
  fun getId(): String
  fun getSelection(): VcsLogCommitSelection
}

@Remote("com.intellij.vcs.log.VcsLogCommitSelection")
interface VcsLogCommitSelection {
  val commits: List<CommitId>
}

@Remote("com.intellij.vcs.log.CommitId")
interface CommitId {
  fun getHash(): Hash
  fun getRoot(): VirtualFile
}

@Remote("com.intellij.vcs.log.Hash")
interface Hash {
  fun asString(): String
}