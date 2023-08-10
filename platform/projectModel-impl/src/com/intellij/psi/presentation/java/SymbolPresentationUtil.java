// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.presentation.java;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class SymbolPresentationUtil {
  private SymbolPresentationUtil() {
  }

  public static @NlsSafe String getSymbolPresentableText(@NotNull PsiElement element) {
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
  public static @NlsSafe String getSymbolContainerText(PsiElement element) {
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

  public static @NlsSafe String getFilePathPresentation(PsiFile psiFile) {
    return getFilePathPresentation((PsiFileSystemItem)psiFile);
  }

  public static @NlsSafe String getFilePathPresentation(PsiFileSystemItem item) {
    ProjectFileIndex index = ProjectRootManager.getInstance(item.getProject()).getFileIndex();
    VirtualFile file = (item instanceof PsiFile ? ((PsiFile)item).getOriginalFile() : item).getVirtualFile();
    VirtualFile rootForFile = file != null ? index.getContentRootForFile(file):null;

    if (rootForFile != null) {
      String relativePath = VfsUtilCore.getRelativePath(file, rootForFile, File.separatorChar);
      if (relativePath != null) return relativePath;
    }

    return item.getName();
  }
}
