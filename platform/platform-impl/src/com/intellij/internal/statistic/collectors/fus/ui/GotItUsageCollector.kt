// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.ui.GotItTooltip
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus


class GotItTooltipAllowlistEP {
  @Attribute("prefix")
  var prefix: String = ""
}

@ApiStatus.Internal
@Service
class GotItUsageCollector private constructor() {
  companion object {
    @JvmStatic
    val instance: GotItUsageCollector
      get() = ApplicationManager.getApplication().getService(GotItUsageCollector::class.java)

    val EP_NAME : ExtensionPointName<GotItTooltipAllowlistEP> = ExtensionPointName.create("com.intellij.statistics.gotItTooltipAllowlist")
  }


  private val allowedPrefixes : MutableSet<String> = HashSet()

  init {
    EP_NAME.processWithPluginDescriptor(this::addToWhileList)
    EP_NAME.addExtensionPointListener(object: ExtensionPointListener<GotItTooltipAllowlistEP> {
      override fun extensionAdded(extension: GotItTooltipAllowlistEP, pluginDescriptor: PluginDescriptor) {
        addToWhileList(extension, pluginDescriptor)
      }

      override fun extensionRemoved(extension: GotItTooltipAllowlistEP, pluginDescriptor: PluginDescriptor) {
        allowedPrefixes.remove(extension.prefix)
      }
    }, null)

  }

  private fun addToWhileList(extension : GotItTooltipAllowlistEP, plugin : PluginDescriptor) {
    if (getPluginInfoByDescriptor(plugin).isDevelopedByJetBrains() && extension.prefix.isNotEmpty()) {
      allowedPrefixes.add(extension.prefix)
    }
  }

  fun logOpen(id: String, count: Int) {
    toPrefix(id)?.let{ GotItUsageCollectorGroup.showEvent.log(it, count) }
  }

  fun logClose(id: String, closeType: GotItUsageCollectorGroup.CloseType) {
    toPrefix(id)?.let{ GotItUsageCollectorGroup.closeEvent.log(it, closeType) }
  }

  fun toPrefix(id: String): String? {
    if (allowedPrefixes.contains(id)) {
      return id
    }

    var prefix = ""
    for (fragment in id.split(".")) {
      prefix = if (prefix.isEmpty()) fragment else "$prefix.$fragment"
      if (allowedPrefixes.contains(prefix)) {
        return prefix
      }
    }
    return null
  }
}

@ApiStatus.Internal
object GotItUsageCollectorGroup : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  @ApiStatus.Internal
  enum class CloseType(private val text: String) {
    ButtonClick("click.button"),
    LinkClick("click.link"),
    OutsideClick("click.outside"),
    AncestorRemoved("ancestor.removed"),
    EscapeShortcutPressed("escape.shortcut.pressed"),
    Timeout("timeout");

    override fun toString(): String = text
  }

  private val GROUP = EventLogGroup("got.it.tooltip", 2)

  internal val showEvent: EventId2<String?, Int> = GROUP.registerEvent("show",
                                                                       EventFields.StringValidatedByCustomRule("id_prefix",
                                                                                                               GotItIDValidator::class.java),
                                                                       EventFields.Int("count"))

  internal val closeEvent: EventId2<String?, CloseType> = GROUP.registerEvent("close",
                                                                              EventFields.StringValidatedByCustomRule("id_prefix",
                                                                                                                      GotItIDValidator::class.java),
                                                                              EventFields.Enum<CloseType>("type"))
}

internal class GotItIDValidator : CustomValidationRule() {
  override fun getRuleId(): String = GotItTooltip.PROPERTY_PREFIX

  override fun doValidate(data: String, context: EventContext): ValidationResultType =
    if (GotItUsageCollector.instance.toPrefix(data) == data) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
}