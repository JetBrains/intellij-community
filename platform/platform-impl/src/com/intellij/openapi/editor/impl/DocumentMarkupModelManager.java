/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author max
 */
public class DocumentMarkupModelManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.DocumentMarkupModelManager");

  private final WeakHashMap<DocumentImpl,String> myDocumentSet = new WeakHashMap<DocumentImpl, String>();
  private final Project myProject;
  private boolean myIsDisposed = false;

  public static DocumentMarkupModelManager getInstance(Project project) {
    return project.getComponent(DocumentMarkupModelManager.class);
  }

  public DocumentMarkupModelManager(Project project) {
    myProject = project;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
    cleanup();
  }

  public void registerDocument(DocumentImpl doc) {
    LOG.assertTrue(!myIsDisposed);
    myDocumentSet.put(doc, "");
  }

  @NotNull
  public String getComponentName() {
    return "DocumentMarkupModelManager";
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

  public void initComponent() { }

  public void disposeComponent() {
    cleanup();
  }

  private void cleanup() {
    if (!myIsDisposed) {
      myIsDisposed = true;
      Set<DocumentImpl> docs = myDocumentSet.keySet();
      for (DocumentImpl doc : docs) {
        doc.removeMarkupModel(myProject);
      }
    }
  }
}
