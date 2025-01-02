// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.util.JavaAnonymousClassesHelper
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import java.io.File
import java.util.*

@Service(Service.Level.PROJECT)
object BytecodeViewerManager {
  private val CLASS_SEARCHER_EP = create<ClassSearcher>("ByteCodeViewer.classSearcher")

  @JvmStatic
  fun getInstance(project: Project): BytecodeViewerManager? {
    return project.getService<BytecodeViewerManager?>(BytecodeViewerManager::class.java)
  }

  @JvmStatic
  fun loadClassFileBytes(aClass: PsiClass): ByteArray? {
    val jvmClassName = getJVMClassName(aClass) ?: return null
    var fileClass: PsiClass = aClass
    while (PsiUtil.isLocalOrAnonymousClass(fileClass)) {
      val containingClass = PsiTreeUtil.getParentOfType<PsiClass?>(fileClass, PsiClass::class.java)
      if (containingClass != null) {
        fileClass = containingClass
      }
    }
    val file = fileClass.getOriginalElement().getContainingFile().getVirtualFile() ?: return null
    val index = ProjectFileIndex.getInstance(aClass.getProject())
    if (FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) {
      // compiled class; looking for the right .class file (inner class 'A.B' is "contained" in 'A.class', but we need 'A$B.class')
      val classFileName = StringUtil.getShortName(jvmClassName) + ".class"
      if (index.isInLibraryClasses(file)) {
        val classFile = file.getParent().findChild(classFileName)
        if (classFile != null) return classFile.contentsToByteArray(false)
      }
      else {
        val classFile = File(file.getParent().getPath(), classFileName)
        if (classFile.isFile()) return FileUtil.loadFileBytes(classFile)
      }
    }
    else {
      // source code; looking for a .class file in compiler output
      val module = index.getModuleForFile(file) ?: return null
      val extension = CompilerModuleExtension.getInstance(module) ?: return null
      val inTests = index.isInTestSourceContent(file)
      val classRoot = (if (inTests) extension.getCompilerOutputPathForTests() else extension.getCompilerOutputPath()) ?: return null
      val relativePath = jvmClassName.replace('.', '/') + ".class"
      val classFile = File(classRoot.getPath(), relativePath)
      if (classFile.exists()) return FileUtil.loadFileBytes(classFile)
    }

    return null
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
