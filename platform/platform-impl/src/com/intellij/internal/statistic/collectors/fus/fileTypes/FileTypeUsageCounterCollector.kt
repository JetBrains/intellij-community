// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes

import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.PluginBundledTemplate
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Class
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByCustomRule
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.IncompleteDependenciesService
import com.intellij.openapi.project.IncompleteDependenciesService.DependenciesState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

private val LOG = Logger.getInstance(FileTypeUsageCounterCollector::class.java)

@ApiStatus.Internal
object FileTypeUsageCounterCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("file.types.usage", 74)

  private val FILE_EDITOR = Class("file_editor")
  private val SCHEMA: EventField<String?> = StringValidatedByCustomRule("schema", FileTypeSchemaValidator::class.java)
  private val IS_WRITABLE: EventField<Boolean> = Boolean("is_writable")
  private val IS_PREVIEW_TAB: EventField<Boolean> = Boolean("is_preview_tab")
  private val INCOMPLETE_DEPENDENCIES_MODE = Enum("incomplete_dependencies_mode",
                                                  DependenciesState::class.java)

  const val FILE_NAME_PATTERN: String = "file_name_pattern"
  const val FILE_TEMPLATE_NAME: String = "file_template_name"

  private val FILE_NAME_PATTERN_FIELD: EventField<String?> = StringValidatedByCustomRule(FILE_NAME_PATTERN,
                                                                                         FileNamePatternCustomValidationRule::class.java)
  private val FILE_TEMPLATE_FIELD: EventField<String?> = StringValidatedByCustomRule(FILE_TEMPLATE_NAME,
                                                                                     BundledFileTemplateValidationRule::class.java)

  private fun registerFileTypeEvent(eventId: String, vararg extraFields: EventField<*>?): VarargEventId {
    val baseFields = arrayOf<EventField<*>?>(EventFields.PluginInfoFromInstance, EventFields.FileType, EventFields.AnonymizedPath, SCHEMA)
    return GROUP.registerVarargEvent(eventId, *ArrayUtil.mergeArrays<EventField<*>?>(baseFields, extraFields))
  }

  private val SELECT: VarargEventId = registerFileTypeEvent("select")
  private val EDIT: VarargEventId = registerFileTypeEvent("edit", FILE_NAME_PATTERN_FIELD, EventFields.Dumb, INCOMPLETE_DEPENDENCIES_MODE)
  private val OPEN: VarargEventId = registerFileTypeEvent(
    "open", FILE_EDITOR, EventFields.TimeToShowMs, EventFields.DurationMs, IS_WRITABLE, IS_PREVIEW_TAB, FILE_NAME_PATTERN_FIELD,
    EventFields.Dumb,
    INCOMPLETE_DEPENDENCIES_MODE
  )
  private val CLOSE: VarargEventId = registerFileTypeEvent("close", IS_WRITABLE)

  private val CREATE_BY_NEW_FILE: VarargEventId = registerFileTypeEvent("create_by_new_file")
  private val CREATE_WITH_FILE_TEMPLATE: VarargEventId = registerFileTypeEvent("create_with_template",
                                                                               FILE_TEMPLATE_FIELD, EventFields.PluginInfo)

  @RequiresEdt
  @JvmStatic
  fun triggerEdit(project: Project, file: VirtualFile) {
    val projectState = ReadAction.compute<List<EventPair<*>>, Throwable> {
      listOf(
        EventFields.Dumb.with(isDumb(project)),
        INCOMPLETE_DEPENDENCIES_MODE.with(project.service<IncompleteDependenciesService>().getState())
      )
    }

    EDIT.log(project, Consumer { pairs: MutableList<EventPair<*>> ->
      pairs.addAll(buildCommonEventPairs(project, file, false))
      addFileNamePattern(pairs, file)
      pairs.addAll(projectState)
    })
  }

  @RequiresEdt
  fun triggerSelect(project: Project, file: VirtualFile?) {
    if (file != null) {
      log(SELECT, project, file, false)
    }
    else {
      logEmptyFile()
    }
  }

  @JvmStatic
  fun logCreated(project: Project, file: VirtualFile) {
    log(CREATE_BY_NEW_FILE, project, file, false)
  }

  @JvmStatic
  fun logCreated(project: Project, file: VirtualFile, fileTemplate: FileTemplate) {
    CREATE_WITH_FILE_TEMPLATE.log(project, Consumer { pairs: MutableList<EventPair<*>> ->
      pairs.addAll(buildCommonEventPairs(project, file, false))
      pairs.add(FILE_TEMPLATE_FIELD.with(fileTemplate.getName()))
      if (fileTemplate is PluginBundledTemplate) {
        val pluginDescriptor = (fileTemplate as PluginBundledTemplate).getPluginDescriptor()
        pairs.add(EventFields.PluginInfo.with(getPluginInfoByDescriptor(pluginDescriptor)))
      }
    })

    if (fileTemplate is PluginBundledTemplate
        && !java.lang.Boolean.getBoolean("ide.skip.plugin.templates.registered.check")) {
      val internalTemplates = FileTemplateManager.getDefaultInstance().getInternalTemplates()
        .map { t -> t.getName() }
        .toSet()

      LOG.assertTrue(
        internalTemplates.contains(fileTemplate.name),
        "Unknown bundled file template: $fileTemplate, register it in plugin.xml via <internalFileTemplate name=\"${fileTemplate.name}\"/> tag"
      )
    }
  }

  fun logOpened(
    project: Project,
    file: VirtualFile,
    fileEditor: FileEditor?,
    timeToShow: Long,
    durationMs: Long,
    composite: FileEditorComposite,
  ) {
    val projectState = getProjectState(project)
    val fileEditorPairs = if (fileEditor != null) {
      listOf(
        FILE_EDITOR.with(fileEditor.javaClass),
        IS_PREVIEW_TAB.with(composite.isPreview)
      )
    }
    else {
      emptyList()
    }

    OPEN.log(project, Consumer { pairs: MutableList<EventPair<*>> ->
      pairs.addAll(buildCommonEventPairs(project, file, true))
      pairs.addAll(fileEditorPairs)
      pairs.add(EventFields.TimeToShowMs.with(timeToShow))
      if (durationMs != -1L) {
        pairs.add(EventFields.DurationMs.with(durationMs))
      }
      addFileNamePattern(pairs, file)
      pairs.addAll(projectState)
    })
  }

  private fun getProjectState(project: Project): List<EventPair<*>> {
    val state = project.service<ProjectStateObserver>().getState()
    return listOf(
      EventFields.Dumb.with(state.isDumb),
      INCOMPLETE_DEPENDENCIES_MODE.with(state.dependenciesState)
    )
  }

  fun triggerClosed(project: Project, file: VirtualFile) {
    log(CLOSE, project, file, true)
  }

  private fun log(eventId: VarargEventId, project: Project, file: VirtualFile, withWritable: Boolean) {
    eventId.log(project, Consumer { pairs: MutableList<EventPair<*>> ->
      pairs.addAll(buildCommonEventPairs(project, file, withWritable))
    })
  }

  private fun buildCommonEventPairs(
    project: Project,
    file: VirtualFile,
    withWritable: Boolean,
  ): List<EventPair<*>> {
    val fileType = file.fileType
    val data = listOf(
      EventFields.PluginInfoFromInstance.with(fileType),
      EventFields.FileType.with(fileType),
      EventFields.AnonymizedPath.with(file.getPath()),
      SCHEMA.with(findSchema(project, file))
    )

    if (!withWritable) return data

    return data + IS_WRITABLE.with(file.isWritable())
  }

  private fun addFileNamePattern(data: MutableList<in EventPair<*>>, file: VirtualFile) {
    val fileType = file.fileType
    val fileTypeManager = FileTypeManager.getInstance()
    if (fileTypeManager !is FileTypeManagerImpl) {
      return
    }
    val fileNameMatchers = fileTypeManager.getStandardMatchers(fileType)
    fileNameMatchers
      .firstOrNull { it.acceptsCharSequence(file.getName()) }
      ?.let {
        data.add(FILE_NAME_PATTERN_FIELD.with(it.getPresentableString()))
      }
  }

  private fun logEmptyFile() {
    SELECT.log(EventFields.AnonymizedPath.with(null))
  }

  @JvmStatic
  fun findSchema(
    project: Project,
    file: VirtualFile,
  ): String? {
    for (ext in FileTypeSchemaValidator.EP.extensionList) {
      val instance = ext.getInstance()
      if (ext.schema == null) {
        Logger.getInstance(FileTypeUsageCounterCollector::class.java)
          .warn("Extension " + ext.implementationClass + " should define a 'schema' attribute")
        continue
      }

      if (instance.describes(project, file)) {
        return if (getPluginInfo(instance.javaClass).isSafeToReport()) ext.schema else "third.party"
      }
    }
    return null
  }
}

