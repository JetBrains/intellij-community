// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.execution.process.ProcessInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.application
import com.intellij.xdebugger.attach.XAttachDebugger
import com.intellij.xdebugger.attach.XAttachPresentationGroup
import com.intellij.xdebugger.impl.actions.AttachToProcessActionBase.AttachToProcessItem
import org.jetbrains.annotations.Nls

open class AttachDialogProcessItem protected constructor(
  val processInfo: ProcessInfo,
  val groupsWithItems: List<Pair<XAttachPresentationGroup<*>, List<AttachToProcessItem>>>,
  val dataHolder: UserDataHolder) {

  companion object {
    fun create(processInfo: ProcessInfo,
               groupsWithItems: Map<XAttachPresentationGroup<*>, List<AttachToProcessItem>>,
               dataHolder: UserDataHolder): AttachDialogProcessItem {
      return AttachDialogProcessItem(processInfo, groupsWithItems.toList().sortedBy { it.first.order }, dataHolder)
    }
  }

  private val allDebuggers = lazy { groupsWithItems.flatMap { it.second }.flatMap { it.debuggers } }
  private val allGroups = lazy { groupsWithItems.map { it.first }.toSet() }

  private val presentationInfo = lazy { application.getService(AttachDialogPresentationService::class.java).getItemPresentationInfo(this) }

  fun getGroups(): Set<XAttachPresentationGroup<*>> = allGroups.value

  open fun getMainDebugger(state: AttachDialogState): XAttachDebugger? {
    val groups = groupsWithItems.toList().sortedBy { it.first.order }
    val firstGroup = groups.firstOrNull { state.selectedDebuggersFilter.get().canBeAppliedTo(setOf(it.first)) } ?: groups.firstOrNull()
    return firstGroup?.second?.flatMap { it.debuggers }?.firstOrNull()
  }

  val debuggers: List<XAttachDebugger>
    get() = allDebuggers.value

  @get:Nls
  val executableText: String
    get() = presentationInfo.value.executableText

  @get:Nls
  val commandLineText: String
    get() = presentationInfo.value.commandLineText

  val executableTextAttributes: SimpleTextAttributes?
    get() = presentationInfo.value.executableTextAttributes

  val commandLineTextAttributes: SimpleTextAttributes?
    get() = presentationInfo.value.commandLineTextAttributes

  val indexedString: String
    get() = presentationInfo.value.indexedString

  override fun toString(): String = "pid${processInfo.pid}"
}

internal class AttachDialogRecentItem(dialogItem: AttachDialogProcessItem,
                                      private val recentDebuggers: List<XAttachDebugger>): AttachDialogProcessItem(
  dialogItem.processInfo, dialogItem.groupsWithItems, dialogItem.dataHolder) {

  override fun getMainDebugger(state: AttachDialogState): XAttachDebugger? {
    return recentDebuggers.firstOrNull() ?: super.getMainDebugger(state)
  }
}

data class AttachItemsInfo(
  val processItems: List<AttachDialogProcessItem>,
  val recentItems: List<AttachDialogProcessItem>,
  val dataHolder: UserDataHolder) {
  companion object {
    val EMPTY = AttachItemsInfo(emptyList(), emptyList(), UserDataHolderBase())
  }
}