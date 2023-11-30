// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.baseEvents

import com.intellij.ae.database.activities.ReadableUserActivity
import com.intellij.ae.database.activities.WritableDatabaseBackedCounterUserActivity
import com.intellij.ae.database.baseEvents.FusBasedCounterUserActivity.Factory
import com.intellij.ae.database.utils.InstantUtils
import com.intellij.internal.statistic.eventLog.EventLogListenersManager
import com.intellij.internal.statistic.eventLog.StatisticsEventLogListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.asSafely
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

data class FusEventDefinitionField<T>(
  val type: Class<T>,
  val comparator: (T) -> Boolean
)

/**
 * FUS-based counter user activity. You should define the event with [define] method. The resulting object should look something like this:
 *
 * ```
 * object SampleFusBasedUserActivity : FusBasedCounterUserActivity() {
 *   internal class Factory : FusBasedCounterUserActivity.Factory {
 *     override fun getInstance() = SampleFusBasedUserActivity
 *   }
 *
 *   override fun define() // see doc for [define]
 * }
 * ```
 *
 * You need to register your activity in XML:
 * ```
 * <fusBasedCounterUserActivity implementation="com.intellij.ae.database.v2.events.SampleFusBasedUserActivity$Factory"/>
 * ```
 *
 * `implementation` is a path to [Factory] class. Note the dollar symbol at the end.
 */
abstract class FusBasedCounterUserActivity : ReadableUserActivity<Int>, WritableDatabaseBackedCounterUserActivity() {
  companion object {
    val EP_NAME = ExtensionPointName.create<Factory>("com.intellij.ae.database.fusBasedCounterUserActivity")
  }

  interface Factory {
    fun getInstance(): FusBasedCounterUserActivity
  }

  protected class FusEventDefinitionBuilder(private val id: String) {
    val fields = mutableMapOf<String, FusEventDefinitionField<*>>()
    private var myGroup: String? = null
    private var myEvent: String? = null

    inner class FieldsBuilder {
      inline fun <reified T> field(name: String, value: T) = field<T>(name) { it == value }

      inline fun <reified T> field(name: String, noinline comparator: (T) -> Boolean) {
        fields[name] = FusEventDefinitionField(T::class.java, comparator)
      }
    }

    fun event(group: String, event: String, x: FieldsBuilder.() -> Unit) {
      myGroup = group
      myEvent = event

      x(FieldsBuilder())
    }

    fun event(group: String, event: String) {
      event(group, event) {}
    }

    internal fun build(): FusEventDefinition {
      val group = myGroup ?: error("Group is not defined")
      val event = myEvent ?: error("Event is not defined")

      return FusEventDefinition(id, group, event, fields as Map<String, FusEventDefinitionField<Any>>)
    }
  }

  internal data class FusEventDefinition(
    val id: String,
    val group: String,
    val event: String,
    val fields: Map<String, FusEventDefinitionField<Any>>
  )

  internal val definition: FusEventDefinition by lazy { define().build() }

  final override val id: String by lazy { definition.id }

  final override suspend fun getActivityValue(): Int {
    return getDatabase().getActivitySum(this, null, null)
  }

  internal suspend fun increment(fields: Map<String, Any>, eventTime: Instant) {
    getDatabase().submit(this, getIncrementValue(fields), eventTime)
  }

  /**
   * Override this method if your need to do custom calculations over FUS data
   *
   * Example 1:
   * You want to write down every debug session start. In this case '1' is good for you here, no need to override
   *
   * Example 2:
   * You want to calculate how many characters completion saved. You need to subtract 'typing' field from 'token_length' field.
   * Of course, don't forget null checks. The resulting method should look something like this:
   * ```
   * val tokenLen = fields["token_length"] as? Int
   * val typing = fields["typing"] as? Int
   * if (tokenLen == null || typing == null) {
   *   thisLogger().error("One of required fields is null")
   *   return 1
   * }
   * return tokenLen - typing
   * ```
   */
  protected open fun getIncrementValue(fields: Map<String, Any>) = 1

  protected fun definition(id: String, x: FusEventDefinitionBuilder.() -> Unit): FusEventDefinitionBuilder {
    return FusEventDefinitionBuilder(id).apply(x)
  }

  /**
   * A definition of event.
   *
   * Starts with [definition] function. It accepts 'id' as an argument – it will be used in [id] object field; and a lambda.
   * Lambda contains definition of FUS event. You should call [FusEventDefinitionBuilder.event] method and pass event group, event id and a lambda.
   * This lambda contains a list of fields that should be present in an event.
   *
   * Example:
   * ```
   * definition("sampleEvent") {
   *   event("toolwindow", "activated") {
   *     field("id", "Project")
   *     field<Int>("invocation") { it > 10 }
   *   }
   * }
   * ```
   *
   * It defines user activity "sampleEvent", which is FUS event "toolwindow.activated" with fields "id" = "Project" and "invokation" > 10.
   * Note that logical condition between fields is 'AND' – all fields should satisfy the condition
   */
  protected abstract fun define(): FusEventDefinitionBuilder
}

@Service(Service.Level.APP)
internal class FusBasedCounterUserActivityService(cs: CoroutineScope) {
  companion object {
    fun getInstance() = ApplicationManager.getApplication().service<FusBasedCounterUserActivityService>()
  }

  private data class ActivitySubmission(val group: String, val event: String, val fields: Map<String, Any>, val time: Instant)

  private val activitiesSubmitFlow = MutableSharedFlow<ActivitySubmission>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  // group to event
  private val activitiesLock = Mutex()
  private val activities = mutableMapOf<Pair<String, String>, MutableList<FusBasedCounterUserActivity>>()

  init {
    cs.launch {
      activitiesSubmitFlow.collect {
        val activity = find(it.group, it.event, it.fields)
        if (activity != null) {
          activity.increment(it.fields, it.time)
        }
      }
    }
  }

  fun submit(group: String, event: String, fields: Map<String, Any>) {
    val time = fields["created"]?.asSafely<Long>()?.let { Instant.ofEpochMilli(it) } ?: InstantUtils.Now
    activitiesSubmitFlow.tryEmit(ActivitySubmission(group, event, fields, time))
  }

  private suspend fun find(group: String, event: String, fields: Map<String, Any>): FusBasedCounterUserActivity? {
    activitiesLock.withLock {
      if (activities.isEmpty()) {
        for (activity in FusBasedCounterUserActivity.EP_NAME.extensionList.map { it.getInstance() }) {
          val key = activity.definition.group to activity.definition.event
          activities.getOrPut(key) { mutableListOf() }.add(activity)
        }
      }
    }

    val suitableActivities = activities[group to event] ?: return null

    // TODO there might be more than one activity
    return suitableActivities.find { activity ->
      activity.definition.fields.all { definitionField ->
        val value = fields[definitionField.key] ?: return@all false
        val definition = definitionField.value
        if (definition.type != value::class.java) return@all false

        definition.comparator(value)
      }
    }
  }
}

private class Listener : StatisticsEventLogListener {
  override fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?) {
    // rawEventId and rawData can't be used

    val group = validatedEvent.group.id
    val event = validatedEvent.event.id
    val fields = validatedEvent.event.data

    FusBasedCounterUserActivityService.getInstance().submit(group, event, fields)
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