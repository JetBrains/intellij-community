package com.intellij.remoteServer.ir.configuration

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.remoteServer.ir.configuration.BaseExtendableConfiguration.Companion.getTypeImpl
import com.intellij.util.text.UniqueNameGenerator
import javax.swing.Icon
import javax.swing.JComponent

class RemoteTargetsMasterDetails @JvmOverloads constructor(private val project: Project, private val initialSelectedName: String? = null)
  : MasterDetailsComponent() {

  init {
    // note that `MasterDetailsComponent` does not work without `initTree()`
    initTree()
  }

  override fun getDisplayName(): String = "Remote Targets"

  override fun getEmptySelectionString(): String? {
    return "To add new target, click +"
  }

  override fun reset() {
    myRoot.removeAllChildren()

    allTargets().forEach { nextTarget -> addTargetNode(nextTarget) }

    super.reset()

    initialSelectedName?.let { selectNodeInTree(initialSelectedName) }
  }

  override fun createActions(fromPopup: Boolean): List<AnAction> =
    RemoteTargetType.EXTENSION_NAME.extensionList.map { CreateNewTargetAction(it) } + MyDeleteAction()

  override fun processRemovedItems() {
    val deletedTargets = allTargets().toSet() - getConfiguredTargets()
    deletedTargets.forEach { RemoteTargetsManager.instance.removeConfig(it) }
    super.processRemovedItems()
  }

  override fun apply() {
    super.apply()

    val addedConfigs = getConfiguredTargets() - RemoteTargetsManager.instance.resolvedConfigs()
    addedConfigs.forEach { RemoteTargetsManager.instance.addConfig(it) }
  }

  private fun allTargets() = RemoteTargetsManager.instance.resolvedConfigs()

  private fun addTargetNode(config: RemoteTargetConfiguration): MyNode {
    val configurable = TargetDetailsConfigurable(project, config)
    val result = MyNode(configurable)
    addNode(result, myRoot)
    return myRoot
  }

  private fun getConfiguredTargets(): List<RemoteTargetConfiguration> =
    myRoot.children().asSequence()
      .map { node -> (node as MyNode).configurable?.editableObject as? RemoteTargetConfiguration }
      .filterNotNull()
      .toList()

  inner class CreateNewTargetAction(private val type: RemoteTargetType<*>)
    : DumbAwareAction(type.displayName, null, type.icon) {

    override fun actionPerformed(e: AnActionEvent) {
      val newConfig = type.createDefaultConfig()
      newConfig.displayName = UniqueNameGenerator.generateUniqueName(type.displayName) { curName ->
        getConfiguredTargets().none { it.displayName == curName }
      }

      val newNode = addTargetNode(newConfig)
      selectNodeInTree(newNode)
    }
  }

  private class TargetDetailsConfigurable(project: Project, private val config: RemoteTargetConfiguration)
    : NamedConfigurable<RemoteTargetConfiguration>(true, null) {

    private val targetConfigurable: Configurable = config.getTypeImpl().createConfigurable(project, config)

    override fun getBannerSlogan(): String = config.displayName

    override fun getIcon(expanded: Boolean): Icon? = config.getTypeImpl().icon

    override fun isModified(): Boolean = targetConfigurable.isModified

    override fun getDisplayName(): String = config.displayName

    override fun apply() = targetConfigurable.apply()

    override fun setDisplayName(name: String) {
      config.displayName = name
    }

    override fun disposeUIResources() {
      super.disposeUIResources()
      targetConfigurable.disposeUIResources()
    }

    override fun getEditableObject() = config

    override fun createOptionsPanel(): JComponent = targetConfigurable.createComponent() ?: throw IllegalStateException()
  }
}