// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.lang.jvm.JvmElementVisitor
import com.intellij.lang.jvm.util.JvmInheritanceUtil
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import org.jetbrains.idea.devkit.DevKitBundle
import javax.swing.JComponent

internal class JComponentDataProviderInspection : DevKitJvmInspection() {

  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return super.isAllowed(holder) &&
           DevKitInspectionUtil.isClassAvailable(holder, UiDataProvider::class.java.name)
  }

  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean>? {
    return object : DefaultJvmElementVisitor<Boolean> {

      override fun visitClass(clazz: JvmClass): Boolean {
        if (clazz.classKind != JvmClassKind.CLASS) return true

        if (!JvmInheritanceUtil.isInheritor(clazz, DataProvider::class.java.canonicalName)) return true

        @Suppress("UsagesOfObsoleteApi")
        if (JvmInheritanceUtil.isInheritor(clazz, UiCompatibleDataProvider::class.java.canonicalName)) return true

        if (JvmInheritanceUtil.isInheritor(clazz, JComponent::class.java.canonicalName)) {
          sink.highlight(DevKitBundle.message("inspections.jcomponent.data.provider.use.uidataprovider.instead.of.dataprovider"))
        }
        return true
      }
    }
  }
}