// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.DynamicBundle
import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.QuickSwitchSchemeAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.actionSystem.impl.BundledQuickListsProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.Project

private var EP_NAME = ExtensionPointName<BundledQuickListsProvider>("com.intellij.bundledQuickListsProvider")

@Service(Service.Level.APP)
class QuickListsManager {
  private val schemeProcessor = object : LazySchemeProcessor<QuickList, QuickList>(QuickList.DISPLAY_NAME_TAG) {
    override fun createScheme(dataHolder: SchemeDataHolder<QuickList>,
                              name: String,
                              attributeProvider: (String) -> String?,
                              isBundled: Boolean): QuickList {
      val item = QuickList()
      item.readExternal(dataHolder.read())
      dataHolder.updateDigest(item)
      return item
    }

    override fun reloaded(schemeManager: SchemeManager<QuickList>, schemes: Collection<QuickList>) {
      registerActions(ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar())
    }
  }

  val schemeManager: SchemeManager<QuickList> = SchemeManagerFactory.getInstance().create("quicklists", schemeProcessor,
                                                                                          presentableName = IdeBundle.message(
                                                                                            "quick.lists.presentable.name"),
                                                                                          settingsCategory = SettingsCategory.UI)

  init {
    EP_NAME.processWithPluginDescriptor { provider, pluginDescriptor ->
      for (path in provider.bundledListsRelativePaths) {
        schemeManager.loadBundledScheme(resourceName = if (path.endsWith(".xml")) path else "$path.xml", requestor = null,
                                        pluginDescriptor = pluginDescriptor)
          ?.localizeWithBundle(DynamicBundle.getPluginBundle(pluginDescriptor))
      }
    }
    schemeManager.loadSchemes()
  }

  internal class QuickListActionCustomizer : ActionConfigurationCustomizer, ActionConfigurationCustomizer.LightCustomizeStrategy {
    override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
      serviceAsync<QuickListsManager>().registerActions(actionRegistrar)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): QuickListsManager = service()
  }

  val allQuickLists: Array<QuickList>
    get() = schemeManager.allSchemes.toTypedArray()

  private fun registerActions(actionRegistrar: ActionRuntimeRegistrar) {
    actionRegistrar.unregisterActionByIdPrefix(QuickList.QUICK_LIST_PREFIX)

    // to prevent exception if 2 or more targets have the same name
    val registeredIds = HashSet<String>()
    for (scheme in schemeManager.allSchemes) {
      val actionId = scheme.actionId
      if (registeredIds.add(actionId)) {
        actionRegistrar.registerAction(actionId, InvokeQuickListAction(scheme))
      }
    }
  }

  // used by external plugin
  fun setQuickLists(quickLists: List<QuickList>) {
    val actionRegistrar = ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar()
    schemeManager.setSchemes(quickLists)
    registerActions(actionRegistrar)
  }
}

private class InvokeQuickListAction(private val quickList: QuickList) : QuickSwitchSchemeAction() {
  init {
    myActionPlace = ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION
    templatePresentation.description = quickList.description
    templatePresentation.setText(quickList.displayName, false)
  }

  override fun fillActions(project: Project, group: DefaultActionGroup, dataContext: DataContext) {
    val actionManager = ActionManager.getInstance()
    for (actionId in quickList.actionIds) {
      if (QuickList.SEPARATOR_ID == actionId) {
        group.addSeparator()
      }
      else {
        actionManager.getAction(actionId)?.let {
          group.add(it)
        }
      }
    }
  }
}