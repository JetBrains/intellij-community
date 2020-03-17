package com.intellij.configurationScript.inspection

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.configurationScript.ConfigurationFileManager
import com.intellij.configurationScript.findListNodeByPath
import com.intellij.configurationScript.processStringKeys
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode

internal class ExternallyConfigurableProjectInspectionProfileManager(project: Project) : ProjectInspectionProfileManager(project) {
  companion object {
    const val KEY = "inspections"
  }

  private val profileFromFile = SynchronizedClearableLazy {
    val mainNode = ConfigurationFileManager.getInstance(project).findValueNode(KEY) ?: return@SynchronizedClearableLazy null

    val toEnable = findListNodeByPath("enable", mainNode)
    val toDisable = findListNodeByPath("disable", mainNode)

    var disableAll = false
    mainNode.processStringKeys { key, value ->
      if (key == "disableAll" && value is ScalarNode) {
        disableAll = (value.value ?: return@processStringKeys).toBoolean()
      }
    }

    val profile = InspectionProfileImpl("intellij configuration file", InspectionToolRegistrar.getInstance(), this)
    if (disableAll) {
      for (entry in profile.getInspectionTools(null)) {
        profile.setToolEnabled(entry.shortName, false, project, fireEvents = false)
      }
      profile.disableAllTools(project)
    }

    toEnable?.let {
      updateTools(it, true, profile, project)
    }
    toDisable?.let {
      updateTools(it, false, profile, project)
    }

    profile
  }

  private fun updateTools(list: List<Node>, value: Boolean, profile: InspectionProfileImpl, project: Project) {
    for (node in list) {
      if (node is ScalarNode) {
        profile.setToolEnabled(node.value, value, project, fireEvents = false)
      }
    }
  }

  init {
    ConfigurationFileManager.getInstance(project).registerClearableLazyValue(profileFromFile)
  }

  override fun getProfiles(): Collection<InspectionProfileImpl> {
    val value = profileFromFile.value
    if (value != null) {
      return listOf(value)
    }
    return super.getProfiles()
  }

  override fun getCurrentProfile(): InspectionProfileImpl {
    val value = profileFromFile.value
    if (value != null) {
      return value
    }
    return super.getCurrentProfile()
  }
}