/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.actionSystem.ex

import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.QuickSwitchSchemeAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.BundledQuickListsProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ExportableApplicationComponent
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.Project
import gnu.trove.THashSet
import java.util.function.Function

class QuickListsManager(private val myActionManager: ActionManager, schemeManagerFactory: SchemeManagerFactory) : ExportableApplicationComponent {
  private val mySchemeManager: SchemeManager<QuickList>

  init {
    mySchemeManager = schemeManagerFactory.create("quicklists",
        object : LazySchemeProcessor<QuickList, QuickList>(QuickList.DISPLAY_NAME_TAG) {
          override fun createScheme(dataHolder: SchemeDataHolder<QuickList>,
                                    name: String,
                                    attributeProvider: Function<String, String?>,
                                    isBundled: Boolean): QuickList {
            val item = QuickList()
            item.readExternal(dataHolder.read())
            dataHolder.updateDigest(item)
            return item
          }
        })
  }

  companion object {
    @JvmStatic
    val instance: QuickListsManager
      get() = ApplicationManager.getApplication().getComponent(QuickListsManager::class.java)
  }

  override fun getExportFiles() = arrayOf(mySchemeManager.rootDirectory)

  override fun getPresentableName() = IdeBundle.message("quick.lists.presentable.name")!!

  override fun getComponentName() = "QuickListsManager"

  override fun initComponent() {
    for (provider in BundledQuickListsProvider.EP_NAME.extensions) {
      for (path in provider.bundledListsRelativePaths) {
        mySchemeManager.loadBundledScheme(path, provider)
      }
    }
    mySchemeManager.loadSchemes()
    registerActions()
  }

  override fun disposeComponent() {
  }

  val schemeManager: SchemeManager<QuickList>
    get() = mySchemeManager

  val allQuickLists: Array<QuickList>
    get() {
      return mySchemeManager.allSchemes.toTypedArray()
    }

  private fun registerActions() {
    // to prevent exception if 2 or more targets have the same name
    val registeredIds = THashSet<String>()
    for (scheme in mySchemeManager.allSchemes) {
      val actionId = scheme.actionId
      if (registeredIds.add(actionId)) {
        myActionManager.registerAction(actionId, InvokeQuickListAction(scheme))
      }
    }
  }

  private fun unregisterActions() {
    for (oldId in myActionManager.getActionIds(QuickList.QUICK_LIST_PREFIX)) {
      myActionManager.unregisterAction(oldId)
    }
  }

  // used by external plugin
  fun setQuickLists(quickLists: List<QuickList>) {
    unregisterActions()
    mySchemeManager.setSchemes(quickLists)
    registerActions()
  }
}

private class InvokeQuickListAction(private val myQuickList: QuickList) : QuickSwitchSchemeAction() {
  init {
    myActionPlace = ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION
    templatePresentation.description = myQuickList.description
    templatePresentation.setText(myQuickList.name, false)
  }

  override fun fillActions(project: Project, group: DefaultActionGroup, dataContext: DataContext) {
    val actionManager = ActionManager.getInstance()
    for (actionId in myQuickList.actionIds) {
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
