// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

/**
 * @author anna
 */
@Service(Service.Level.PROJECT)
public final class BytecodeViewerManager {
  private static final ExtensionPointName<ClassSearcher> CLASS_SEARCHER_EP = ExtensionPointName.create("ByteCodeViewer.classSearcher");

  public static BytecodeViewerManager getInstance(Project project) {
    return project.getService(BytecodeViewerManager.class);
  }

  public static byte[] loadClassFileBytes(PsiClass aClass) throws IOException {
    String jvmClassName = getJVMClassName(aClass);
    if (jvmClassName != null) {
      PsiClass fileClass = aClass;
      while (PsiUtil.isLocalOrAnonymousClass(fileClass)) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(fileClass, PsiClass.class);
        if (containingClass != null) {
          fileClass = containingClass;
        }
      }
      VirtualFile file = fileClass.getOriginalElement().getContainingFile().getVirtualFile();
      if (file != null) {
        ProjectFileIndex index = ProjectFileIndex.getInstance(aClass.getProject());
        if (FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) {
          // compiled class; looking for the right .class file (inner class 'A.B' is "contained" in 'A.class', but we need 'A$B.class')
          String classFileName = StringUtil.getShortName(jvmClassName) + ".class";
          if (index.isInLibraryClasses(file)) {
            VirtualFile classFile = file.getParent().findChild(classFileName);
            if (classFile != null) return classFile.contentsToByteArray(false);
          }
          else {
            File classFile = new File(file.getParent().getPath(), classFileName);
            if (classFile.isFile()) return FileUtil.loadFileBytes(classFile);
          }
        }
        else {
          // source code; looking for a .class file in compiler output
          Module module = index.getModuleForFile(file);
          if (module != null) {
            CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
            if (extension != null) {
              boolean inTests = index.isInTestSourceContent(file);
              VirtualFile classRoot = inTests ? extension.getCompilerOutputPathForTests() : extension.getCompilerOutputPath();
              if (classRoot != null) {
                String relativePath = jvmClassName.replace('.', '/') + ".class";
                File classFile = new File(classRoot.getPath(), relativePath);
                if (classFile.exists()) return FileUtil.loadFileBytes(classFile);
              }
            }
          }
        }
      }
    }

    return null;
  }

  private static String getJVMClassName(PsiClass aClass) {
    if (!(aClass instanceof PsiAnonymousClass)) {
      return ClassUtil.getJVMClassName(aClass);
    }

    PsiClass containingClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
    if (containingClass != null) {
      return getJVMClassName(containingClass) + JavaAnonymousClassesHelper.getName((PsiAnonymousClass)aClass);
    }

    return null;
  }

  public static @Nullable PsiClass getContainingClass(@NotNull PsiElement psiElement) {
    for (ClassSearcher searcher : CLASS_SEARCHER_EP.getExtensionList()) {
      PsiClass aClass = searcher.findClass(psiElement);
      if (aClass != null) {
        return aClass;
      }
    }

    PsiClass containingClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class, false);
    while (containingClass instanceof PsiTypeParameter) {
      containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
    }

    if (containingClass == null) {
      PsiFile containingFile = psiElement.getContainingFile();
      if (containingFile instanceof PsiClassOwner) {
        TextRange textRange = psiElement.getTextRange();
        PsiClass result = null;
        Queue<PsiClass> queue = new ArrayDeque<>(Arrays.asList(((PsiClassOwner)containingFile).getClasses()));
        while (!queue.isEmpty()) {
          PsiClass c = queue.remove();
          PsiElement navigationElement = c.getNavigationElement();
          TextRange classRange = navigationElement != null ? navigationElement.getTextRange() : null;
          if (classRange != null && classRange.contains(textRange)) {
            result = c;
            queue.clear();
            queue.addAll(Arrays.asList(c.getInnerClasses()));
          }
        }
        return result;
      }
      return null;
    }

    return containingClass;
  }
}
