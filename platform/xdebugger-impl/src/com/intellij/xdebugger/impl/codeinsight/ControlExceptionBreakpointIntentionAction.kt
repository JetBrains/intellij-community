// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.codeinsight

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.codeinsight.ControlExceptionBreakpointSupport
import com.intellij.xdebugger.codeinsight.ControlExceptionBreakpointSupport.ExceptionReference
import javax.swing.Icon

private val supportsExtensionPoint: ExtensionPointName<ControlExceptionBreakpointSupport> =
  ExtensionPointName.create("com.intellij.xdebugger.controlExceptionBreakpointSupport")

// It's not a regular source code changing action. There will be no before/after templates, category or description.
@Suppress("IntentionDescriptionNotFoundInspection")
internal class ControlExceptionBreakpointIntentionAction : BaseElementAtCaretIntentionAction(), Iconable {

  private var foundExceptionReference: ExceptionReference? = null

  // Explicitly remember what action is suggested during isAvailable calculation
  // to prevent problems in cases when user wanted to disable the breakpoint which was concurrently disabled.
  private var shouldEnable = false

  override fun getFamilyName(): @IntentionFamilyName String =
    XDebuggerBundle.message("xdebugger.intention.control.exception.breakpoint.family")

  override fun getIcon(flags: Int): Icon =
    AllIcons.Debugger.Db_exception_breakpoint

  override fun checkFile(file: PsiFile): Boolean =
    true

  override fun startInWriteAction(): Boolean =
    false

  override fun isAvailable(project: Project, editor: Editor, psiElement: PsiElement): Boolean {
    for (support in supportsExtensionPoint.extensionList) {
      val exRef = support.findExceptionReference(project, psiElement) ?: continue
      val displayName = exRef.displayName
      val breakpoint = exRef.findExistingBreakpoint(project)
      when {
        breakpoint == null -> {
          text = XDebuggerBundle.message("xdebugger.intention.control.exception.breakpoint.create.text", displayName)
          shouldEnable = true
        }
        breakpoint.isEnabled -> {
          text = XDebuggerBundle.message("xdebugger.intention.control.exception.breakpoint.disable.text", displayName)
          shouldEnable = false
        }
        else -> {
          text = XDebuggerBundle.message("xdebugger.intention.control.exception.breakpoint.enable.text", displayName)
          shouldEnable = true
        }
      }
      foundExceptionReference = exRef
      return true
    }
    foundExceptionReference = null
    return false
  }

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    val exRef = foundExceptionReference ?: return
    val breakpoint = exRef.findExistingBreakpoint(project)
    if (breakpoint == null) {
      if (shouldEnable) {
        exRef.createBreakpoint(project)
      } // otherwise it's nothing to do: breakpoint was already deleted
    }
    else {
      breakpoint.isEnabled = shouldEnable
    }
  }

}