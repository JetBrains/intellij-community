package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.execution.process.ProcessInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.application
import com.intellij.xdebugger.attach.XAttachDebugger
import com.intellij.xdebugger.attach.XAttachPresentationGroup
import com.intellij.xdebugger.impl.actions.AttachToProcessActionBase

data class AttachDialogProcessItem(
  val processInfo: ProcessInfo,
  val groupsWithItems: Map<XAttachPresentationGroup<*>, List<AttachToProcessActionBase.AttachToProcessItem>>,
  val dataHolder: UserDataHolder) {

  private val allDebuggers = lazy { groupsWithItems.flatMap { it.value }.flatMap { it.debuggers } }

  private val presentationInfo = lazy { application.getService(AttachDialogPresentationService::class.java).getItemPresentationInfo(this) }

  fun getGroups(): Set<XAttachPresentationGroup<*>> = groupsWithItems.keys

  val debuggers: List<XAttachDebugger>
    get() = allDebuggers.value

  val executableText: String
    get() = presentationInfo.value.executableText

  val commandLineText: String
    get() = presentationInfo.value.commandLineText

  val executableTextAttributes: SimpleTextAttributes?
    get() = presentationInfo.value.executableTextAttributes

  val commandLineTextAttributes: SimpleTextAttributes?
    get() = presentationInfo.value.commandLineTextAttributes

  val indexedString: String
    get() = presentationInfo.value.indexedString
}

data class AttachItemsInfo(
  val processItems: List<AttachDialogProcessItem>,
  val recentItems: List<AttachDialogProcessItem>,
  val dataHolder: UserDataHolder) {
  companion object {
    val EMPTY = AttachItemsInfo(emptyList(), emptyList(), UserDataHolderBase())
  }
}