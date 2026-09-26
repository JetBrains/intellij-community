// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.attach.XAttachPresentationGroup
import com.intellij.xdebugger.impl.actions.AttachToProcessActionBase
import com.intellij.xdebugger.impl.actions.AttachToProcessActionBase.AttachToProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.diagnostics.ProcessesFetchingProblemException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import org.jetbrains.annotations.ApiStatus
import java.util.function.Predicate
import java.util.function.Supplier
import kotlin.coroutines.coroutineContext

private val logger = Logger.getInstance("AttachDialogCollectItemsUtil")

/**
 * Actions added to the [AttachDialogSettings] group can implement this interface to
 * affect list of processes. Only processes accepted by all predicates will be shown.
 */
@ApiStatus.Internal
interface ProcessPredicate : Supplier<Predicate<ProcessInfo>>

suspend fun collectAttachProcessItemsGroupByProcessInfo(
  project: Project,
  host: XAttachHost,
  attachDebuggerProviders: List<XAttachDebuggerProvider>): AttachItemsInfo {
  try {
    val processes = host.getProcessListAsync()

    val debuggerProviders = attachDebuggerProviders.filter { it.isAttachHostApplicable(host) }
    val dataHolder = UserDataHolderBase()

    val allItems = mutableListOf<AttachToProcessItem>()

    val processesToAttachItems = processes.associateWith { processInfo ->
      coroutineContext.ensureActive()

      val providersWithItems = mutableMapOf<XAttachPresentationGroup<*>, MutableList<AttachToProcessItem>>()

      for (debuggerProvider in debuggerProviders) {
        val itemsFromProvider = debuggerProvider.getAvailableDebuggers(project, host, processInfo, dataHolder).map {
          AttachToProcessItem(debuggerProvider.presentationGroup, false, host, processInfo, listOf(it), project, dataHolder)
        }
        if (itemsFromProvider.any()) {
          providersWithItems.putIfAbsent(debuggerProvider.presentationGroup, itemsFromProvider.toMutableList())?.addAll(itemsFromProvider)
          allItems.addAll(itemsFromProvider)
        }
      }
      AttachDialogProcessItem.create(processInfo, providersWithItems, dataHolder)
    }

    val recentItems = getRecentItems(allItems, processesToAttachItems, host, project, dataHolder)

    return AttachItemsInfo(processesToAttachItems.values.toList(), recentItems, dataHolder)

  }
  catch (processesFetchingProblemException: ProcessesFetchingProblemException) {
    throw processesFetchingProblemException
  }
  catch (cancellationException: CancellationException) {
    throw cancellationException
  }
  catch (_: ExecutionException) {
    return AttachItemsInfo.EMPTY
  }
  catch (t: Throwable) {
    logger.error(t)
    return AttachItemsInfo.EMPTY
  }
}

private fun getRecentItems(currentItems: List<AttachToProcessItem>,
                           currentDialogItems: Map<ProcessInfo, AttachDialogProcessItem>,
                           host: XAttachHost,
                           project: Project,
                           dataHolder: UserDataHolderBase): List<AttachDialogRecentItem> {
  return AttachToProcessActionBase.getRecentItems(currentItems, host, project, dataHolder).groupBy { it.processInfo.pid }.mapNotNull { groupedItems ->
    val firstItem = groupedItems.value.first()
    val processInfo = firstItem.processInfo
    val dialogItem = currentDialogItems[processInfo]
    if (dialogItem == null) {
      logger.error("Unable to get all available debuggers for the recent item $processInfo")
      return@mapNotNull null
    }

    return@mapNotNull AttachDialogRecentItem(dialogItem, groupedItems.value.sortedBy { it.group.order }.flatMap { it.debuggers })
  }
}