/**
 * Usage statistics has the relaxed guarantees for data consistency, so we just have some observation of dumb/dependencies state.
 * At the same time, it lets us avoid getting the read lock on every change in files.
 */
@Service(Service.Level.PROJECT)
private class ProjectStateObserver(private val project: Project, coroutineScope: CoroutineScope) {
  private val flow: MutableStateFlow<ProjectState> = MutableStateFlow(ProjectState(true, DependenciesState.COMPLETE))

  init {
    val connection = project.messageBus.connect(coroutineScope)
    connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun enteredDumbMode() {
        updateState { state -> ProjectState(true, state.dependenciesState) }
      }

      override fun exitDumbMode() {
        updateState { state -> ProjectState(false, state.dependenciesState) }
      }
    })

    coroutineScope.launch {
      flow.value = ProjectState(
        isDumb(project),
        readActionBlocking { project.service<IncompleteDependenciesService>().getState() }
      )

      project.service<IncompleteDependenciesService>().stateFlow.collect {
        updateState { state -> ProjectState(state.isDumb, it) }
      }
    }
  }

  fun getState(): ProjectState = flow.value

  private fun updateState(updater: (ProjectState) -> ProjectState) {
    while (true) {
      val state = flow.value
      val newState = updater(state)
      if (flow.compareAndSet(state, newState)) {
        return
      }
    }
  }
}

private class ProjectState(
  val isDumb: Boolean,
  val dependenciesState: DependenciesState,
)