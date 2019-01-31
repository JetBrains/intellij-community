// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiParameterList
import com.intellij.util.SmartList
import gnu.trove.THashSet
import org.jetbrains.idea.devkit.util.ExtensionByExtensionPointLocator
import org.jetbrains.uast.UClass

internal class NonDefaultConstructorInspection : DevKitUastInspectionBase() {
  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val javaPsi = aClass.javaPsi
    if (!javaPsi.isPhysical || javaPsi.classKind != JvmClassKind.CLASS) {
      return null
    }

    val constructors = javaPsi.constructors
    // very fast path - do nothing if no constructors
    if (constructors.isEmpty()) {
      return null
    }

    // fast path - check by qualified name
    if (!isExtensionBean(aClass)) {
      // slow path - check using index
      if (!isRegisteredIntentionAction(aClass, manager)) {
        return null
      }
    }

    var errors: MutableList<ProblemDescriptor>? = null
    for (method in constructors) {
      val parameters = method.parameterList
      if (parameters.isEmpty || isAllowedParameters(parameters)) {
        // allow to have empty constructor and extra (e.g. DartQuickAssistIntention)
        return null
      }

      if (errors == null) {
        errors = SmartList()
      }
      errors.add(manager.createProblemDescriptor(method ?: continue,
                                                 "Bean extension class should not have constructor with parameters", true,
                                                 ProblemHighlightType.ERROR, isOnTheFly))
    }
    return errors?.toTypedArray()
  }
}

// cannot check com.intellij.codeInsight.intention.IntentionAction by class qualified name because not all IntentionAction used as IntentionActionBean
private fun isRegisteredIntentionAction(aClass: UClass, manager: InspectionManager): Boolean {
  val qualifiedName = aClass.qualifiedName
  var isFound = false
  ExtensionByExtensionPointLocator(manager.project, IntentionManager.EP_INTENTION_ACTIONS.name).processCandidates { tag ->
    if (qualifiedName == tag.getSubTagText("className")) {
      isFound = true
    }
    !isFound
  }
  return isFound
}

private fun isAllowedParameters(list: PsiParameterList): Boolean {
  if (list.parametersCount != 1) {
    return false
  }

  val first = list.parameters.first()
  if (first.isVarArgs) {
    return false
  }

  val type = first.type as? PsiClassType ?: return false
  // before resolve, check unqualified name
  if (!(type.className == "Project" || type.className == "Module")) {
    return false
  }

  val qualifiedName = (type.resolve() ?: return false).qualifiedName
  return qualifiedName == "com.intellij.openapi.project.Project" || qualifiedName == "com.intellij.openapi.module.Module"
}

private val interfacesToCheck = THashSet<String>(listOf(
  "com.intellij.codeInsight.daemon.LineMarkerProvider",
  "com.intellij.openapi.fileTypes.SyntaxHighlighterFactory",
  "com.intellij.openapi.compiler.CompileTask"
))

private val classesToCheck = THashSet<String>(listOf(
  "com.intellij.openapi.extensions.AbstractExtensionPointBean",
  "com.intellij.codeInsight.completion.CompletionContributor",
  "com.intellij.codeInsight.completion.CompletionConfidence",
  "com.intellij.psi.PsiReferenceContributor"
))

private fun isExtensionBean(aClass: UClass): Boolean {
  var p = aClass
  while (true) {
    if (checkInterfaces(p.javaPsi.interfaces)) {
      return true
    }

    p = p.superClass ?: return false
    if (classesToCheck.contains(p.qualifiedName)) {
      return true
    }
  }
}

private fun checkInterfaces(list: Array<PsiClass>): Boolean {
  for (interfaceClass in list) {
    if (interfacesToCheck.contains(interfaceClass.qualifiedName) || checkInterfaces(interfaceClass.interfaces)) {
      return true
    }
  }
  return false
}