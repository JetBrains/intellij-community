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
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import com.jetbrains.reactivemodel.Path
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime

public class DocumentsSynchronizer(val project: Project) : ProjectComponent {
  val lifetime = Lifetime.create(Lifetime.Eternal)
  //  var bJavaHost: EditorHost? = null
  var tabHost: TabViewHost? = null
  var viewHost: ProjectViewHost? = null
  val startupManager = StartupManager.getInstance(project)


  override fun getComponentName(): String = "DocumentsSynchronizer"

  private val messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect()


  private fun guessDataContext(contextHint: MapModel): DataContext? {
    //todo: look at the hint!
    val editorContext = contextHint.get("editor") as ListModel
    val editorIdx = (editorContext.get(3) as PrimitiveModel<*>).value
    val editor = tabHost!!.getEditor(Integer.valueOf(editorIdx as String)).editor
    val component = editor.getContentComponent()
    return DataManager.getInstance().getDataContext(component)
  }

  override fun initComponent() {

    UIUtil.invokeLaterIfNeeded {
      val serverModel = serverModel(lifetime.lifetime, 12346)
      serverModel.registerHandler(lifetime.lifetime, "invoke-action") { args: MapModel ->
        val actionName = (args["name"] as PrimitiveModel<String>).value
        val contextHint = args["context"] as MapModel
        val anAction = ActionManager.getInstance().getAction(actionName)
        if (anAction != null) {
          val dataContext = guessDataContext(contextHint)
          anAction.actionPerformed(AnActionEvent.createFromDataContext("ide-frontend", Presentation(), dataContext))
        } else {
          println("can't find idea action $args")
        }
      }

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
              val editors = FileEditorManager.getInstance(project).getAllEditors(file)
                  .filter { it is TextEditor }
                  .map { (it as TextEditor).getEditor() }

              if (!editors.isEmpty()) {
                val editor = editors.first()
                tabHost!!.addEditor(editor, file)
              }
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
              tabHost!!.removeEditor(file)
            }

            override fun selectionChanged(event: FileEditorManagerEvent) {
            }
          })

    }
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
