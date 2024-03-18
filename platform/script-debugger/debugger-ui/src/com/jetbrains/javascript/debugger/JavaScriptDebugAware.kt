// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.javascript.debugger

import com.intellij.javascript.debugger.ExpressionInfoFactory
import com.intellij.javascript.debugger.NameMapper
import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.evaluation.ExpressionInfo
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.MemberFilter

/**
 * @see com.intellij.javascript.debugger.JavaScriptDebugAwareBase
 */
abstract class JavaScriptDebugAware {
  companion object {
    val EP_NAME: ExtensionPointName<JavaScriptDebugAware> = ExtensionPointName("com.jetbrains.javaScriptDebugAware")

    @JvmStatic
    fun isBreakpointAware(fileType: FileType): Boolean {
      val aware = getBreakpointAware(fileType)
      return aware != null && aware.breakpointTypeClass == null
    }

    fun getBreakpointAware(fileType: FileType): JavaScriptDebugAware? {
      return EP_NAME.extensionList.firstOrNull { fileType == it.fileType }
    }
  }

  protected open val fileType: LanguageFileType?
    get() = null

  open val breakpointTypeClass: Class<out XLineBreakpointType<*>>?
    get() = null

  /**
   * Return false if your language could be natively executed in the VM
   * You must not specify it, and it doesn't matter if you use not own breakpoint type - (Kotlin or GWT use a java breakpoint type, for example)
   */
  open val isOnlySourceMappedBreakpoints: Boolean
    get() = true

  open fun canGetEvaluationInfo(file: PsiFile): Boolean = file.fileType == fileType

  open fun getEvaluationInfo(element: PsiElement, document: Document, expressionInfoFactory: ExpressionInfoFactory): Promise<ExpressionInfo?>? = null

  open fun createMemberFilter(nameMapper: NameMapper?, element: PsiElement, end: Int): MemberFilter? = null

  open fun getNavigationElementForSourcemapInspector(file: PsiFile): PsiElement? = null

  // return null if unsupported
  // cannot be in MemberFilter because creation of MemberFilter could be async
  // the problem - GWT mangles name (https://code.google.com/p/google-web-toolkit/issues/detail?id=9106 https://github.com/sdbg/sdbg/issues/6 https://youtrack.jetbrains.com/issue/IDEA-135356), but doesn't add name mappings
  open fun normalizeMemberName(name: String): String? = null
}