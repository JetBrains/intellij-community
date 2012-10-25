/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jdom.Element;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public final class AndroidDesignerEditorProvider implements FileEditorProvider, DumbAware {
  public static boolean acceptLayout(final @NotNull Project project, final @NotNull VirtualFile file) {
    PsiFile psiFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        return file.isValid() ? PsiManager.getInstance(project).findFile(file) : null;
      }
    });
    return psiFile instanceof XmlFile &&
           AndroidFacet.getInstance(psiFile) != null &&
           LayoutDomFileDescription.isLayoutFile((XmlFile)psiFile);
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return acceptLayout(project, file);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new AndroidDesignerEditor(project, file);
  }

  @Override
  public void disposeEditor(@NotNull FileEditor editor) {
    Disposer.dispose(editor);
  }

  @NotNull
  @Override
  public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    // TODO: Auto-generated method stub
    return new FileEditorState() {
      @Override
      public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
        return false;
      }
    };
  }

  @Override
  public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
    // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return "android-designer";
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
  }
}