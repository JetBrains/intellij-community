// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.target

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.remoteServer.ir.config.BaseExtendableConfiguration
import com.intellij.remoteServer.ir.config.BaseExtendableConfiguration.Companion.getTypeImpl
import com.intellij.remoteServer.ir.runtime.sample.SampleLanguageRuntimeConfiguration
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.util.ui.UIUtil
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

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
    deletedTargets.forEach { RemoteTargetsManager.instance.targets.removeConfig(it) }
    super.processRemovedItems()
  }

  override fun apply() {
    super.apply()

    val addedConfigs = getConfiguredTargets() - RemoteTargetsManager.instance.targets.resolvedConfigs()

    //FIXME: temporary hack to have some runtimes in UI
    addedConfigs.forEach {
      if (it.displayName.contains("-sample") && it.runtimes.resolvedConfigs().isEmpty()) {
        it.runtimes.addConfig(SampleLanguageRuntimeConfiguration().also {
          it.homePath = "my home"
          it.applicationFolder = "my app"
        })
      }
    }

    addedConfigs.forEach { RemoteTargetsManager.instance.targets.addConfig(it) }
  }

  private fun allTargets() = RemoteTargetsManager.instance.targets.resolvedConfigs()

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

  private class TargetDetailsConfigurable(private val project: Project, private val config: RemoteTargetConfiguration)
    : NamedConfigurable<RemoteTargetConfiguration>(true, null) {

    private val targetConfigurable: Configurable = doCreateConfigurable(config)

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

    override fun createOptionsPanel(): JComponent {
      val panel = JPanel(VerticalLayout(JBUIScale.scale(UIUtil.DEFAULT_VGAP)))
      panel.add(targetConfigurable.createComponent() ?: throw IllegalStateException())

      config.runtimes.resolvedConfigs().forEach {
        val nextConfigurable = doCreateConfigurable(it)
        panel.add(nextConfigurable.createComponent())
      }

      return panel
    }

    private fun doCreateConfigurable(config: BaseExtendableConfiguration) =
      config.getTypeImpl().createConfigurable(project, config)

  }
}