package com.intellij.smartUpdate

import com.intellij.ide.actions.SettingsEntryPointAction.UpdateAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.MapAnnotation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.jetbrains.ide.UpdateActionsListener
import java.time.Duration
import java.time.LocalTime
import java.util.*

@State(name = "SmartUpdateOptions", storages = [Storage(value = StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)])
@Service(Service.Level.PROJECT)
class SmartUpdate(val project: Project, private val coroutineScope: CoroutineScope) : PersistentStateComponent<SmartUpdate.Options>, Disposable {

  class Options: BaseState() {
    var scheduled by property(false)
    var scheduledTime by property(LocalTime.of(6, 0).toSecondOfDay())

    @get:MapAnnotation(surroundWithTag = false)
    var map: MutableMap<String, Boolean> by linkedMap()
    fun value(id: String) = map[id] ?: true
    fun property(id: String) = MutableProperty({ value(id) }, { map[id] = it })
  }

  var restartRequested = false
  private var updateScheduled: Deferred<*>? = null
  private val options = Options()

  init {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(UpdateActionsListener.TOPIC, object : UpdateActionsListener {
      override fun actionReceived(action: UpdateAction) {
        if (restartRequested && action.isRestartRequired) {
          restartRequested = false
          restartIde(project, action)
        }
      }
    })
  }

  override fun getState() = options

  override fun loadState(state: Options) {
    XmlSerializerUtil.copyBean(state, options)
  }

  fun availableSteps(): List<SmartUpdateStep> = EP_NAME.extensionList.filter { it.isAvailable(project) }

  fun execute(project: Project, e: AnActionEvent? = null) {
    executeNext(LinkedList(availableSteps().filter { options.value(it.id) }), project, e)
    scheduleUpdate()
  }

  private fun executeNext(steps: Queue<SmartUpdateStep>, project: Project, e: AnActionEvent?) {
    steps.poll()?.performUpdateStep(project, e) { executeNext(steps, project, e) }
  }

  internal fun scheduleUpdate() {
    if (!options.scheduled) return
    updateScheduled?.cancel()
    updateScheduled = coroutineScope.async {
      var duration = Duration.between(LocalTime.now(), LocalTime.ofSecondOfDay(options.scheduledTime.toLong()))
      if (duration.isNegative) duration = duration.plusDays(1)
      delay(duration.toMillis())
      blockingContext { execute(project) }
    }
  }

  override fun dispose() {
  }
}

class SmartUpdateAction: DumbAwareAction(SmartUpdateBundle.message("action.smart.update.text")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = getEventProject(e)!!
    if (SmartUpdateDialog(project).showAndGet()) {
      project.service<SmartUpdate>().execute(project, e)
    }
    project.service<SmartUpdate>().scheduleUpdate()
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (!Registry.`is`("ide.smart.update", false) || project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (!ProjectLevelVcsManager.getInstance(project).hasActiveVcss()) {
      e.presentation.setEnabledAndVisible(false)
      return
    }
    e.presentation.text = templateText
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

const val IDE_RESTARTED_KEY = "smart.update.ide.restarted"

class IdeRestartedActivity: ProjectActivity {
  override suspend fun execute(project: Project) {
    val service = project.service<SmartUpdate>()
    if (PropertiesComponent.getInstance().isTrueValue(IDE_RESTARTED_KEY)) {
      PropertiesComponent.getInstance().setValue(IDE_RESTARTED_KEY, false)
      service.execute(project)
    }
    else service.scheduleUpdate()
  }
}