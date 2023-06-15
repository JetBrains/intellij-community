package com.intellij.smartUpdate

import com.intellij.ide.actions.SettingsEntryPointAction.UpdateAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.update.CommonUpdateProjectAction
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.MapAnnotation
import org.jetbrains.ide.UpdateActionsListener
import java.util.*

@State(name = "SmartUpdateOptions", storages = [Storage(value = StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)])
@Service(Service.Level.PROJECT)
class SmartUpdate(val project: Project) : PersistentStateComponent<SmartUpdate.Options>, Disposable {

  class Options: BaseState() {
    @get:MapAnnotation(surroundWithTag = false)
    var map: MutableMap<String, Boolean> by linkedMap()
    fun value(id: String) = map[id] ?: true
    fun property(id: String) = MutableProperty({ value(id) }, { map[id] = it })
  }

  var restartRequested: Boolean = false
  private val options = Options()
  private val allSteps = listOf(IdeUpdateStep(), IdeRestartStep(), VcsUpdateStep(), BuildProjectStep())

  init {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(UpdateActionsListener.TOPIC, object : UpdateActionsListener {
      override fun actionReceived(action: UpdateAction) {
        if (restartRequested && action.isRestartRequired) {
          restartRequested = false
          val event = AnActionEvent.createFromDataContext("", null, SimpleDataContext.getProjectContext(project))
          IdeRestartStep().performUpdateStep(project, event) {}
        }
      }
    })
  }

  override fun getState() = options

  override fun loadState(state: Options) {
    XmlSerializerUtil.copyBean(state, options)
  }

  fun execute(project: Project, e: AnActionEvent? = null) {
    val steps = LinkedList(allSteps.filter { options.value(it.id) })
    executeNext(steps, project, e)
  }

  private fun executeNext(steps: Queue<SmartUpdateStep>, project: Project, e: AnActionEvent?) {
    steps.poll()?.performUpdateStep(project, e) { executeNext(steps, project, e) }
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
  }

  override fun update(e: AnActionEvent) {
    if (!Registry.`is`("ide.smart.update", false)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    CommonUpdateProjectAction().update(e)
    e.presentation.text = templateText
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

const val IDE_RESTARTED_KEY = "smart.update.ide.restarted"

class IdeRestartedActivity: ProjectActivity {
  override suspend fun execute(project: Project) {
    if (PropertiesComponent.getInstance().isTrueValue(IDE_RESTARTED_KEY)) {
      PropertiesComponent.getInstance().setValue(IDE_RESTARTED_KEY, false)
      project.service<SmartUpdate>().execute(project)
    }
  }
}