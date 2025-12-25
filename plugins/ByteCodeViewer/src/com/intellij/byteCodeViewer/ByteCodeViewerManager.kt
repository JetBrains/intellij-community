// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.resolveFromRootOrRelative
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.parentsOfType
import java.nio.file.Path
import java.util.*

public object ByteCodeViewerManager {
  private val CLASS_SEARCHER_EP = ExtensionPointName<ClassSearcher>("ByteCodeViewer.classSearcher")
  private val CLASS_FINDER_EP = ExtensionPointName<BytecodeViewerClassFileFinder>("ByteCodeViewer.classFileFinder")

  private fun PsiClass.containingClassFileClass(): PsiClass {
    return parentsOfType<PsiClass>(withSelf = true)
      .filterNot(PsiUtil::isLocalOrAnonymousClass)
      .first()
  }

  public fun findClassFile(psiClass: PsiClass): VirtualFile? {
    val fileClass = psiClass.containingClassFileClass()
    for (finder in CLASS_FINDER_EP.extensionList) {
      val vFile = finder.findClass(psiClass, fileClass)
      if (vFile != null) {
        return vFile
      }
    }
    val file = fileClass.originalElement.containingFile.virtualFile ?: return null
    val jvmClassName = ClassUtil.getBinaryClassName(psiClass) ?: return null
    if (FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) {
      // compiled class; looking for the right .class file (inner class 'A.B' is "contained" in 'A.class', but we need 'A$B.class')
      return file.parent.findChild(StringUtil.getShortName(jvmClassName) + ".class")
    }
    else {
      // source code; looking for a .class file in compiler output
      val fileIndex = ProjectFileIndex.getInstance(psiClass.project)
      val module = fileIndex.getModuleForFile(file) ?: return null
      for (compilerOutputPath in CompilerPaths.getOutputPaths(arrayOf(module))) {
        val classRoot = VirtualFileManager.getInstance().findFileByNioPath(Path.of(compilerOutputPath))?.let {
          val jarRoot = if (it.isDirectory) null else JarFileSystem.getInstance().getJarRootForLocalFile(it)
          jarRoot ?: it  // fallback to local
        } ?: continue
        val relativeClassFilePath = jvmClassName.replace('.', '/') + ".class"
        val classFile = classRoot.resolveFromRootOrRelative(relativeClassFilePath)
        if (classFile != null) {
          return classFile
        }
      }
    }
    return null
  }

  @Deprecated(message = "Use findClassFile instead")
  @JvmStatic
  public fun loadClassFileBytes(aClass: PsiClass): ByteArray? {
    return findClassFile(aClass)?.contentsToByteArray(false)
  }

  @JvmStatic
  public fun getContainingClass(psiElement: PsiElement): PsiClass? {
    for (searcher in CLASS_SEARCHER_EP.extensionList) {
      val aClass = searcher.findClass(psiElement)
      if (aClass != null) return aClass
    }

    var containingClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass::class.java, false)
    while (containingClass is PsiTypeParameter) {
      containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass::class.java)
    }
    if (containingClass != null) return containingClass

    val containingFile = psiElement.getContainingFile()
    if (containingFile !is PsiClassOwner) return null

    val textRange = psiElement.getTextRange()
    var result: PsiClass? = null
    val queue: Queue<PsiClass> = ArrayDeque<PsiClass>(listOf<PsiClass?>(*containingFile.getClasses()))
    while (!queue.isEmpty()) {
      val c = queue.remove()
      val navigationElement: PsiElement = c.getNavigationElement()
      val classRange = navigationElement.getTextRange()
      if (classRange != null && classRange.contains(textRange)) {
        result = c
        queue.clear()
        queue.addAll(c.innerClasses)
      }
    }
    return result
  }
}
