// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.groovy.config

import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.usages.impl.rules.UsageType
import com.intellij.util.Processor
import org.jetbrains.plugins.gradle.config.GradleUseScopeEnlarger
import org.jetbrains.plugins.gradle.config.hasGradleTaskAnnotation
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField

internal class GradleImplicitUsageProvider : ImplicitUsageProvider {
  override fun isImplicitUsage(element: PsiElement): Boolean {
    if (element is GrField && hasGradleTaskAnnotation(element.modifierList)) return true
    return hasUsageOfType(element, null)
  }

  override fun isImplicitRead(element: PsiElement): Boolean {
    return hasUsageOfType(element, UsageType.READ)
  }

  override fun isImplicitWrite(element: PsiElement): Boolean {
    return hasUsageOfType(element, UsageType.WRITE)
  }

  private fun hasUsageOfType(element: PsiElement, usage: UsageType?): Boolean {
    if (element !is PsiMember) return false

    val found = Ref.create(false)
    GradleUseScopeEnlarger.Companion.search(element, Processor {

      if (!it.isReferenceTo(element))
        return@Processor true

      if (usage == null) {
        found.set(true)
        return@Processor false
      }

      val readWriteAccessDetector = ReadWriteAccessDetector.findDetector(element) ?: return@Processor true
      when (readWriteAccessDetector.getReferenceAccess(it.element, it)) {
        ReadWriteAccessDetector.Access.ReadWrite -> {
          found.set(true)
          return@Processor false
        }
        ReadWriteAccessDetector.Access.Read -> {
          if (usage == UsageType.READ) {
            found.set(true)
            return@Processor false
          }
        }
        ReadWriteAccessDetector.Access.Write -> {
          if (usage == UsageType.WRITE) {
            found.set(true)
            return@Processor false
          }
        }
      }
      true
    })
    return found.get()
  }
}
