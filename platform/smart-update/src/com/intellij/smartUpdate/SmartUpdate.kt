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
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.update.CommonUpdateProjectAction
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.ide.UpdateActionsListener
import java.util.*

@State(name = "SmartUpdateOptions", storages = [Storage(value = StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)])
@Service(Service.Level.PROJECT)
class SmartUpdate(val project: Project) : PersistentStateComponent<SmartUpdate.Options>, Disposable {

  class Options {
    @get:Attribute
    var updateIde = true
    @get:Attribute
    var restartIde = true
    @get:Attribute
    var updateProject = true
    @get:Attribute
    var buildProject = true
  }

  var restartRequested: Boolean = false
  private val options = Options()
  private val allSteps = listOf(IdeUpdateStep(), IdeRestartStep(), VcsUpdateStep(), BuildStep())

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
    val steps = LinkedList(allSteps.filter { it.isRequested(options) })
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
      e.presentation.isVisible = false
      return
    }
    CommonUpdateProjectAction().update(e)
    e.presentation.text = templateText
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

interface SmartUpdateStep {
  fun performUpdateStep(project: Project, e: AnActionEvent? = null, onSuccess: () -> Unit)
  fun isRequested(options: SmartUpdate.Options): Boolean
  fun isAvailable(): Boolean = true
}

const val IDE_RESTARTED_KEY = "smart.update.ide.restarted"

class IdeRestartedActivity: StartupActivity {
  override fun runActivity(project: Project) {
    if (PropertiesComponent.getInstance().isTrueValue(IDE_RESTARTED_KEY)) {
      PropertiesComponent.getInstance().setValue(IDE_RESTARTED_KEY, false)
      project.service<SmartUpdate>().execute(project)
    }
  }
}