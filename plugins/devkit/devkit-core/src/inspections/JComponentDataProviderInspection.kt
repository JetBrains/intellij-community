// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UClass
import javax.swing.JComponent

internal class JComponentDataProviderInspection : DevKitUastInspectionBase(UClass::class.java) {

  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return super.isAllowed(holder) &&
           DevKitInspectionUtil.isClassAvailable(holder, UiDataProvider::class.java.name)
  }

  override fun checkClass(uClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<out ProblemDescriptor?>? {
    val psiClass = uClass.javaPsi
    if (psiClass.classKind != JvmClassKind.CLASS) return null

    @Suppress("UsagesOfObsoleteApi")
    if (!InheritanceUtil.isInheritor(psiClass, DataProvider::class.java.canonicalName)) return null

    @Suppress("UsagesOfObsoleteApi")
    if (InheritanceUtil.isInheritor(psiClass, UiCompatibleDataProvider::class.java.canonicalName)) return null

    if (!InheritanceUtil.isInheritor(psiClass, JComponent::class.java.canonicalName)) return null

    val holder = createProblemsHolder(uClass, manager, isOnTheFly)
    holder.registerUProblem(uClass,
                            DevKitBundle.message("inspections.jcomponent.data.provider.use.uidataprovider.instead.of.dataprovider"))
    return holder.resultsArray
  }

}