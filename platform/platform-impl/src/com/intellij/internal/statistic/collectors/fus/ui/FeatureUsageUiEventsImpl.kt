// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.FeatureUsageUiEvents
import com.intellij.internal.statistic.eventLog.events.ClassEventField
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByCustomRule
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ExitActionType
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object DialogsCounterUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("ui.dialogs", 63)

  val EXIT_CODE: PrimitiveEventField<Int> = object: PrimitiveEventField<Int>() {
    override val name: String = "code"

    override val validationRule: List<String>
      get() = listOf("{enum:0|1|2}")

    override fun addData(fuData: FeatureUsageData, value: Int) {
      val toReport = getExitCodeToReport(value)
      fuData.addData(name, toReport)
    }

    private fun getExitCodeToReport(exitCode: Int): Int {
      if (exitCode == DialogWrapper.OK_EXIT_CODE || exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
        return exitCode
      }
      return DialogWrapper.NEXT_USER_EXIT_CODE
    }
  }

  val DIALOG_CLASS: ClassEventField = EventFields.Class("dialog_class")
  val INVOCATION_PLACE: StringEventField = StringValidatedByCustomRule("dialog_invocation_place", ListValidationRule::class.java)
  val EXIT_ACTION_TYPE = EnumEventField("exit_action_type", ExitActionType::class.java) { it.name }

  val SHOW: VarargEventId = GROUP.registerVarargEvent("show", DIALOG_CLASS, INVOCATION_PLACE, EventFields.PluginInfo)
  val CLOSE: VarargEventId = GROUP.registerVarargEvent("close", DIALOG_CLASS, INVOCATION_PLACE, EXIT_ACTION_TYPE, EXIT_CODE, EventFields.PluginInfo)
  val HELP: VarargEventId = GROUP.registerVarargEvent("help.clicked", DIALOG_CLASS, INVOCATION_PLACE, EventFields.PluginInfo)

  override fun getGroup(): EventLogGroup = GROUP
}

 /**This ensures that invocation places are collected only from internal plugins
 and plugins from the marketplace that are safe to report.
 This is done to prevent the collection of any potentially personal or sensitive information,
 as only explicitly-defined and safe invocation places are considered.*/
internal class ListValidationRule : CustomValidationRule() {
  override fun getRuleId(): String = "dialog_invocation_place"
  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val invocationPlaces = DialogInvocationPlacesCollector.getInstance().getInvocationPlaces()
    if (invocationPlaces.contains(data)) return ValidationResultType.ACCEPTED
    return ValidationResultType.REJECTED
  }
}

internal object SettingsCounterUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("ui.settings", 65)

  internal val CONFIGURABLE_CLASS = EventFields.Class("configurable")
  val SELECT: EventId3<Class<*>?, Boolean, Long> = GROUP.registerEvent("select",
                                                                       CONFIGURABLE_CLASS,
                                                                       EventFields.Boolean("loaded_from_cache"),
                                                                       EventFields.DurationMs)
  val APPLY: EventId1<Class<*>?> = GROUP.registerEvent("apply", CONFIGURABLE_CLASS)
  val RESET: EventId1<Class<*>?> = GROUP.registerEvent("reset", CONFIGURABLE_CLASS)
  @JvmField
  val SEARCH: EventId3<Class<*>?, Int, Int> = GROUP.registerEvent("search", CONFIGURABLE_CLASS,
                                                                  EventFields.Int("hits"),
                                                                  EventFields.Int("characters"))
  val ADVANDED_SETTINGS_SEARCH: EventId3<Int, Int, Boolean> = GROUP.registerEvent("advanced.settings.search",
                                                                                  EventFields.Int("hits"),
                                                                                  EventFields.Int("characters"),
                                                                                  EventFields.Boolean("modifiedOnly"))
  @JvmField
  val EXTERNAL_CHANGE_WHILE_MODIFIED: EventId1<Class<*>?> = GROUP.registerEvent("external.change.while.modified", CONFIGURABLE_CLASS)

  internal val ENTRY = EventFields.Enum<SettingsOpeningTracker.OpeningEntry>("entry", "The way that the opening starts") { it.name.lowercase() }
  internal val FIRST_IN_SESSION = EventFields.Boolean("first_in_session", "The opening creates the first Settings editor of the IDE session")
  /** Logged by [SettingsOpeningTracker]. */
  internal val OPENED: VarargEventId = GROUP.registerVarargEvent("opened",
                                                                 ENTRY,
                                                                 CONFIGURABLE_CLASS,
                                                                 FIRST_IN_SESSION,
                                                                 EventFields.DurationMs)

  override fun getGroup(): EventLogGroup = GROUP
}

