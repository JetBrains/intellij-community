// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.BaseProjectDirectories;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class FqnUtil {
  public static @Nullable String getQualifiedNameFromProviders(@Nullable PsiElement element) {
    if (element == null) return null;
    return DumbService.getInstance(element.getProject()).computeWithAlternativeResolveEnabled(() ->
                                                                                                QualifiedNameProviderUtil.getQualifiedName(element));
  }

  public static @Nullable String elementToFqn(final @Nullable PsiElement element, @Nullable Editor editor) {
    String result = getQualifiedNameFromProviders(element);
    if (result != null) return result;

    if (editor != null) { //IDEA-70346
      PsiReference reference = TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset());
      if (reference != null) {
        result = getQualifiedNameFromProviders(reference.resolve());
        if (result != null) return result;
      }
    }

    if (element instanceof PsiFile) {
      return FileUtil.toSystemIndependentName(getFileFqn((PsiFile)element));
    }
    if (element instanceof PsiDirectory) {
      return FileUtil.toSystemIndependentName(getVirtualFileFqn(((PsiDirectory)element).getVirtualFile(), element.getProject()));
    }

    return null;
  }

  public static @NotNull @NlsSafe String getFileFqn(final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile == null ? file.getName() : getVirtualFileFqn(virtualFile, file.getProject());
  }

  public static @NotNull String getVirtualFileFqn(@NotNull VirtualFile virtualFile, @NotNull Project project) {
    for (VirtualFileQualifiedNameProvider provider : VirtualFileQualifiedNameProvider.EP_NAME.getExtensionList()) {
      String qualifiedName = provider.getQualifiedName(project, virtualFile);
      if (qualifiedName != null) {
        return qualifiedName;
      }
    }

    VirtualFile baseDirectory = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(virtualFile);
    if (baseDirectory != null) {
      String relativePath = VfsUtilCore.getRelativePath(virtualFile, baseDirectory);
      if (relativePath != null) {
        return relativePath;
      }
    }

    RootType rootType = RootType.forFile(virtualFile);
    if (rootType != null) {
      VirtualFile scratchRootVirtualFile =
        VfsUtil.findFileByIoFile(new File(ScratchFileService.getInstance().getRootPath(rootType)), false);
      if (scratchRootVirtualFile != null) {
        String scratchRelativePath = VfsUtilCore.getRelativePath(virtualFile, scratchRootVirtualFile);
        if (scratchRelativePath != null) {
          return scratchRelativePath;
        }
      }
    }

    return virtualFile.getPath();
  }
}
