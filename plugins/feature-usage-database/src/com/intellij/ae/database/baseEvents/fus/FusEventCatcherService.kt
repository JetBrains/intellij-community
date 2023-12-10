package com.intellij.ae.database.baseEvents.fus

import com.intellij.ae.database.utils.InstantUtils
import com.intellij.internal.statistic.eventLog.EventLogListenersManager
import com.intellij.internal.statistic.eventLog.StatisticsEventLogListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.util.asSafely
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Service that receives all FUS events and tries to find suitable [FusEventCatcher]
 *
 * TODO: lifetime of this service is longer than lifetime of [FusEventCatcher], because a catcher can be implemented inside a plugin,
 * TODO: but an EP is marked is marked as non-dynamic and there is no catchers outside impl-detail plugin
 */
@Service(Service.Level.APP)
internal class FusEventCatcherService(cs: CoroutineScope) {
  companion object {
    internal fun getInstance() = ApplicationManager.getApplication().service<FusEventCatcherService>()
  }

  private data class EventSubmission(val group: String, val event: String, val fields: Map<String, Any>, val time: Instant)

  private val eventsSubmitFlow = MutableSharedFlow<EventSubmission>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  // group to event
  private val catchers = mutableMapOf<Pair<String, String>, MutableList<FusEventCatcher>>()
  private val catchersLock = Mutex()

  init {
    cs.launch {
      eventsSubmitFlow.collect {
        val catchers = find(it.group, it.event, it.fields)
        for (catcher in catchers) {
          catcher.onEvent(it.fields, it.time)
        }
      }
    }
  }

  fun submit(group: String, event: String, fields: Map<String, Any>) {
    val time = fields["created"]?.asSafely<Long>()?.let { Instant.ofEpochMilli(it) } ?: InstantUtils.Now
    eventsSubmitFlow.tryEmit(EventSubmission(group, event, fields, time))
  }

  private suspend fun find(group: String, event: String, fields: Map<String, Any>): List<FusEventCatcher> {
    val suitableCatchers = catchersLock.withLock {
      if (catchers.isEmpty()) {
        initCatchers()
      }
      catchers[group to event]
    }

    return suitableCatchers?.filter { catcher ->
      catcher.definition.fields.all { definitionField ->
        val value = fields[definitionField.key] ?: return@all false
        val definition = definitionField.value
        if (definition.type != value::class.java) return@all false

        definition.comparator(value)
      }
    } ?: emptyList()
  }

  // Must run under [catchersLock]
  private fun initCatchers() {
    assert(catchersLock.isLocked)

    for (catcher in FusEventCatcher.EP_NAME.extensionList.map { it.getInstance() }) {
      val key = catcher.definition.group to catcher.definition.event
      catchers.getOrPut(key) { mutableListOf() }.add(catcher)
    }
  }
}

private class Listener : StatisticsEventLogListener {
  override fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?) {
    // rawEventId and rawData can't be used

    val group = validatedEvent.group.id
    val event = validatedEvent.event.id
    val fields = validatedEvent.event.data

    FusEventCatcherService.getInstance().submit(group, event, fields)
  }
}

class AddStatisticsEventLogListenerTemporary : Disposable {
  private val myListener = Listener()

  init {
    ApplicationManager.getApplication().let { application ->
      if (!application.isUnitTestMode) {
        application.service<EventLogListenersManager>().subscribe(myListener, "FUS")
      }
    }
  }

  override fun dispose() {
    ApplicationManager.getApplication().serviceIfCreated<EventLogListenersManager>()?.unsubscribe(myListener, "FUS")
  }
}