/**
 * Tracks a Settings opening from its entry point until the first page of the new Settings editor is ready,
 * and logs it as the `opened` event of [SettingsCounterUsagesCollector].
 */
@ApiStatus.Internal
object SettingsOpeningTracker {
  /**
   * The way that a Settings opening starts, for the `opened` event.
   */
  enum class OpeningEntry {
    /**
     * The Settings action: the shortcut, the File menu, the Welcome screen, the action in Search Everywhere,
     * or the Settings item of the macOS application menu.
     */
    ACTION,
    /** A settings option that the user selects in Search Everywhere. */
    SEARCH_EVERYWHERE,
    /** An open of a page by its id from code. */
    BY_ID,
    /** An open of a page by its class from code. */
    BY_CLASS,
    /** Any other open from code, for example by a display name, by a predicate or with given groups. */
    OTHER,
  }

  /**
   * One Settings opening of [project] for the `opened` event.
   *
   * [firstInSession] is true when the opening creates the first Settings editor of the IDE session.
   * That opening loads the classes of the tree, the dialog and the page, so it takes longer.
   */
  class Opening internal constructor(
    internal val project: Project,
    internal val entry: OpeningEntry,
    internal val startNanos: Long,
    internal val firstInSession: Boolean = false,
  )

  /**
   * The Settings opening that has not created a [com.intellij.openapi.options.newEditor.SettingsEditor] yet,
   * or null when no opening is pending.
   */
  internal val pendingOpening: AtomicReference<Opening?> = AtomicReference()

  /**
   * True after the first [com.intellij.openapi.options.newEditor.SettingsEditor] of the IDE session is created.
   */
  private val editorCreated = AtomicBoolean()

  /**
   * Runs [block], which opens the Settings window of [project], as one opening for the `opened` event.
   * A null [project] means the default project, the same as for the Settings dialog.
   *
   * [entry] is the way that the opening starts. The opening starts at this call, so a caller that waits for the project
   * startup calls it after the wait. The opening is pending only while [block] runs. A nested call keeps the outer opening,
   * so the outer call sets the entry. When [block] returns, the opening is discarded. So an opening that reuses an open window,
   * or that fails, leaves nothing for a later window.
   */
  internal inline fun <T> trackOpening(project: Project?, entry: OpeningEntry, block: () -> T): T {
    val opening = Opening(currentOrDefaultProject(project), entry, System.nanoTime())
    // an earlier opening that is still pending is kept, so the outer call sets the entry
    val started = pendingOpening.compareAndSet(null, opening)
    try {
      return block()
    }
    finally {
      if (started) {
        // an opening that started later is kept
        pendingOpening.compareAndSet(opening, null)
      }
    }
  }

  /**
   * The same as [trackOpening], for a call from Java or from another module.
   */
  @JvmStatic
  fun runTrackedOpening(project: Project?, entry: OpeningEntry, block: Runnable) {
    trackOpening(project, entry) { block.run() }
  }

  /**
   * Called by a new Settings editor of [project]. Returns the opening that creates the editor, or null when that opening
   * is not tracked. A reused window creates no editor, so it takes no opening. An opening of another project stays pending.
   * Every new editor calls it, so the first call of the IDE session marks [Opening.firstInSession], even when no opening is pending.
   */
  @JvmStatic
  fun takeOpening(project: Project): Opening? {
    val firstInSession = editorCreated.compareAndSet(false, true)
    val opening = pendingOpening.get() ?: return null
    if (opening.project !== project || !pendingOpening.compareAndSet(opening, null)) {
      return null
    }
    return Opening(opening.project, opening.entry, opening.startNanos, firstInSession)
  }

