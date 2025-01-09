// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.util.JavaAnonymousClassesHelper
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.parentsOfType
import java.io.File
import java.util.*

object BytecodeViewerManager {
  private val CLASS_SEARCHER_EP = ExtensionPointName<ClassSearcher>("ByteCodeViewer.classSearcher")

  private fun PsiClass.containingClassFileClass(): PsiClass {
    return parentsOfType<PsiClass>(withSelf = true)
      .filterNot(PsiUtil::isLocalOrAnonymousClass)
      .first()
  }

  internal fun findClassFile(aClass: PsiClass): ClassFileInfo? {
    val fileClass = aClass.containingClassFileClass()
    val file = fileClass.getOriginalElement().getContainingFile().getVirtualFile() ?: return null
    val fileIndex = ProjectFileIndex.getInstance(aClass.getProject())
    val jvmClassName = getJVMClassName(aClass) ?: return null
    if (FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) {
      // compiled class; looking for the right .class file (inner class 'A.B' is "contained" in 'A.class', but we need 'A$B.class')
      val classFileName = StringUtil.getShortName(jvmClassName) + ".class"
      if (fileIndex.isInLibraryClasses(file)) {
        val classFile = file.getParent().findChild(classFileName)
        if (classFile != null) return ClassFileInfo.read(classFile)
      }
      else {
        val classFile = File(file.getParent().getPath(), classFileName)
        if (classFile.isFile()) return ClassFileInfo.read(classFile)
      }
    }
    else {
      // source code; looking for a .class file in compiler output
      val module = fileIndex.getModuleForFile(file) ?: return null
      val extension = CompilerModuleExtension.getInstance(module) ?: return null
      val inTests = fileIndex.isInTestSourceContent(file)
      val classRoot = (if (inTests) extension.getCompilerOutputPathForTests() else extension.getCompilerOutputPath()) ?: return null
      val relativePath = jvmClassName.replace('.', '/') + ".class"
      val classFile = File(classRoot.getPath(), relativePath)
      if (classFile.exists()) return ClassFileInfo.read(classFile)
    }
    return null
  }

  @JvmStatic
  fun loadClassFileBytes(aClass: PsiClass): ByteArray? {
    return findClassFile(aClass)?.bytecode
  }

  private fun getJVMClassName(aClass: PsiClass): String? {
    if (aClass !is PsiAnonymousClass) return ClassUtil.getJVMClassName(aClass)
    val containingClass = PsiTreeUtil.getParentOfType<PsiClass?>(aClass, PsiClass::class.java)
    if (containingClass != null) return getJVMClassName(containingClass) + JavaAnonymousClassesHelper.getName(aClass)
    return null
  }

  @JvmStatic
  fun getContainingClass(psiElement: PsiElement): PsiClass? {
    for (searcher in CLASS_SEARCHER_EP.extensionList) {
      val aClass = searcher.findClass(psiElement)
      if (aClass != null) return aClass
    }

    var containingClass = PsiTreeUtil.getParentOfType<PsiClass?>(psiElement, PsiClass::class.java, false)
    while (containingClass is PsiTypeParameter) {
      containingClass = PsiTreeUtil.getParentOfType<PsiClass?>(containingClass, PsiClass::class.java)
    }
    if (containingClass != null) return containingClass

    val containingFile = psiElement.getContainingFile()
    if (containingFile !is PsiClassOwner) return null

    val textRange = psiElement.getTextRange()
    var result: PsiClass? = null
    val queue: Queue<PsiClass> = ArrayDeque<PsiClass>(listOf<PsiClass?>(*containingFile.getClasses()))
    while (!queue.isEmpty()) {
      val c = queue.remove()
      val navigationElement: PsiElement? = c.getNavigationElement()
      val classRange = navigationElement?.getTextRange()
      if (classRange != null && classRange.contains(textRange)) {
        result = c
        queue.clear()
        queue.addAll(c.innerClasses)
      }
    }
    return result
  }
}
