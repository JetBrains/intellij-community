package com.jetbrains.reactiveidea;/*
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

import com.intellij.codeInsight.daemon.impl.EditorTracker;
import com.intellij.codeInsight.daemon.impl.EditorTrackerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.util.EventDispatcher;
import com.jetbrains.reactivemodel.ReactivemodelPackage;
import com.jetbrains.reactivemodel.VariableSignal;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerEditorTracker extends AbstractProjectComponent implements EditorTracker {

  private final EditorFactory myEditorFactory;
  private final SmartPointerManagerImpl mySmartPointerManager;
  private final EventDispatcher<EditorTrackerListener> myDispatcher = EventDispatcher.create(EditorTrackerListener.class);
  private volatile VariableSignal<List<Editor>> myActiveEditors = null;

  public ServerEditorTracker(Project project, final EditorFactory editorFactory, SmartPointerManager manager) {
    super(project);
    myEditorFactory = editorFactory;
    mySmartPointerManager = (SmartPointerManagerImpl)manager;
  }

  @Override
  public void projectOpened() {
    final MyEditorFactoryListener myEditorFactoryListener = new MyEditorFactoryListener();
    myEditorFactory.addEditorFactoryListener(myEditorFactoryListener, myProject);
    Disposer.register(myProject, new Disposable() {
      @Override
      public void dispose() {
        myEditorFactoryListener.executeOnRelease(null);
      }
    });
  }

  private void dispatchChanged() {
    myDispatcher.getMulticaster().activeEditorsChanged(getActiveEditors());
  }

  @NotNull
  @Override
  public List<Editor> getActiveEditors() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert myActiveEditors.getValue() != null;
    return myActiveEditors == null ? Collections.<Editor>emptyList() : myActiveEditors.getValue();
  }

  @Override
  public void addEditorTrackerListener(@NotNull EditorTrackerListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  public void setActiveEditors(VariableSignal<List<Editor>> activeEditors) {
    myActiveEditors = activeEditors;
    ReactivemodelPackage.reaction(false, "active editor changed", activeEditors, new Function1<List<Editor>, Object>() {
      @Override
      public Object invoke(List<Editor> editors) {
        dispatchChanged();
        return null;
      }
    });
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "EditorTracker";
  }

  private class MyEditorFactoryListener implements EditorFactoryListener {
    private final Map<Editor, Runnable> myExecuteOnEditorRelease = new HashMap<Editor, Runnable>();

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
      final Editor editor = event.getEditor();
      if (editor.getProject() != null && editor.getProject() != myProject) return;
      final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (psiFile == null) return;

      final VirtualFile virtualFile = psiFile.getVirtualFile();
      myExecuteOnEditorRelease.put(event.getEditor(), new Runnable() {
        @Override
        public void run() {
          // allow range markers in smart pointers to be collected
          if (virtualFile != null) {
            mySmartPointerManager.unfastenBelts(virtualFile, 0);
          }
        }
      });
      // materialize all range markers and do not let them to be collected to improve responsiveness
      if (virtualFile != null) {
        mySmartPointerManager.fastenBelts(virtualFile, 0, null);
      }
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
      final Editor editor = event.getEditor();
      if (editor.getProject() != null && editor.getProject() != myProject) return;
      executeOnRelease(editor);
    }

    private void executeOnRelease(Editor editor) {
      if (editor == null) {
        for (Runnable r : myExecuteOnEditorRelease.values()) {
          r.run();
        }
        myExecuteOnEditorRelease.clear();
      }
      else {
        final Runnable runnable = myExecuteOnEditorRelease.get(editor);
        if (runnable != null) {
          runnable.run();
          myExecuteOnEditorRelease.remove(editor);
        }
      }
    }
  }
}
