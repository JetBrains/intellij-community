// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.codeinsight

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.annotations.ApiStatus

interface ControlExceptionBreakpointSupport {

  /**
   * Find a reference to an exception class or something throwable, which can be used as a target of exception breakpoint.
   *
   * Note: don't capture [Project] or [PsiElement] to prevent memory leaks because the result might be cached for a long time.
   */
  @ApiStatus.OverrideOnly
  fun findExceptionReference(project: Project, element: PsiElement): ExceptionReference?

  interface ExceptionReference {
    val displayName: @NlsSafe String

    fun findExistingBreakpoint(project: Project): XBreakpoint<*>?

    fun createBreakpoint(project: Project): XBreakpoint<*>?
  }

}