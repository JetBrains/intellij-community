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
package com.intellij.psi.presentation.java;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class SymbolPresentationUtil {
  private SymbolPresentationUtil() {
  }

  public static String getSymbolPresentableText(@NotNull PsiElement element) {
    if (element instanceof NavigationItem) {
      final ItemPresentation presentation = ((NavigationItem)element).getPresentation();
      if (presentation != null){
        return presentation.getPresentableText();
      }
    }

    if (element instanceof PsiNamedElement) return ((PsiNamedElement)element).getName();
    return element.getText();
  }

  @Nullable
  public static String getSymbolContainerText(PsiElement element) {
    if (element instanceof NavigationItem) {
      final ItemPresentation presentation = ((NavigationItem)element).getPresentation();
      if (presentation != null){
        return presentation.getLocationString();
      } else {
        PsiFile file = element.getContainingFile();
        if (file != null) {
          VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null) return virtualFile.getPath();
        }
      }
    }

    return null;
  }

  public static String getFilePathPresentation(PsiFile psiFile) {
    ProjectFileIndex index = ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex();
    VirtualFile file = psiFile.getOriginalFile().getVirtualFile();
    VirtualFile rootForFile = file != null ? index.getContentRootForFile(file):null;

    if (rootForFile != null) {
      String relativePath = VfsUtilCore.getRelativePath(file, rootForFile, File.separatorChar);
      if (relativePath != null) return relativePath;
    }

    return psiFile.getName();
  }
}