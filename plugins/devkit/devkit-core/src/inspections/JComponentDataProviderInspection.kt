// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.idea.devkit.DevKitBundle
import javax.swing.JComponent

internal class JComponentDataProviderInspection : DevKitJvmInspection.ForClass() {

  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return super.isAllowed(holder) &&
           DevKitInspectionUtil.isClassAvailable(holder, UiDataProvider::class.java.name)
  }

  override fun checkClass(project: Project, psiClass: PsiClass, sink: HighlightSink) {
    if (psiClass.classKind != JvmClassKind.CLASS) return

    @Suppress("UsagesOfObsoleteApi")
    if (!InheritanceUtil.isInheritor(psiClass, DataProvider::class.java.canonicalName)) return

    @Suppress("UsagesOfObsoleteApi")
    if (InheritanceUtil.isInheritor(psiClass, UiCompatibleDataProvider::class.java.canonicalName)) return

    if (!InheritanceUtil.isInheritor(psiClass, JComponent::class.java.canonicalName)) return

    sink.highlight(DevKitBundle.message("inspections.jcomponent.data.provider.use.uidataprovider.instead.of.dataprovider"))
  }

}