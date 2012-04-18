/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.MarkupModelWindow;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentMap;

/**
 * Manages per-project markup models of documents.
 * 
 * @author yole
 */
public class DocumentMarkupModel {
  private static final Object lock = new Object();

  private static final Key<MarkupModelEx> MARKUP_MODEL_KEY = Key.create("DocumentMarkupModel.MarkupModel");
  private static final Key<ConcurrentMap<Project, MarkupModelImpl>> MARKUP_MODEL_MAP_KEY = Key.create("DocumentMarkupModel.MarkupModelMap");

  private DocumentMarkupModel() {
  }

  /**
   * Returns the markup model for the specified project. A document can have multiple markup
   * models for different projects if the file to which it corresponds belongs to multiple projects
   * opened in different IDEA frames at the same time.
   *
   * @param document the document for which the markup model is requested.
   * @param project the project for which the markup model is requested, or null if the default markup
   *                model is requested.
   * @return the markup model instance.
   * @see com.intellij.openapi.editor.Editor#getMarkupModel()
   */
  public static MarkupModel forDocument(@NotNull Document document, @Nullable Project project, boolean create) {
    if (document instanceof DocumentWindow) {
      final Document delegate = ((DocumentWindow)document).getDelegate();
      final MarkupModelEx baseMarkupModel = (MarkupModelEx)forDocument(delegate, project, true);
      return new MarkupModelWindow(baseMarkupModel, (DocumentWindow) document);
    }

    if (project == null) {
      MarkupModelEx markupModel = document.getUserData(MARKUP_MODEL_KEY);
      if (create && markupModel == null) {
        synchronized (lock) {
          markupModel = document.getUserData(MARKUP_MODEL_KEY);
          if (markupModel == null) {
            markupModel = new MarkupModelImpl((DocumentImpl)document);
            document.putUserData(MARKUP_MODEL_KEY, markupModel);
          }
        }
      }
      return markupModel;
    }

    final DocumentMarkupModelManager documentMarkupModelManager =
      project.isDisposed() ? null : DocumentMarkupModelManager.getInstance(project);
    if (documentMarkupModelManager == null || documentMarkupModelManager.isDisposed() || project.isDisposed()) {
      return new EmptyMarkupModel(document);
    }

    ConcurrentMap<Project, MarkupModelImpl> markupModelMap = getMarkupModelMap(document);

    MarkupModelImpl model = markupModelMap.get(project);
    if (create && model == null) {
      synchronized (lock) {
        model = markupModelMap.get(project);
        if (model == null) {
          model = new MarkupModelImpl((DocumentImpl)document);
          markupModelMap.put(project, model);
          documentMarkupModelManager.registerDocument((DocumentImpl)document);
        }
      }
    }

    return model;
  }

  private static ConcurrentMap<Project, MarkupModelImpl> getMarkupModelMap(@NotNull Document document) {
    ConcurrentMap<Project, MarkupModelImpl> markupModelMap = document.getUserData(MARKUP_MODEL_MAP_KEY);
    if (markupModelMap == null) {
      synchronized (lock) {
        markupModelMap = document.getUserData(MARKUP_MODEL_MAP_KEY);
        if (markupModelMap == null) {
          markupModelMap = new ConcurrentHashMap<Project, MarkupModelImpl>();
          document.putUserData(MARKUP_MODEL_MAP_KEY, markupModelMap);
        }
        
      }
    }
    return markupModelMap;
  }

  static void removeMarkupModel(@NotNull Document document, @NotNull Project project) {
    MarkupModelImpl removed = getMarkupModelMap(document).remove(project);
    if (removed != null) {
      removed.dispose();
    }
  }
}
