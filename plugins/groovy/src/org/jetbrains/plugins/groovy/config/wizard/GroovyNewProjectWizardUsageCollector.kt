// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils

class GroovyNewProjectWizardUsageCollector : CounterUsagesCollector() {

  override fun getGroup() = GROUP

  internal enum class DistributionType { MAVEN, LOCAL }

  companion object {
    private val GROUP = EventLogGroup("new.project.wizard.interactions.groovy", 1)

    private val SESSION_ID_FIELD = EventFields.Int("wizard_session_id")
    private val VERSION_FIELD = EventFields.StringValidatedByInlineRegexp("version", GroovyConfigUtils.GROOVY_VERSION_REGEX.pattern())
    private val SOURCE_TYPE_FIELD = EventFields.Enum("source", DistributionType::class.java) {
      when (it) {
        DistributionType.MAVEN -> "maven"
        DistributionType.LOCAL -> "local"
      }
    }

    private val GROOVY_LIB_SELECTED = GROUP.registerEvent("select.groovy.lib", SESSION_ID_FIELD, SOURCE_TYPE_FIELD, VERSION_FIELD)

    @JvmStatic
    internal fun logGroovyLibrarySelected(context: WizardContext, type: DistributionType, version: String) =
      GROOVY_LIB_SELECTED.log(context.project, context.sessionId.id, type, version)

    @JvmStatic
    fun logGroovyLibrarySelected(context: WizardContext, version: String) = logGroovyLibrarySelected(context, DistributionType.MAVEN, version)
  }
}