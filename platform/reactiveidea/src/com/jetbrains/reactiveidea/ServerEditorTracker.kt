package com.jetbrains.reactiveidea

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
import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.codeInsight.daemon.impl.EditorTrackerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl
import com.intellij.util.EventDispatcher
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.host
import org.jetbrains.annotations.NonNls
import java.util.HashMap

public class ServerEditorTracker(project: Project,
                                 val editorFactory: EditorFactory,
                                 spmanager: SmartPointerManager,
                                 modelsManager: ReactiveModelsManager) : AbstractProjectComponent(project), EditorTracker {
  private val smartPointerManager = spmanager as SmartPointerManagerImpl
  private val dispatcher = EventDispatcher.create(javaClass<EditorTrackerListener>())

  private val activeEditors: Signal<List<Editor>> =
      reaction(true, "filter active", reaction(true, "flatmap", flatten(reaction(true, "editors", modelsManager.modelsForProject(project)) { models ->
        unlist(models.map {
          it.subscribe(it.lifetime, com.jetbrains.reactivemodel.editorsTag)
        })
      })) {
        it?.flatten()
      }) {
        (if (it != null) {
          it.filter {
            val isActive = (it[EditorHost.activePath] as PrimitiveModel<*>?)?.value
            if (isActive == null) false
            else isActive as Boolean
          }
        } else emptyList()).map { it.meta.host<EditorHost>().editor }
      }

  init {
    reaction(false, "active editor changed", activeEditors) {
      dispatchChanged()
    }
  }

  public override fun projectOpened() {
    val myEditorFactoryListener = MyEditorFactoryListener()
    editorFactory.addEditorFactoryListener(myEditorFactoryListener, myProject)
    Disposer.register(myProject, object : Disposable {
      public override fun dispose() {
        myEditorFactoryListener.executeOnRelease(null)
      }
    })
  }

  private fun dispatchChanged() {
    dispatcher.getMulticaster().activeEditorsChanged(getActiveEditors())
  }

  public override fun getActiveEditors(): List<Editor> {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return activeEditors.value
  }

  public override fun addEditorTrackerListener(listener: EditorTrackerListener, parentDisposable: Disposable) {
    dispatcher.addListener(listener, parentDisposable)
  }

  NonNls
  public override fun getComponentName(): String {
    return "EditorTracker"
  }

  private inner class MyEditorFactoryListener : EditorFactoryListener {
    private val myExecuteOnEditorRelease = HashMap<Editor, Runnable>()
    public override fun editorCreated(event: EditorFactoryEvent) {
      val editor = event.getEditor()
      if (editor.getProject() != null && editor.getProject() != myProject) return
      val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument())
      if (psiFile == null) return
      val virtualFile = psiFile.getVirtualFile()
      myExecuteOnEditorRelease.put(event.getEditor(), object : Runnable {
        override fun run() {
          // allow range markers in smart pointers to be collected
          if (virtualFile != null) {
            smartPointerManager.unfastenBelts(virtualFile, 0)
          }
        }
      })
      // materialize all range markers and do not let them to be collected to improve responsiveness
      if (virtualFile != null) {
        smartPointerManager.fastenBelts(virtualFile, 0, null)
      }
    }

    public override fun editorReleased(event: EditorFactoryEvent) {
      val editor = event.getEditor()
      if (editor.getProject() != null && editor.getProject() != myProject) return
      executeOnRelease(editor)
    }

    fun executeOnRelease(editor: Editor?) {
      if (editor == null) {
        for (r in myExecuteOnEditorRelease.values()) {
          r.run()
        }
        myExecuteOnEditorRelease.clear()
      } else {
        val runnable = myExecuteOnEditorRelease.get(editor)
        if (runnable != null) {
          runnable.run()
          myExecuteOnEditorRelease.remove(editor)
        }
      }
    }
  }
}