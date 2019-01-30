// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.util.SmartList
import org.jetbrains.uast.UClass

internal class NonDefaultConstructorInspection : DevKitUastInspectionBase() {
  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (!isExtensionBean(aClass)) {
      return null
    }

    var errors: MutableList<ProblemDescriptor>? = null
    for (method in aClass.javaPsi.constructors) {
      if (method.hasParameters()) {
        if (errors == null) {
          errors = SmartList()
        }
        errors.add(manager.createProblemDescriptor(method ?: continue,
                                                   "Bean extension class should not have constructor with parameters", true,
                                                   ProblemHighlightType.ERROR, isOnTheFly))
      }
    }
    return errors?.toTypedArray()
  }
}

private fun isExtensionBean(aClass: UClass): Boolean {
  if (aClass.javaPsi.classKind != JvmClassKind.CLASS) {
    return false
  }

  var p = aClass
  while (true) {
    p = p.superClass ?: return false
    if (p.qualifiedName == "com.intellij.openapi.extensions.AbstractExtensionPointBean") {
      return true
    }
  }
}