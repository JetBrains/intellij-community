// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.core.baseEvents

import com.intellij.ae.database.core.activities.WritableDatabaseBackedCounterUserActivity
import com.intellij.ae.database.core.baseEvents.fus.FusEventCatcher
import com.intellij.ae.database.core.baseEvents.fus.FusEventDefinitionBuilder
import java.time.Instant

/**
 * FUS-based counter user activity. You should define the event with [define] method.
 *
 * You need to register your activity in XML
 *
 * @see com.intellij.ae.database.baseEvents.fus.FusEventCatcher
 */
abstract class FusBasedCounterUserActivity : WritableDatabaseBackedCounterUserActivity() {
  protected val catcher = object : FusEventCatcher() {
    override fun define(): FusEventDefinitionBuilder {
      return this@FusBasedCounterUserActivity.define()
    }

    override suspend fun onEvent(fields: Map<String, Any>, eventTime: Instant) {
      getDatabase().submit(this@FusBasedCounterUserActivity, getIncrementValue(fields), eventTime)
    }
  }

  final override val id: String by lazy { catcher.definition.id }

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

  /**
   * @see com.intellij.ae.database.baseEvents.fus.FusEventCatcher.define
   */
  protected abstract fun define(): FusEventDefinitionBuilder

  /**
   * @see com.intellij.ae.database.baseEvents.fus.FusEventCatcher.definition
   */
  protected fun definition(id: String, x: FusEventDefinitionBuilder.() -> Unit) = catcher.definition(id, x)
}

