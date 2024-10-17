// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.options.JavaClassValidator
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.stringList
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.util.InheritanceUtil
import com.siyeh.ig.ui.ExternalizableStringSet
import org.jetbrains.idea.devkit.DevKitBundle

internal class CanBeDumbAwareInspection : DevKitJvmInspection.ForClass() {

  @Suppress("MemberVisibilityCanBePrivate")
  var ignoreClasses: MutableList<String> = ExternalizableStringSet()

  override fun getOptionsPane(): OptPane {
    return pane(
      stringList("ignoreClasses", DevKitBundle.message("inspection.can.be.dumb.aware.settings.ignore.classes.title"),
                 JavaClassValidator()
                   .withTitle(DevKitBundle.message("inspection.can.be.dumb.aware.settings.ignore.classes.dialog.title"))
                   .withSuperClass(PossiblyDumbAware::class.java.canonicalName))
    )
  }

  override fun checkClass(project: Project, psiClass: PsiClass, sink: HighlightSink) {
    if (psiClass.hasModifier(JvmModifier.ABSTRACT)) return
    val qualifiedName = psiClass.qualifiedName
    if (ignoreClasses.contains(qualifiedName)) return

    val possiblyDumbAwarePsiClass = JavaPsiFacade.getInstance(project)
                                      .findClass(PossiblyDumbAware::class.java.canonicalName, psiClass.resolveScope) ?: return
    if (!psiClass.isInheritor(possiblyDumbAwarePsiClass, true)) return

    if (InheritanceUtil.isInheritor(psiClass, DumbAware::class.java.canonicalName)) return

    val isDumbAwareMethod = LightMethodBuilder(psiClass.manager, "isDumbAware")
      .setContainingClass(possiblyDumbAwarePsiClass)
      .setModifiers(PsiModifier.PUBLIC)
      .setMethodReturnType(PsiTypes.booleanType())
    val overriddenMethod = psiClass.findMethodBySignature(isDumbAwareMethod, true)
    if (overriddenMethod != null) {
      if (overriddenMethod.containingClass != possiblyDumbAwarePsiClass) {
        return
      }

      // explicit search for 'default' method in interfaces is necessary
      for (clazz in psiClass.interfaces) {
        val overriddenInInterface = clazz.findMethodBySignature(isDumbAwareMethod, false)
        if (overriddenInInterface != null &&
            overriddenInInterface.containingClass != possiblyDumbAwarePsiClass) {
          return
        }
      }
    }

    val fixes =
      when {
        qualifiedName != null -> object : LocalQuickFix {
          override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            ignoreClasses.add(qualifiedName)
          }

          override fun getFamilyName(): @IntentionFamilyName String {
            return DevKitBundle.message("inspection.can.be.dumb.aware.quickfix.add.to.ignore", qualifiedName)
          }
        }
        else -> null
      }

    sink.highlight(DevKitBundle.message("inspection.can.be.dumb.aware.message"), fixes)
  }
}