  /**
   * Logs the entry of [opening], the class of [configurable], whether [opening] is the first in the IDE session,
   * and the duration from the start of [opening] until now.
   * Called when [configurable], the first page of a new Settings editor, is ready. [configurable] is null when the editor selects no page.
   */
  @JvmStatic
  fun logOpened(project: Project?, opening: Opening, configurable: Configurable?) {
    val durationNanos = System.nanoTime() - opening.startNanos
    // the page exists already, so the unwrap creates nothing
    val page = (configurable as? ConfigurableWrapper)?.configurable ?: configurable
    SettingsCounterUsagesCollector.OPENED.log(
      project,
      SettingsCounterUsagesCollector.ENTRY.with(opening.entry),
      SettingsCounterUsagesCollector.CONFIGURABLE_CLASS.with(page?.javaClass),
      SettingsCounterUsagesCollector.FIRST_IN_SESSION.with(opening.firstInSession),
      EventFields.DurationMs.with(TimeUnit.NANOSECONDS.toMillis(durationNanos)),
    )
  }
}

internal class FeatureUsageUiEventsImpl : FeatureUsageUiEvents {
  override fun logSelectConfigurable(configurable: Configurable, loadedFromCache: Boolean, loadTimeMs: Long) {
    if (FeatureUsageLogger.getInstance().isEnabled()) {
      val wrapper = configurable as? ConfigurableWrapper
      SettingsCounterUsagesCollector.SELECT.log(
        wrapper?.project,
        (wrapper?.configurable ?: configurable)::class.java,
        loadedFromCache,
        loadTimeMs
      )
    }
  }

  override fun logApplyConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.getInstance().isEnabled()) {
      logSettingsEvent(configurable, SettingsCounterUsagesCollector.APPLY)
    }
  }

  override fun logResetConfigurable(configurable: Configurable) {
    if (FeatureUsageLogger.getInstance().isEnabled()) {
      logSettingsEvent(configurable, SettingsCounterUsagesCollector.RESET)
    }
  }

  private fun logSettingsEvent(configurable: Configurable, event: EventId1<Class<*>>) {
    val wrapper = configurable as? ConfigurableWrapper
    event.log(wrapper?.project, (wrapper?.configurable ?: configurable)::class.java)
  }

  override fun logShowDialog(clazz: Class<*>, invocationPlace: String?) {
    if (FeatureUsageLogger.getInstance().isEnabled()) {
      DialogsCounterUsagesCollector.SHOW.log(DialogsCounterUsagesCollector.DIALOG_CLASS.with(clazz),
                                             DialogsCounterUsagesCollector.INVOCATION_PLACE.with(invocationPlace))
    }
  }

  override fun logCloseDialog(clazz: Class<*>, exitCode: Int, exitActionType: ExitActionType, invocationPlace: String?) {
    if (FeatureUsageLogger.getInstance().isEnabled()) {
      DialogsCounterUsagesCollector.CLOSE.log(
        DialogsCounterUsagesCollector.DIALOG_CLASS.with(clazz),
        DialogsCounterUsagesCollector.INVOCATION_PLACE.with(invocationPlace),
        DialogsCounterUsagesCollector.EXIT_ACTION_TYPE.with(exitActionType),
        DialogsCounterUsagesCollector.EXIT_CODE.with(exitCode)
      )
    }
  }

  override fun logClickOnHelpDialog(clazz: Class<*>, invocationPlace: String?) {
    if (FeatureUsageLogger.getInstance().isEnabled()) {
      DialogsCounterUsagesCollector.HELP.log(DialogsCounterUsagesCollector.DIALOG_CLASS.with(clazz),
                                             DialogsCounterUsagesCollector.INVOCATION_PLACE.with(invocationPlace))
    }
  }
}