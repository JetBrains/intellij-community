package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.containers.MultiMap
import com.intellij.xdebugger.attach.XAttachDebugger
import com.intellij.xdebugger.attach.XAttachDebuggerProvider
import com.intellij.xdebugger.attach.XAttachHost
import com.intellij.xdebugger.attach.XAttachPresentationGroup
import com.intellij.xdebugger.impl.actions.AttachToProcessActionBase
import com.intellij.xdebugger.impl.actions.AttachToProcessActionBase.AttachToProcessItem
import com.intellij.xdebugger.impl.ui.attach.dialog.diagnostics.ProcessesFetchingProblemException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

private val logger = Logger.getInstance("AttachDialogCollectItemsUtil")

internal suspend fun collectAttachProcessItemsGroupByProcessInfo(
  project: Project,
  host: XAttachHost,
  attachDebuggerProviders: List<XAttachDebuggerProvider>): AttachItemsInfo {
  try {
    val processes = host.processList

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
  catch (executionException: ExecutionException) {
    return AttachItemsInfo.EMPTY
  }
  catch (t: Throwable) {
    logger.error(t)
    return AttachItemsInfo.EMPTY
  }
}

internal suspend fun collectAttachProcessItems(project: Project,
                                               host: XAttachHost,
                                               attachDebuggerProviders: List<XAttachDebuggerProvider>): AttachItemsInfo {

  return try {
    val dataHolder = UserDataHolderBase()

    val currentItems = mutableListOf<AttachToProcessItem>()
    val dialogItems = mutableListOf<AttachDialogProcessItem>()

    val applicableDebuggerProviders = attachDebuggerProviders.filter { it.isAttachHostApplicable(host) }
    for (process in host.processList) {
      coroutineContext.ensureActive()
      val groupsWithDebuggers = MultiMap<XAttachPresentationGroup<ProcessInfo>, XAttachDebugger>()
      for (provider in applicableDebuggerProviders) {
        groupsWithDebuggers.putValues(provider.presentationGroup,
                                      provider.getAvailableDebuggers(project, host, process!!, dataHolder))
      }
      for (group in groupsWithDebuggers.keySet()) {
        val debuggers = groupsWithDebuggers[group]
        if (!debuggers.isEmpty()) {
          val attachToProcessItem = AttachToProcessItem(group, false, host, process!!, ArrayList(debuggers), project, dataHolder)
          currentItems.add(attachToProcessItem)
          dialogItems.add(attachToProcessItem.toDialogItem())
        }
      }
    }

    val recentItems = getRecentItems(currentItems, host, project, dataHolder)

    AttachItemsInfo(dialogItems, recentItems, dataHolder)
  }
  catch (processesFetchingProblemException: ProcessesFetchingProblemException) {
    throw processesFetchingProblemException
  }
  catch (cancellationException: CancellationException) {
    throw cancellationException
  }
  catch (executionException: ExecutionException) {
    AttachItemsInfo.EMPTY
  }
  catch (t: Throwable) {
    logger.error(t)
    AttachItemsInfo.EMPTY
  }
}

private fun getRecentItems(currentItems: List<AttachToProcessItem>,
                           host: XAttachHost,
                           project: Project,
                           dataHolder: UserDataHolderBase): List<AttachDialogProcessItem> {
  return AttachToProcessActionBase.getRecentItems(currentItems, host, project, dataHolder).groupBy { it.processInfo.pid }.map { groupedItems ->
    val firstItem = groupedItems.value.first()
    val processInfo = firstItem.processInfo
    return@map AttachDialogProcessItem.create(processInfo, groupedItems.value.groupBy { it.group }, dataHolder)
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

private fun AttachToProcessItem.toDialogItem(): AttachDialogProcessItem {
  return AttachDialogProcessItem.create(processInfo, mapOf(group to listOf(this)), dataHolder)
}