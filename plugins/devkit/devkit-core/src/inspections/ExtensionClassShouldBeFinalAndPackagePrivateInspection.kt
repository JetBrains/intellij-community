// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmElementVisitor
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil

internal class ExtensionClassShouldBeFinalAndPackagePrivateInspection : DevKitJvmInspection() {

  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean> {
    return object : DefaultJvmElementVisitor<Boolean> {
      override fun visitClass(clazz: JvmClass): Boolean {
        if (clazz !is PsiClass) return true
        if (clazz.language.id == "kotlin") return true // see ExtensionClassShouldBeFinalAndInternalInspection
        if (!PsiUtil.isExtensionPointImplementationCandidate(clazz)) {
          return true
        }
        val file = clazz.containingFile ?: return true
        val isFinal = clazz.hasModifier(JvmModifier.FINAL)
        val isPackageLocal = clazz.hasModifier(JvmModifier.PACKAGE_LOCAL)
        if (isFinal && isPackageLocal) return true
        if (!ExtensionUtil.isInstantiatedExtension(clazz) { false }) return true
        if (!isFinal) {
          val actions = createModifierActions(clazz, modifierRequest(JvmModifier.FINAL, true))
          val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
          sink.highlight(DevKitBundle.message("inspection.extension.class.should.be.final.text"), *fixes)
        }
        if (!isPackageLocal) {
          val actions = createModifierActions(clazz, modifierRequest(JvmModifier.PACKAGE_LOCAL, true))
          val fixes = IntentionWrapper.wrapToQuickFixes(actions.toTypedArray(), file)
          sink.highlight(DevKitBundle.message("inspection.extension.class.should.be.package.private.text"), *fixes)
        }
        return true
      }
    }
  }
}