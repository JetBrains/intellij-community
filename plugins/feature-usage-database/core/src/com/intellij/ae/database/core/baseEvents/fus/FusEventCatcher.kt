// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.core.baseEvents.fus

import com.intellij.openapi.extensions.ExtensionPointName
import java.time.Instant

data class FusEventDefinitionField<T>(
  val type: Class<T>,
  val comparator: (T) -> Boolean
)

internal data class FusEventDefinition(
  val id: String,
  val group: String,
  val event: String,
  val fields: Map<String, FusEventDefinitionField<Any>>
)

class FusEventDefinitionBuilder(private val id: String) {
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
    if (myGroup != null) error("event() can be called only once (at least for now)")

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

    @Suppress("UNCHECKED_CAST")
    return FusEventDefinition(id, group, event, fields as Map<String, FusEventDefinitionField<Any>>)
  }
}

/**
 * A class that allows to catch specific FUS event. You should define the event with [define] method
 *
 * You need to register your activity in XML:
 * ```
 * <fusEventCatcher implementation="com.intellij.ae.database.v2.events.SampleFusBasedUserActivity$Factory"/>
 * ```
 *
 * `implementation` is a path to [Factory] class. Note the dollar symbol at the end.
 */
abstract class FusEventCatcher {
  companion object {
    val EP_NAME = ExtensionPointName.create<Factory>("com.intellij.ae.database.fusEventCatcher")
  }

  interface Factory {
    fun getInstance(): FusEventCatcher
  }

  internal val definition: FusEventDefinition by lazy { define().build() }

  fun definition(id: String, x: FusEventDefinitionBuilder.() -> Unit): FusEventDefinitionBuilder {
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
   * It defines user activity "sampleEvent", which is FUS event "toolwindow.activated" with fields "id" = "Project" and "invocation" > 10.
   * Note that logical condition between fields is 'AND' – all fields should satisfy the condition
   */
  protected abstract fun define(): FusEventDefinitionBuilder

  /**
   * Perform an action when FUS event from [define] occurs
   */
  abstract suspend fun onEvent(fields: Map<String, Any>, eventTime: Instant)
}