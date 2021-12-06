// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl.holders

import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.ProjectScope
import org.jetbrains.plugins.groovy.dsl.*
import java.util.function.Consumer

class OriginAwareMembersHolder(private val GDSLOrigin: VirtualFile,
                               private val delegate: CustomMembersHolder) : CustomMembersHolder {

  override fun processMembers(descriptor: GroovyClassDescriptor, processor: PsiScopeProcessor?, state: ResolveState?): Boolean {
    return doWithReporting(descriptor.placeFile, true) { delegate.processMembers(descriptor, processor, state) }
  }

  override fun consumeClosureDescriptors(descriptor: GroovyClassDescriptor, consumer: Consumer<in ClosureDescriptor>?) {
    return doWithReporting(descriptor.placeFile, Unit) { delegate.consumeClosureDescriptors(descriptor, consumer) }
  }

  private fun <T> doWithReporting(invocationTarget: PsiFile, defaultValue: T, runnable: ThrowableComputable<out T, out Throwable>): T {
    try {
      return runnable.compute()
    }
    catch (e: Exception) {
      if (shouldReportErrors(invocationTarget)) {
        DslErrorReporter.getInstance().invokeDslErrorPopup(e, invocationTarget.project, GDSLOrigin)
      }
      else {
        GroovyDslFileIndex.disableFile(GDSLOrigin, DslActivationStatus.Status.ERROR, e.message)
      }
      return defaultValue
    }
  }

  private fun shouldReportErrors(context: PsiElement): Boolean {
    return ProjectScope.getProjectScope(context.project).contains(GDSLOrigin)
  }
}