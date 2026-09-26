// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.references.PropertyReferenceBase
import com.intellij.patterns.uast.injectionHostUExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.UastInjectionHostReferenceProvider
import com.intellij.psi.registerUastReferenceProvider
import com.intellij.util.ProcessingContext
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.getParentOfType

internal class EventLogDescriptionReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerUastReferenceProvider(
      injectionHostUExpression().callParameter(0, eventLogGroupCall()),
      EventLogDescriptionReferenceProvider(),
      PsiReferenceRegistrar.DEFAULT_PRIORITY,
    )
  }
}

private class EventLogDescriptionReferenceProvider : UastInjectionHostReferenceProvider() {
  override fun getReferencesForInjectionHost(
    uExpression: UExpression,
    host: PsiLanguageInjectionHost,
    context: ProcessingContext,
  ): Array<PsiReference> {
    val call = uExpression.getParentOfType(UCallExpression::class.java, true) ?: return PsiReference.EMPTY_ARRAY
    if (call.kind == UastCallKind.CONSTRUCTOR_CALL) {
      val groupId = uExpression.evaluateString() ?: return PsiReference.EMPTY_ARRAY
      val recorder = findRecorderName(call) ?: return PsiReference.EMPTY_ARRAY
      return arrayOf(EventLogDescriptionReference(groupId, host, recorder))
    }
    else {
      val eventId = uExpression.evaluateString() ?: return PsiReference.EMPTY_ARRAY
      val receiver = call.receiver ?: return PsiReference.EMPTY_ARRAY
      val (groupId, recorder) = findGroupIdAndRecorderName(receiver) ?: return PsiReference.EMPTY_ARRAY
      return if (call.methodName == REGISTER_ACTIVITY_NAME) arrayOf(
        EventLogDescriptionReference("${groupId}.${eventId}.started", host, recorder),
        EventLogDescriptionReference("${groupId}.${eventId}.finished", host, recorder)
      ) else arrayOf(EventLogDescriptionReference("${groupId}.${eventId}", host, recorder))
    }
  }
}

private class EventLogDescriptionReference(key: String, element: PsiElement, private val recorder: String)
  : PropertyReferenceBase(key, /*soft =*/ true, element)
{
  override fun getPropertiesFiles(): List<PropertiesFile?> {
    val file = findEventLogPropertiesFile(this.element.project, recorder)
    return if (file != null) listOf(file) else emptyList()
  }
}
