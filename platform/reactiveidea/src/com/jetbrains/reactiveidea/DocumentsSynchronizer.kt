/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.reactiveidea

import com.intellij.ide.DataManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.ui.UIUtil
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime
import com.jetbrains.reactivemodel.util.get
import java.util.ArrayList

public class DocumentsSynchronizer(val project: Project, val serverEditorTracker: ServerEditorTracker) : ProjectComponent {
  val lifetime = Lifetime.create(Lifetime.Eternal)
  var tabHost: TabViewHost? = null
  var viewHost: ProjectViewHost? = null
  val startupManager = StartupManager.getInstance(project)
  val reactiveModels = VariableSignal(lifetime, "reactive models", ArrayList<ReactiveModel>())


  override fun getComponentName(): String = "DocumentsSynchronizer"

  private val messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect()


  private fun guessDataContext(contextHint: MapModel, model: MapModel): DataContext {
    if(contextHint.get("editor") == null) {
      return DataContext.EMPTY_CONTEXT;
    }
    val editor = (contextHint.get("editor") as ListModel)
        .map { (it as PrimitiveModel<*>).value }
        .drop(1)
        .reverse()
        .foldRight(Path(), { part, path -> path / part })
        .getIn(model)!!.meta.valAt("editor") as Editor
    val component = editor.getContentComponent()
    return DataManager.getInstance().getDataContext(component)
  }

  override fun initComponent() {
    UIUtil.invokeLaterIfNeeded {
      initTracker()

      val serverModel = serverModel(lifetime.lifetime, 12346)
      serverModel.registerHandler(lifetime.lifetime, "invoke-action") { args: MapModel, model ->
        val actionName = (args["name"] as PrimitiveModel<String>).value
        val contextHint = args["context"] as MapModel
        val anAction = ActionManager.getInstance().getAction(actionName)
        if (anAction != null) {
          val dataContext = guessDataContext(contextHint, model)
          anAction.actionPerformed(AnActionEvent.createFromDataContext("ide-frontend", Presentation(), dataContext))
        } else {
          println("can't find idea action $args")
        }
        model
      }

      serverModel.registerHandler(lifetime.lifetime, "open-file") {args: MapModel, model ->
        val path = (args["path"] as ListModel).map { (it as PrimitiveModel<*>).value }.drop(1).fold(Path(), { path, part -> path / part })
        val psiPtr  = path.getIn(model)!!.meta["psi"]
        if(psiPtr is SmartPsiElementPointer<*>) {
          FileEditorManager.getInstance(project).openFile(psiPtr.getVirtualFile(), false)
        }
        model
      }

      val models = ArrayList(reactiveModels.value);
      models.add(serverModel)
      reactiveModels.value = models;

      startupManager.runWhenProjectIsInitialized(Runnable {
        val projectView = ProjectView.getInstance(project)
        val viewPane = projectView.getProjectViewPaneById(ProjectViewPane.ID) as AbstractProjectViewPSIPane
        val treeStructure = viewPane.createStructure()
        viewHost = ProjectViewHost(project, projectView, lifetime.lifetime, serverModel, Path("project-view"), treeStructure, viewPane)
      })

      tabHost = TabViewHost(lifetime.lifetime, serverModel, Path("tab-view"))


      messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
          object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
              if(!lifetime.lifetime.isTerminated) {
                val editors = FileEditorManager.getInstance(project).getAllEditors(file)
                    .filter { it is TextEditor }
                    .map { (it as TextEditor).getEditor() }

                if (!editors.isEmpty()) {
                  val editor = editors.first()
                  tabHost!!.addEditor(editor, file)
                }
              }
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
              if(!lifetime.lifetime.isTerminated) {
                tabHost!!.removeEditor(file)
              }
            }

            override fun selectionChanged(event: FileEditorManagerEvent) {
            }
          })

    }
  }

  public fun initTracker() {
    var activeList: VariableSignal<List<Signal<Model?>>> =
        reaction(true, "models", reactiveModels) { list: List<ReactiveModel> ->
          list.map {
            it.subscribe(it.lifetime, Path("tab-view"))
          }
        }

    var signalList: VariableSignal<Signal<List<Model?>>> = reaction(true, "editor models", activeList) { list ->
      unlist(list)
    }

    var flattenList: VariableSignal<List<Model?>?> = flatten(signalList)

    var activeEditorModls = reaction(true, "tabs", flattenList) { list: List<Model?>? ->
      if (list != null) {
        list.map { model -> model?.meta?.valAt("host") as? TabViewHost }
            .filterNotNull()
            .map { host ->
              host.reactiveModel.subscribe(lifetime.lifetime, host.path / TabViewHost.editorsPath)
            };
      } else {
        emptyList()
      }
    }

    var activeEditors: VariableSignal<List<Editor>> = reaction(true, "editors", activeEditorModls) { tabs ->
      tabs.filter { it.value != null }
          .flatMap { editors ->
            val value = editors.value as MapModel
            value.values().filter {
              it as MapModel
              (it[EditorHost.activePath] as PrimitiveModel<*>).value as Boolean
            }.map {
              it!!.meta.valAt("editor") as Editor
            }
          }
    }

    serverEditorTracker.setActiveEditors(activeEditors)
  }

  private fun isClient(): Boolean = System.getProperty("com.jetbrains.reactiveidea.client") == "true"


  override fun disposeComponent() {
    lifetime.terminate()
  }

  override fun projectOpened() {

  }

  override fun projectClosed() {

  }

}
