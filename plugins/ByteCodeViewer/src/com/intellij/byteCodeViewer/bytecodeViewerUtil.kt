// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.annotations.Contract
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.util.Textifier
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

private val LOG = fileLogger()

@Contract("null -> false")
internal fun isValidFileType(fileType: FileType?): Boolean {
  return fileType === JavaClassFileType.INSTANCE || fileType === JavaFileType.INSTANCE
}

internal fun isMarkedForCompilation(project: Project, virtualFile: VirtualFile): Boolean {
  val compilerManager = CompilerManager.getInstance(project)
  val compileScope = compilerManager.createFilesCompileScope(arrayOf(virtualFile))
  return !compilerManager.isUpToDate(compileScope)
}

internal fun getPsiElement(project: Project, editor: Editor): PsiElement? {
  fun findElementInFile(psiFile: PsiFile?, editor: Editor): PsiElement? {
    return psiFile?.findElementAt(editor.getCaretModel().offset)
  }

  val file = PsiUtilBase.getPsiFileInEditor(editor, project)
  val injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file)
  var psiElement = findElementInFile(PsiUtilBase.getPsiFileInEditor(injectedEditor, project), injectedEditor)

  if (file != null && psiElement == null) {
    psiElement = findElementInFile(file, editor)
  }

  return psiElement
}

internal data class Bytecode(val withDebugInfo: String, val withoutDebugInfo: String)

/**
 * Retrieves the bytecode representation of the class containing the provided PSI element.
 *
 * @return a Pair where the first value is a bytecode with debug info included, and the second value is without any debug info
 */
internal fun getByteCodeVariants(psiElement: PsiElement): Bytecode? {
  val containingClass = BytecodeViewerManager.getContainingClass(psiElement) ?: return null
  try {
    val bytes = BytecodeViewerManager.loadClassFileBytes(containingClass) ?: return null
    val withDebugInfoWriter = StringWriter()
    PrintWriter(withDebugInfoWriter).use { printWriter ->
      val textifier = Textifier()
      val classVisitor: ClassVisitor = TraceClassVisitor(null, textifier, printWriter)
      ClassReader(bytes).accept(classVisitor, ClassReader.SKIP_FRAMES)
    }
    val noDebugInfo = removeDebugInfo(withDebugInfoWriter.toString())
    return Bytecode(withDebugInfoWriter.toString(), noDebugInfo)
  }
  catch (e: IOException) {
    LOG.error(e)
  }
  return null
}