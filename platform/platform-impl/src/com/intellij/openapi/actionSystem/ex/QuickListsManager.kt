// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex

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
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.Project
import java.util.function.BiConsumer
import java.util.function.Function

private var EP_NAME = ExtensionPointName<BundledQuickListsProvider>("com.intellij.bundledQuickListsProvider")

@Service
class QuickListsManager {
  val schemeManager: SchemeManager<QuickList> = SchemeManagerFactory.getInstance()
    .create("quicklists", object : LazySchemeProcessor<QuickList, QuickList>(QuickList.DISPLAY_NAME_TAG) {
      override fun createScheme(dataHolder: SchemeDataHolder<QuickList>,
                                name: String,
                                attributeProvider: Function<in String, String?>,
                                isBundled: Boolean): QuickList {
        val item = QuickList()
        item.readExternal(dataHolder.read())
        dataHolder.updateDigest(item)
        return item
      }
    }, presentableName = IdeBundle.message("quick.lists.presentable.name"))

  init {
    EP_NAME.processWithPluginDescriptor(BiConsumer { provider, pluginDescriptor ->
      for (path in provider.bundledListsRelativePaths) {
        schemeManager.loadBundledScheme(if (path.endsWith(".xml")) path else "$path.xml", null, pluginDescriptor)
      }
    })
    schemeManager.loadSchemes()
  }

  internal class QuickListActionCustomizer : ActionConfigurationCustomizer {
    override fun customize(manager: ActionManager) {
      getInstance().registerActions(manager)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance() = service<QuickListsManager>()
  }

  val allQuickLists: Array<QuickList>
    get() {
      return schemeManager.allSchemes.toTypedArray()
    }

  private fun registerActions(actionManager: ActionManager) {
    // to prevent exception if 2 or more targets have the same name
    val registeredIds = HashSet<String>()
    for (scheme in schemeManager.allSchemes) {
      val actionId = scheme.actionId
      if (registeredIds.add(actionId)) {
        actionManager.registerAction(actionId, InvokeQuickListAction(scheme))
      }
    }
  }

  // used by external plugin
  fun setQuickLists(quickLists: List<QuickList>) {
    val actionManager = ActionManager.getInstance()
    for (oldId in actionManager.getActionIdList(QuickList.QUICK_LIST_PREFIX)) {
      actionManager.unregisterAction(oldId)
    }
    schemeManager.setSchemes(quickLists)
    registerActions(actionManager)
  }
}

private class InvokeQuickListAction(private val quickList: QuickList) : QuickSwitchSchemeAction() {
  init {
    myActionPlace = ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION
    templatePresentation.description = quickList.description
    templatePresentation.setText(quickList.name, false)
  }

  override fun fillActions(project: Project, group: DefaultActionGroup, dataContext: DataContext) {
    val actionManager = ActionManager.getInstance()
    for (actionId in quickList.actionIds) {
      if (QuickList.SEPARATOR_ID == actionId) {
        group.addSeparator()
      }
      else {
        val action = actionManager.getAction(actionId)
        if (action != null) {
          group.add(action)
        }
      }
    }
  }
}